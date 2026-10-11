"""Synthesizes every Silver Instinct sound from scratch (no samples): fists, ki, beams, the aura, the awakening.
Writes mono 44.1 kHz Ogg Vorbis into assets/silverinstinct/sounds via ffmpeg."""
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


# fists and feet
for i, p_ in enumerate([1.0, 1.12, 0.88], 1):
    save(f'strike_{i}', reverb(mixl(crack(0.03) * 1.3, thump(0.3, 210 * p_, 70 * p_, 12), sub(0.35, 95, 45, 9) * 0.7), 0.5, 0.12))
    save(f'swing_{i}', reverb(whoosh(0.22, 700 * p_, 5200 * p_, 2.4) * 0.9, 0.4, 0.1))
save('heavy', reverb(mixl(whoosh(0.3, 400, 4200, 2) * 0.7, at(crack(0.06) * 1.5, 0.2), at(thump(1.2, 150, 34, 3.4) * 1.3, 0.2),
                          at(sub(1.2, 85, 32, 2.6, 2.2), 0.2)), 1.4, 0.25, 4000))

# ki: the blast leaving the hand, and landing
for i, p_ in enumerate([1.0, 1.15, 0.9], 1):
    save(f'blast_{i}', reverb(mixl(zap(0.35, 2200 * p_, 380 * p_, 0.8), hp(noise(0.2), 2000) * env(0.2, 0.002, 0.19, 9) * 0.5,
                                    thump(0.25, 160, 70, 14) * 0.5), 0.6, 0.2, 7000))
save('blast_hit', reverb(mixl(crack(0.05) * 1.2, thump(0.6, 140, 40, 6), lp(noise(0.6), 2500) * env(0.6, 0.003, 0.6, 6) * 0.9), 0.9, 0.25))

# moving without moving: the vanish, the instinct slipping a blow, the answer
save('vanish', reverb(mixl(crack(0.015) * 0.8, sweep_bp(noise(0.3), 9000, 900, 2) * env(0.3, 0.005, 0.29, 6), at(crack(0.015) * 0.6, 0.11),
                           sub(0.5, 120, 45, 6) * 0.6), 0.6, 0.25, 8000))
save('dodge', reverb(mixl(whoosh(0.18, 2500, 9500, 4) * 0.8, shimmer(0.5, 3000, 4200, 5, 0.18) * env(0.5, 0.005, 0.48, 5)), 0.6, 0.3, 9000))
save('counter', reverb(mixl(crack(0.05) * 1.5, thump(0.5, 190, 50, 8) * 1.1, sub(0.8, 90, 35, 4, 2.2), shimmer(0.4, 2600, 3400, 4, 0.15)), 1.0, 0.25))

# the gathering hum of ki between the palms
d = 2.2
t = t_(d)
f = 90 + 260 * (t / d) ** 1.6
hum = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5 + np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * 0.25
crackle = np.zeros_like(t)
for _ in range(80):
    pos = int(rng.uniform(0, d - 0.02) * SR)
    ln = int(rng.uniform(0.002, 0.01) * SR)
    crackle[pos:pos + ln] += hp(rng.standard_normal(ln), 3000) * rng.uniform(0.2, 0.8) * (pos / len(t))
save('charge', reverb(mixl(hum * np.minimum(1, t / d) ** 1.3, crackle * 0.8, sweep_bp(noise(d), 200, 3000, 2) * 0.25 * (t / d)), 1.2, 0.3))
save('wave_fire', reverb(mixl(crack(0.06) * 1.3, sub(2.0, 80, 30, 1.2, 2.4) * 1.2, roar(1.8, 60, 1200, 0.05) * 1.1), 1.4, 0.3, 5000))
save('final_fire', reverb(mixl(crack(0.1) * 1.6, sub(3.5, 70, 24, 0.8, 2.6) * 1.4, roar(3.4, 40, 1600, 0.06) * 1.3,
                               shimmer(3.0, 1200, 600, 6, 0.2) * env(3.0, 0.1, 2.8, 1.2)), 2.4, 0.35, 4000))

# the serpent flurry: a storm of fists
d = 1.6
bed = np.zeros(int(SR * d))
k = 0.0
while k < d - 0.12:
    j = mixl(crack(0.02) * 0.9, thump(0.12, 250 * rng.uniform(0.85, 1.25), 90, 22) * 0.8)
    pos = int(k * SR)
    bed[pos:pos + len(j)] += j[: max(0, len(bed) - pos)] * rng.uniform(0.6, 1.0)
    k += rng.uniform(0.03, 0.05)
save('flurry', reverb(mixl(bed, sweep_bp(noise(d), 900, 3200, 1.5) * 0.25) * env(d, 0.03, 1.5, 1.2), 0.8, 0.2))

# boom: the meteor slam, the sphere, the final radiance
for name, d, f0 in (('slam', 2.4, 120), ('sphere_boom', 4.0, 90), ('final_boom', 4.5, 80)):
    save(name, reverb(mixl(crack(0.1) * 1.6, sub(d, f0 * 0.8, 24, 1.3, 2.6) * 1.4, thump(d, f0, 26, 1.6) * 1.2,
                           lp(noise(d), 3000) * env(d, 0.003, d, 2.0) * 1.1, bp(noise(d), 200, 1500) * env(d, 0.2, d, 1.8) * 0.5), 2.8, 0.4, 3500))
save('launch', reverb(mixl(crack(0.04), thump(0.6, 200, 60, 7), sweep_bp(noise(0.6), 400, 6000, 1.8) * env(0.6, 0.02, 0.58, 4) * 0.7), 0.8, 0.2))

# the sphere gathering: the world's energy rising into one place
d = 5.0
t = t_(d)
choir = shimmer(d, 220, 880, 8, 0.25)
swell = sweep_bp(noise(d), 120, 5000, 1.4) * (t / d) ** 2 * 0.7
save('sphere_gather', reverb((choir + swell + np.sin(2 * np.pi * 41 * t) * 0.4) * np.minimum(1, t / 4.5), 3.0, 0.45, 5000))
save('sphere_throw', reverb(mixl(whoosh(0.8, 200, 3000, 1.6) * 0.9, sub(1.4, 80, 30, 2.0, 2.3), crack(0.03)), 1.4, 0.3))
d = 5.0
t = t_(d)
storm = (bp(noise(d), 80, 600) * 1.3 + sweep_bp(noise(d), 300, 3500, 2) * 0.5) * (0.6 + 0.4 * np.sin(2 * np.pi * 3.0 * t * (1 + t / d))) * np.minimum(1, t / d) ** 1.4
save('final_charge', reverb(mixl(storm, shimmer(d, 300, 1200, 7, 0.22) * np.minimum(1, t / d) ** 2, np.sin(2 * np.pi * 33 * t) * 0.5 * np.minimum(1, t / d)), 2.5, 0.4))

# the awakening: a heartbeat, a breath, the surge
beat = mixl(thump(0.35, 75, 45, 12), at(thump(0.3, 68, 42, 13) * 0.75, 0.22))
save('heartbeat', reverb(lp(beat, 400), 0.8, 0.15))
for name, d, scale in (('awaken_omen', 3.0, 1.0), ('awaken_zenith', 4.0, 1.4)):
    t = t_(d)
    surge = mixl(crack(0.08) * 1.4, sub(d, 90, 28, 1.4, 2.6) * 1.3, shimmer(d, 500, 2000, 8, 0.3) * env(d, 0.02, d, 1.5) * scale,
                 sweep_bp(noise(d), 300, 7000, 1.5) * env(d, 0.01, d, 2.0) * 0.6, bell(d, 659.3, 0.8, 0.3))
    save(name, reverb(surge, 2.8, 0.45, 6000))
d = 3.0
t = t_(d)
save('aura_hum', reverb((sum(np.sin(2 * np.pi * f * t + 0.5 * np.sin(2 * np.pi * 0.7 * t)) for f in (110, 165, 220)) * 0.2
                         + bp(noise(d), 300, 2000) * (0.5 + 0.5 * np.sin(2 * np.pi * 5 * t)) * 0.2) * env(d, 0.4, 2.5, 1.0), 1.5, 0.3), peak=0.5)
save('revert', reverb(mixl(reverse(shimmer(1.0, 800, 2400, 6, 0.3) * env(1.0, 0.02, 0.95, 3)), at(sub(0.8, 70, 35, 4) * 0.6, 0.9)), 1.4, 0.3))

for i, (f0, f1, d) in enumerate([(78, 31, 1.6), (90, 36, 1.3)], 1):
    save(f'bass_{i}', mixl(sub(d, f0, f1, 2.4, 2.2) * 1.3, crack(0.02) * 0.6), peak=0.98)
print('sounds written:', len(os.listdir(OUT)))
