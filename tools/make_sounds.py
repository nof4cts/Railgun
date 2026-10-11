"""Synthesizes Naoya's Projection Sorcery sounds from scratch: film shutters, a projector at 24 frames a second,
sonic booms, frames snapping shut and shattering. Mono 44.1 kHz Ogg Vorbis via ffmpeg."""
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


def shutter(pitch=1.0):
    """A film camera's shutter: two hard clicks a hair apart, a little metal ring."""
    c1 = mixl(crack(0.01) * 0.9, ring(0.08, [4200 * pitch, 6100 * pitch], [60, 90], [0.4, 0.25]))
    return mixl(c1, at(c1 * 0.7, 0.028))


save('shutter', shutter())
# the dash: a tearing whoosh with a crack of displaced air at the front
d = 0.6
save('dash', mixl(whoosh(0.45, 400, 6000, 2.0) * 1.0, at(crack(0.05) * 0.8, 0.02), thump(0.3, 140, 60, 10) * 0.4))
save('blink', mixl(whoosh(0.22, 900, 7000, 2.5), at(shutter(1.3) * 0.6, 0.12)))
# the sonic boom: an N-wave double crack and the roll after it
d = 2.0
boom = mixl(crack(0.06) * 1.0, at(crack(0.06) * 0.9, 0.11), sub(1.4, 90, 30, 3.0, 2.4) * 0.9, lp(noise(1.8), 300) * env(1.8, 0.01, 1.6, 3) * 0.6)
save('boom', reverb(boom, 2.2, 0.35, 2500))
# a body stuck in a frame: the snap of a cel slammed down, a glassy chime, a dead thud
frz = mixl(shutter(0.8) * 1.2, at(bell(1.2, 1480, 3.0, 0.35), 0.01), thump(0.35, 120, 50, 9) * 0.7)
save('freeze', reverb(frz, 1.2, 0.25, 5000))
# the frame breaking: glass, many small pieces
gl = np.zeros(int(SR * 1.4))
for k in range(36):
    t0 = rng.random() * 0.5
    piece = ring(0.4, [2000 + rng.random() * 5000, 3000 + rng.random() * 6000], [20 + rng.random() * 40, 30 + rng.random() * 40], [0.3, 0.2])
    gl = mixl(gl, at(piece * (0.3 + 0.7 * rng.random()), t0))
gl = mixl(gl, crack(0.08) * 1.0, hp(noise(0.9), 2500) * env(0.9, 0.001, 0.8, 5) * 0.5, thump(0.4, 100, 45, 8) * 0.6)
save('shatter', reverb(gl, 1.4, 0.3, 7000))
# hits
save('hit', mixl(thump(0.18, 180, 70, 22) * 1.0, crack(0.03) * 0.6, bp(noise(0.08), 600, 3000) * env(0.08, 0.001, 0.07, 9) * 0.4))
save('heavy', reverb(mixl(thump(0.5, 160, 45, 8), crack(0.05) * 0.9, sub(0.7, 110, 40, 6, 2.0) * 0.8), 0.9, 0.25, 3000))
# the projector: a motor hum with the gate clicking 24 times a second, rising as the plan comes together
d = 2.6
t = t_(d)
motor = (np.sin(2 * np.pi * 96 * t) * 0.3 + np.sin(2 * np.pi * 192 * t) * 0.15) * np.minimum(1, t / 0.3)
clicks = np.zeros_like(t)
for i in range(int(d * 24)):
    clicks = mixl(clicks, at(crack(0.006) * (0.4 + 0.25 * np.sin(i)), i / 24.0))
film = mixl(motor, clicks[: len(t)], bp(noise(d), 2000, 6000) * 0.04) * env(d, 0.05, 2.5, 1.2)
save('film', film, 0.75)
# Mach 3: three booms run together and a hit that flattens the air
d = 3.2
u = mixl(sub(2.5, 140, 26, 2.0, 3.0), crack(0.1) * 1.0, at(crack(0.08) * 0.8, 0.06), hp(noise(1.6), 300) * env(1.6, 0.002, 1.4, 3.5) * 0.6,
         at(gl * 0.5, 0.02))
save('ult_hit', reverb(pad(u, d), 2.8, 0.4, 2500))
print('sounds ok')
