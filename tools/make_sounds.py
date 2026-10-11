"""Synthesizes every Worldbreaker sound from scratch (no samples): footfalls a city away, the ground grinding, a horn
from nowhere, steam, stone giving way. Writes mono 44.1 kHz Ogg Vorbis into assets/worldbreaker/sounds via ffmpeg."""
import os, subprocess, sys, tempfile
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1033)


def t_(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.standard_normal(int(SR * d))


def env(d, a, r, curve=4.0):
    t = t_(d)
    e = np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) * curve / max(r, 1e-4))
    return e


def bp(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype='band', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def lp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype='low', fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype='high', fs=SR, output='sos'), x)


def sweep_bp(x, f0, f1, q=3.0, steps=64):
    """Band-pass whose centre glides from f0 to f1 (block-wise), the sound of a blade cutting air."""
    out = np.zeros_like(x)
    n = len(x)
    block = max(1, n // steps)
    zi = None
    for i in range(0, n, block):
        k = i / max(1, n - 1)
        fc = f0 * (f1 / f0) ** k
        lo, hi = fc / (1 + 1 / q), min(fc * (1 + 1 / q), SR / 2 - 100)
        sos = signal.butter(2, [lo, hi], btype='band', fs=SR, output='sos')
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        seg, zi = signal.sosfilt(sos, x[i:i + block], zi=zi)
        out[i:i + block] = seg
    return out


def ring(d, freqs, decays, amps):
    """Struck steel: inharmonic partials, each with its own decay."""
    t = t_(d)
    y = np.zeros_like(t)
    for f, dc, a in zip(freqs, decays, amps):
        y += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t * dc)
    return y


def reverb(x, tail=1.6, mix=0.3, bright=6000):
    t = t_(tail)
    ir = rng.standard_normal(len(t)) * np.exp(-t * 6.9 / tail)
    ir = lp(ir, bright)
    ir[0] = 0
    dry = np.concatenate([x, np.zeros(len(t))])
    wet = signal.fftconvolve(x, ir)[: len(dry)]
    wet = np.concatenate([wet, np.zeros(len(dry) - len(wet))])
    wet = wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(x))
    return dry * (1 - mix) + wet * mix


def pad(x, d):
    n = int(SR * d)
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[:n] if n > len(x) else x


def mixl(*parts):
    n = max(len(p) for p in parts)
    y = np.zeros(n)
    for p in parts:
        y[: len(p)] += p
    return y


def at(x, sec):
    return np.concatenate([np.zeros(int(SR * sec)), x])


def save(name, y, peak=0.92):
    y = y - np.mean(y)
    fade = min(len(y), int(SR * 0.01))
    y[-fade:] *= np.linspace(1, 0, fade)
    y = y / (np.max(np.abs(y)) + 1e-9) * peak
    y = np.tanh(y * 1.15) / np.tanh(1.15)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        wavfile.write(f.name, SR, (y * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', f.name, '-c:a', 'libvorbis', '-q:a', '6',
                        os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(f.name)


def whoosh(d=0.28, f0=600, f1=4200, q=2.5):
    n = noise(d)
    y = sweep_bp(n, f0, f1, q) * env(d, d * 0.35, d * 0.65, 5)
    return y


def thump(d=0.5, f0=120, f1=38, decay=7):
    t = t_(d)
    f = f1 + (f0 - f1) * np.exp(-t * 18)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * decay)


def crack(d=0.08):
    return hp(noise(d), 1800) * env(d, 0.001, d, 9)


def bell(d, f0, decay=1.4, amp=1.0):
    """A clock bell: a struck bar's inharmonic partials over a warm hum."""
    return ring(d, [f0, f0 * 2.0, f0 * 2.76, f0 * 5.4, f0 * 8.93], [decay, decay * 1.3, decay * 1.8, decay * 3, decay * 4.5],
                [amp, 0.55 * amp, 0.4 * amp, 0.2 * amp, 0.1 * amp])


def tick_click(d=0.05, f=3200):
    return mixl(crack(0.012) * 0.9, ring(d, [f, f * 1.52, f * 2.3], [80, 110, 140], [0.5, 0.3, 0.2]))


def sub(d, f0, f1, decay, drive=1.8):
    """An 808: a sine that falls in pitch, driven warm."""
    t = t_(d)
    f = f1 + (f0 - f1) * np.exp(-t * 6.0)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * decay)
    return np.tanh(y * drive) / np.tanh(drive)


def shimmer(d, f0, f1, n=6, amp=0.3):
    """Detuned crystal partials gliding together: the sound of a Stand's light."""
    t = t_(d)
    y = np.zeros_like(t)
    for k in range(n):
        det = 1 + (k - n / 2) * 0.004
        f = (f0 + (f1 - f0) * (t / d)) * det * (1 + k * 0.5)
        y += np.sin(2 * np.pi * np.cumsum(f) / SR + k) / (1 + k * 0.6)
    return y * amp


def reverse(x):
    return x[::-1].copy()



def zap(d, f0, f1, amp=1.0):
    t = t_(d)
    f = f1 + (f0 - f1) * np.exp(-t * 14)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) + 0.4 * np.sign(np.sin(ph * 2.01))) * np.exp(-t * 9) * amp


def roar(d, lo=60, hi=900, rise=0.3):
    """The sound of a beam: a wall of filtered noise over a sub, a bright edge on top."""
    t = t_(d)
    body = bp(noise(d), lo, hi) * 1.4 + lp(noise(d), 140) * 1.2
    edge = sweep_bp(noise(d), 1500, 4500, 2) * 0.35
    hum = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f in (55, 82.4, 110)) * 0.35
    return np.tanh((body + edge + hum) * 1.2) * env(d, rise, d - rise, 1.2)


def brown(d):
    """Brown noise: the low roar of the ground itself."""
    w = noise(d)
    b = np.cumsum(w)
    b = b - signal.savgol_filter(b, 4001 if len(b) > 4001 else (len(b) // 2) * 2 - 1, 2)
    return b / (np.max(np.abs(b)) + 1e-9)


def footfall(seed):
    global rng
    rng = np.random.default_rng(seed)
    d = 4.0
    # the hit: a very low drop of a sine, sat hard, then the body of the earth taking it
    boom = sub(1.8, 70, 24, 2.2, 2.6) * 1.0
    body = lp(noise(2.4), 180) * env(2.4, 0.01, 2.0, 3.5) * 0.9
    knock = thump(0.6, 160, 45, 6) * 0.8
    # the crunch of everything under the foot, and debris pattering down after
    crunch = bp(noise(0.8), 300, 2500) * env(0.8, 0.002, 0.6, 5) * 0.45
    debris = np.zeros(int(SR * d))
    for k in range(40):
        t0 = 0.2 + rng.random() * 2.6
        c = bp(noise(0.05), 800 + rng.random() * 3000, 6000) * env(0.05, 0.001, 0.05, 8) * (0.15 * rng.random())
        debris = mixl(debris, at(c, t0))
    y = mixl(boom, body, knock, crunch, debris)
    return reverb(pad(y, d), 2.8, 0.35, 900)


for i in range(3):
    save(f'footfall_{i + 1}', footfall(100 + i))

# the ground grinding: a constant low churn with slow swells, 4 s, seamless-ish
d = 4.0
rumble = lp(brown(d), 120) * (0.7 + 0.3 * np.sin(2 * np.pi * t_(d) * 0.4))
rumble = mixl(rumble, lp(noise(d), 60) * 0.6, bp(noise(d), 200, 600) * 0.05)
save('rumble', rumble * env(d, 0.6, 3.4, 1.2), 0.8)

# the horn: an eerie low chord swelling out of nowhere, detuned brass partials and a choir-like shimmer
d = 7.0
t = t_(d)
horn = np.zeros_like(t)
for f, a in [(55, 1.0), (82.4, 0.7), (110, 0.6), (130.8, 0.35), (164.8, 0.25)]:
    for det in (-0.6, 0, 0.7):
        ph = 2 * np.pi * (f + det) * t + 0.3 * np.sin(2 * np.pi * 0.2 * t)
        saw = signal.sawtooth(ph)
        horn += a * saw
horn = lp(horn, 900) * np.minimum(1, t / 2.2) * np.exp(-np.maximum(0, t - 4.8) * 1.6)
horn = mixl(horn * 0.5, bp(noise(d), 300, 1200) * 0.05 * np.minimum(1, t / 3))
save('horn', reverb(horn, 3.5, 0.45, 2500))

# steam: a broad hiss with sputter
d = 3.0
steam = hp(noise(d), 1800) * env(d, 0.3, 2.6, 1.5) * (0.8 + 0.2 * np.sin(2 * np.pi * 7 * t_(d)))
save('steam', steam, 0.6)

# stone giving way: cracks, grinding, a slump
for i in range(2):
    rng = np.random.default_rng(300 + i)
    d = 2.2
    y = lp(noise(d), 400) * env(d, 0.05, 2.0, 2.5) * 0.6
    for k in range(18):
        y = mixl(y, at(crack(0.06) * (0.3 + 0.7 * rng.random()), rng.random() * 1.6))
    y = mixl(y, at(thump(0.5, 90, 40, 6) * 0.8, 0.3 + rng.random() * 0.4))
    save(f'crumble_{i + 1}', reverb(y, 1.4, 0.3, 3000))

# heartbeat
d = 1.0
hb = mixl(thump(0.35, 75, 35, 9), at(thump(0.3, 70, 32, 10) * 0.7, 0.22))
save('heartbeat', reverb(pad(hb, d), 0.8, 0.2, 1500))

# the cinematic hit: a sub drop, a crack of thunder, a long tail
d = 3.0
imp = mixl(sub(2.0, 110, 30, 2.4, 3.0), crack(0.15) * 0.7, hp(noise(1.2), 400) * env(1.2, 0.002, 1.0, 4) * 0.5)
save('impact', reverb(pad(imp, d), 2.6, 0.4, 2000))

# the earth tearing open as they rise
d = 5.0
rise = mixl(lp(brown(d), 200) * np.minimum(1, t_(d) / 1.5) * env(d, 0.01, 4.8, 1.2), bp(noise(d), 300, 1400) * 0.2 * env(d, 1.0, 3.5, 2))
for k in range(30):
    rise = mixl(rise, at(crack(0.08) * 0.4, 0.5 + rng.random() * 3.8))
save('rise', reverb(rise, 2.5, 0.35, 1500))

# wind at altitude
d = 3.5
wind = sweep_bp(noise(d), 250, 900, 1.6) * env(d, 1.0, 2.5, 1.6)
save('wind', wind, 0.7)
print('sounds ok')
