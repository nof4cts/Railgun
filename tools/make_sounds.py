"""Synthesizes every sound effect from scratch (no samples): steel, air, impacts, haki, the demon's drone.
Writes mono 44.1 kHz Ogg Vorbis into assets/santoryu/sounds via ffmpeg."""
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


# ------------------------------------------------------------------ blades
for i, (p, s) in enumerate([(1.0, 0.0), (1.12, 0.05)], 1):
    d = 0.75
    scrape = sweep_bp(noise(0.42), 2600 * p, 6500 * p, 6) * env(0.42, 0.25, 0.17, 3)
    shing = ring(0.7, [2120 * p, 3410 * p, 5230 * p, 7380 * p], [5, 6.5, 8, 10], [1, 0.7, 0.45, 0.25])
    save(f'unsheathe_{i}', reverb(mixl(scrape * 0.6, at(shing, 0.33 + s)), 0.9, 0.22))

slide = sweep_bp(noise(0.3), 4000, 2200, 5) * env(0.3, 0.22, 0.08, 3)
click = mixl(crack(0.03) * 1.6, ring(0.18, [3100, 4700, 6900], [30, 40, 55], [0.6, 0.4, 0.25]))
save('sheathe', reverb(mixl(slide * 0.5, at(click, 0.3)), 0.6, 0.18))

for i, (f0, f1, d) in enumerate([(500, 4800, 0.24), (420, 4000, 0.3), (650, 5600, 0.2)], 1):
    save(f'slash_{i}', reverb(whoosh(d, f0, f1), 0.5, 0.15))

for i, p in enumerate([1.0, 0.86], 1):
    w = whoosh(0.42, 300 * p, 3600 * p, 2.0)
    tear = hp(noise(0.25), 3000) * env(0.25, 0.005, 0.24, 6) * 0.5
    save(f'slash_heavy_{i}', reverb(mixl(w, at(tear, 0.18), at(thump(0.4, 140, 45, 9) * 0.8, 0.2)), 1.2, 0.25))

# vanish: air torn open, then the rush past
d = 0.55
rush = sweep_bp(noise(d), 3500, 500, 1.6) * env(d, 0.06, 0.49, 5)
save('dash', reverb(mixl(crack(0.04) * 0.8, rush, lp(noise(d), 180) * env(d, 0.02, 0.5, 6) * 0.8), 0.8, 0.2))

# ------------------------------------------------------------------ impacts
for i, p in enumerate([1.0, 1.15], 1):
    body = thump(0.6, 160 * p, 42 * p, 6)
    save(f'impact_{i}', reverb(mixl(crack(0.06) * 1.2, body, hp(noise(0.2), 900) * env(0.2, 0.002, 0.2, 7) * 0.5), 1.0, 0.2))

d = 2.6
boom = thump(d, 110, 28, 1.8) * 1.2
burst = lp(noise(d), 2500) * env(d, 0.004, d, 3.0)
debris = bp(noise(d), 300, 1800) * env(d, 0.15, d, 2.5) * 0.4
steel = ring(2.0, [1830, 2960, 4410, 6650], [2.5, 3.2, 4.5, 6], [0.25, 0.2, 0.12, 0.08])
save('impact_big', reverb(mixl(crack(0.08) * 1.4, boom, burst * 0.8, debris, steel), 2.4, 0.35, 4000))

# ------------------------------------------------------------------ winds and haki
d = 3.2
t = t_(d)
lfo = 0.5 + 0.5 * np.sin(2 * np.pi * (1.6 + 1.4 * t / d) * t)
howl = sweep_bp(noise(d), 300, 1400, 4) * (0.35 + 0.65 * lfo)
roar = lp(noise(d), 260) * 1.6
wind = (howl + roar) * env(d, 0.6, 2.6, 2.2)
save('tornado', reverb(wind, 1.2, 0.2))

d = 2.4
t = t_(d)
drone = (np.sin(2 * np.pi * 46 * t) + 0.6 * np.sign(np.sin(2 * np.pi * 69.3 * t)) * 0.35) * env(d, 0.4, 2.0, 2.0)
crackle = np.zeros(int(SR * d))
for _ in range(110):
    pos = int(rng.uniform(0, d - 0.03) * SR)
    ln = int(rng.uniform(0.002, 0.012) * SR)
    crackle[pos:pos + ln] += hp(rng.standard_normal(ln), 2500) * rng.uniform(0.3, 1.0)
zap = bp(crackle, 1500, 9000) * env(d, 0.3, 2.1, 1.8)
save('haki', reverb(mixl(np.tanh(drone * 2.5) * 0.6, zap * 1.4), 1.4, 0.25))

# the demon: a low chord breathing in, three voices a hair apart
d = 4.0
t = t_(d)
chord = np.zeros_like(t)
for f in (55.0, 82.4, 110.0, 164.8):
    for det in (-0.6, 0.0, 0.7):
        chord += np.sin(2 * np.pi * (f + det) * t + rng.uniform(0, 6)) * (1 / (1 + f / 80))
breath = bp(noise(d), 200, 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.35 * t)) * 0.35
swell = np.minimum(1, t / 1.6) * np.exp(-np.maximum(0, t - 2.6) * 2.2)
save('asura', reverb((np.tanh(chord * 0.9) + breath) * swell, 2.5, 0.4, 2500))

# the dragon: a growl that rises and tears
d = 2.2
t = t_(d)
f = 70 + 90 * np.sin(np.pi * np.minimum(1, t / 1.4)) + 6 * np.sin(2 * np.pi * 23 * t)
saw = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR)
formant = bp(saw, 400, 1200, 2) + 0.6 * bp(saw, 1800, 2600, 2)
growl = formant * (0.7 + 0.3 * np.sign(np.sin(2 * np.pi * 31 * t))) + bp(noise(d), 300, 2500) * 0.6
save('dragon', reverb(np.tanh(growl * 2.0) * env(d, 0.25, 1.95, 2.4), 1.8, 0.35, 3500))

# the air cannon: a slap of pressure and the rush after it
d = 1.0
blast = lp(noise(d), 900) * env(d, 0.003, d, 6) * 1.4
save('cannon', reverb(mixl(crack(0.05) * 1.2, thump(0.8, 130, 40, 5), blast, at(whoosh(0.6, 2500, 400, 1.8) * 0.6, 0.05)), 1.2, 0.25))

# the title sting: a taiko strike with a cymbal shimmer
d = 2.0
taiko = mixl(thump(d, 95, 52, 4.5) * 1.3, lp(noise(0.06), 1200) * env(0.06, 0.001, 0.06, 6))
shimmer = hp(noise(d), 5000) * env(d, 0.01, 1.8, 3) * 0.18
save('sting', reverb(mixl(taiko, shimmer, at(thump(0.6, 140, 70, 9) * 0.5, 0.0)), 2.2, 0.38, 3000))

# a ringing blade after the cut
save('ting', reverb(mixl(ring(2.2, [2640, 4030, 6120, 8150], [1.6, 2.1, 3.0, 4.2], [1, 0.6, 0.35, 0.2]), crack(0.01) * 0.3), 1.8, 0.3))

# the heartbeat in the silence
beat = mixl(thump(0.35, 75, 45, 12), at(thump(0.3, 68, 42, 13) * 0.75, 0.22))
save('heartbeat', reverb(lp(beat, 400), 0.8, 0.15))
print('sounds written:', len(os.listdir(OUT)))
