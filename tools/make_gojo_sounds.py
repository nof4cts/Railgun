"""Synthesizes the sounds of Gojo's rework (assets/gojo/sounds/*.ogg): Blue's suction and grinding, Red's whine, crack
and blast, Purple's crackle and the tearing roar of erasure, the void's drone, Infinity's glass. No samples: every
sound is made here from oscillators and noise.
Usage: make_gojo_sounds.py <assets/gojo/sounds>   (needs numpy, scipy, ffmpeg)"""
import os
import subprocess
import sys
import tempfile

import numpy as np
import scipy.io.wavfile as wav
import scipy.signal as sig

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
SR = 44100
R = np.random.default_rng(5150)


def T(d):
    return np.arange(int(d * SR)) / SR


def N(d):
    return R.standard_normal(int(d * SR))


def filt(x, kind, f, order=2):
    if kind == 'band':
        lo, hi = f
        b, a = sig.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 200) / (SR / 2)], 'band')
    else:
        b, a = sig.butter(order, min(f, SR / 2 - 200) / (SR / 2), kind)
    return sig.lfilter(b, a, x)


def env(d, a, r, curve=3.0):
    t = T(d)
    e = np.minimum(1, t / max(a, 1e-4))
    tail = np.clip((d - t) / max(r, 1e-4), 0, 1) ** curve
    return e * tail


def decay(d, k):
    return np.exp(-T(d) * k)


def fit(x, d):
    n = int(d * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def mix(d, *parts):
    out = np.zeros(int(d * SR))
    for start, x in parts:
        i = int(start * SR)
        x = x[:max(0, len(out) - i)]
        out[i:i + len(x)] += x
    return out


def bell(d, f0, bright=1.0, k=1.6):
    """A temple bell: partials at the ratios of a cast bell (hum, prime, tierce, quint, nominal...), each beating
    slightly and dying at its own rate."""
    t = T(d)
    ratios = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166, 5.433]
    amps = [0.55, 0.9, 0.6, 0.35, 0.7, 0.3, 0.22, 0.18, 0.12 * bright, 0.08 * bright]
    x = np.zeros_like(t)
    for i, (r, a) in enumerate(zip(ratios, amps)):
        f = f0 * r
        beat = 1 + 0.15 * np.sin(2 * np.pi * (0.7 + i * 0.31) * t)
        x += a * np.sin(2 * np.pi * f * t + R.uniform(0, 6)) * np.exp(-t * k * (0.4 + 0.35 * i)) * beat
    strike = filt(N(0.03), 'high', 2500) * decay(0.03, 140) * 0.5 * bright
    x[:len(strike)] += strike
    return x


def choir(d, freqs, vowel='a', breath=0.25, vib=4.8):
    """Several detuned voices (bright pulse waves with vibrato) through the formants of a sung vowel."""
    t = T(d)
    formants = {'a': [(800, 1.0), (1150, 0.5), (2900, 0.25)], 'o': [(450, 1.0), (800, 0.45), (2830, 0.15)],
                'u': [(325, 1.0), (700, 0.3), (2530, 0.1)], 'e': [(400, 1.0), (1600, 0.5), (2700, 0.3)]}[vowel]
    src = np.zeros_like(t)
    for f in freqs:
        for k in range(5):
            det = f * (1 + R.uniform(-0.006, 0.006))
            ph = 2 * np.pi * np.cumsum(det * (1 + 0.006 * np.sin(2 * np.pi * (vib + R.uniform(-0.6, 0.6)) * t + R.uniform(0, 6)))) / SR
            src += sig.square(ph, duty=0.18 + 0.05 * k / 5) * 0.2
    src += N(d) * breath * 0.3
    out = np.zeros_like(t)
    for f, a in formants:
        out += a * filt(src, 'band', (f * 0.88, f * 1.12), 2)
    return out


def sub(d, f0, f1, curve=2.0):
    """A sine sweeping from f0 to f1: gravity, pressure."""
    t = T(d)
    f = f1 + (f0 - f1) * (1 - t / d) ** curve
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def rumble(d, lo=30, hi=180):
    return filt(N(d), 'band', (lo, hi), 2) * 3.0


def hall(x, size=2.6, wet=0.35, damp=3500):
    """A long stone hall: exponentially decaying filtered noise as the impulse response."""
    n = int(size * SR)
    ir = R.standard_normal(n) * np.exp(-np.arange(n) / SR * (6.9 / size))
    ir = filt(ir, 'low', damp)
    ir /= np.sqrt(np.sum(ir ** 2))
    w = sig.fftconvolve(x, ir)[:len(x) + n // 2]
    out = np.pad(x, (0, len(w) - len(x))) * (1 - wet) + w * wet
    return out


def reverse_suck(x):
    """Sound sucked backwards: the tail of x reversed, swelling into a cut."""
    return x[::-1]


def save(name, x, peak=0.89):
    x = np.asarray(x, dtype=np.float64)
    fade = min(len(x), int(0.02 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    m = np.max(np.abs(x)) or 1
    x = x / m * peak
    with tempfile.TemporaryDirectory() as td:
        w = os.path.join(td, 'a.wav')
        wav.write(w, SR, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', w, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')],
                       check=True)
    print(name, f'{len(x) / SR:.2f}s')



def crackle(d, rate=60, bright=4000):
    """Electric crackle: sparse sharp clicks through a bright band."""
    n = int(d * SR)
    x = np.zeros(n)
    k = int(rate * d)
    for i in R.integers(0, n, k):
        L = int(SR * R.uniform(0.002, 0.012))
        seg = R.standard_normal(min(L, n - i)) * np.exp(-np.arange(min(L, n - i)) / (L / 4 + 1))
        x[i:i + len(seg)] += seg * R.uniform(0.3, 1.0)
    return filt(x, 'band', (900, bright)) * 2.5


def whine(d, f0, f1):
    t = T(d)
    f = f0 + (f1 - f0) * (t / d) ** 2
    return np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * np.sin(2 * np.pi * np.cumsum(f * 2.01) / SR)


def boom(d, f0=70, f1=24, k=1.4):
    return sub(d, f0, f1, 1.2) * decay(d, k) * 2.0 + filt(N(d), 'low', 900) * decay(d, 6) * 2.2 + rumble(d) * decay(d, 1.0) * 1.2


# ---- Red
d = 1.0
save('red_charge', hall(whine(d, 300, 1400) * np.linspace(0.1, 1, int(d * SR)) ** 2 * 0.5 + crackle(d, 40, 5000) * np.linspace(0, 1, int(d * SR)) * 0.5
                        + sub(d, 50, 90) * 0.4, 0.8, 0.2))
d = 1.2
x = filt(N(d), 'band', (1500, 9000)) * decay(d, 40) * 2.5 + filt(N(d), 'band', (200, 2500)) * np.sin(np.pi * np.clip(T(d) / 0.5, 0, 1)) ** 2 * 0.8
x += sub(d, 160, 50) * decay(d, 8) * 1.2
save('red_fire', hall(x, 1.0, 0.25))
d = 3.2
save('red_impact', hall(boom(d, 90, 22, 1.6) + filt(N(d), 'band', (300, 4000)) * decay(d, 3) * 0.5, 2.2, 0.3, 2600))

# ---- Blue
d = 1.6
x = np.flip(filt(N(d), 'band', (200, 3000)) * decay(d, 4)) * 0.9 + sub(d, 40, 70) * np.linspace(0.2, 1, int(d * SR)) * 0.9
x += whine(d, 900, 300) * np.linspace(1, 0.2, int(d * SR)) * 0.15
save('blue_charge', hall(x, 1.2, 0.3))
d = 4.0
t = T(d)
grind = filt(N(d), 'band', (60, 900)) * (0.7 + 0.3 * np.sin(2 * np.pi * 3.1 * t)) * 1.4 + sub(d, 38, 38) * 0.8
grind += np.flip(filt(N(d), 'band', (400, 3500)) * decay(d, 1.2)) * 0.3 + crackle(d, 25, 2500) * 0.25
save('blue_drag', grind * env(d, 0.2, 0.8, 1))
d = 2.2
x = np.flip(filt(N(0.5), 'band', (200, 6000)) * decay(0.5, 6))
save('blue_crush', hall(mix(d, (0, x * 1.2), (0.5, boom(1.7, 60, 20, 2.2))), 1.6, 0.3))

# ---- Maximum Red
d = 5.0
x = mix(d, (0, filt(N(0.25), 'high', 2000) * decay(0.25, 30) * 2), (0.02, boom(4.9, 110, 20, 0.9) * 1.3))
x += filt(N(d), 'band', (200, 3500)) * np.exp(-((T(d) - 0.4) / 0.35) ** 2) * 1.6
save('maxred_fire', hall(x, 3.0, 0.3, 2400))

# ---- Purple
d = 2.4
t = T(d)
x = crackle(d, 140, 7000) * (0.4 + 0.6 * t / d) + whine(d, 120, 520) * (t / d) ** 2 * 0.35 + sub(d, 45, 80) * 0.6 * (t / d)
save('purple_merge', hall(x, 1.6, 0.3))
d = 3.6
x = mix(d, (0, filt(N(0.2), 'high', 1800) * decay(0.2, 25) * 2.5), (0.0, boom(3.6, 80, 24, 1.2)))
x += filt(N(d), 'band', (300, 5000)) * np.exp(-T(d) * 1.4) * 1.2 + crackle(d, 60, 6000) * decay(d, 1.5) * 0.6
save('purple_fire', hall(x, 2.4, 0.3))
d = 7.0
x = mix(d, (0, filt(N(0.3), 'high', 1500) * decay(0.3, 15) * 3), (0, boom(7, 70, 16, 0.6) * 1.5))
tear = filt(N(d), 'band', (150, 4000)) * np.exp(-T(d) * 0.5) * 1.4
x += tear + crackle(d, 80, 7000) * decay(d, 0.8) * 0.7
save('purple_big', hall(x, 4.0, 0.35, 2400))

# ---- the incantation (a struck tone and a breath of air, no voice)
d = 2.6
x = bell(d, 440, 0.6, 1.4) * 0.6 + filt(N(d), 'band', (600, 5000)) * env(d, 0.05, 2.2, 3) * 0.12 + sub(d, 55, 55) * env(d, 0.3, 2, 2) * 0.4
save('chant', hall(x, 3.0, 0.5))

# ---- Hollow Nuke
d = 4.0
t = T(d)
x = whine(d, 60, 900) * (t / d) ** 2.5 * 0.4 + crackle(d, 120, 6000) * (t / d) ** 1.5 + sub(d, 30, 70) * (t / d) * 1.0 + rumble(d) * (t / d) * 0.8
save('nuke_charge', hall(x, 2.0, 0.3))
d = 10.0
x = mix(d, (0, filt(N(0.4), 'high', 1200) * decay(0.4, 10) * 3), (0, boom(10, 60, 12, 0.35) * 1.8))
x += filt(N(d), 'band', (80, 2500)) * np.exp(-T(d) * 0.4) * 1.3 + crackle(d, 50, 5000) * np.exp(-T(d) * 0.5) * 0.5
save('nuke_blast', hall(x, 5.0, 0.35, 2000))

# ---- Unlimited Void
d = 0.9
x = filt(N(0.05), 'band', (1500, 7000)) * decay(0.05, 60)
save('domain_sign', hall(mix(d, (0, x * 1.5), (0.02, bell(0.85, 1320, 0.8, 5) * 0.4)), 1.2, 0.35))
d = 2.6
t = T(d)
woosh = filt(N(d), 'band', (200, 6000)) * np.clip(t / 1.6, 0, 1) ** 3 * (t < 1.7) * 1.4
hitx = mix(d, (1.7, boom(0.9, 120, 40, 4) * 1.2), (1.7, filt(N(0.3), 'high', 2000) * decay(0.3, 20) * 1.5))
save('domain_open', hall(woosh + hitx, 1.8, 0.3))
d = 8.0
t = T(d)
x = choir(d, [55, 82.5, 110, 165], 'u', 0.1, vib=2.0) * env(d, 1.5, 2.5, 1.5) * 0.7 + sub(d, 32, 32) * env(d, 1, 2, 1) * 0.6
for f0 in (1760, 2093, 2637, 3136):
    x += np.sin(2 * np.pi * f0 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * R.uniform(0.2, 0.6) * t)) * env(d, 2, 3, 2) * 0.03
save('domain_void', hall(x, 4.5, 0.5))

# ---- Infinity
d = 1.6
x = bell(d, 1568, 0.9, 3.0) * 0.4
x = x + mix(d, (0, filt(N(0.5), 'band', (800, 6000)) * np.linspace(0, 1, int(0.5 * SR)) ** 2 * 0.6)) + sub(d, 300, 80) * decay(d, 6) * 0.3
save('infinity_on', hall(x, 2.0, 0.45))
d = 0.8
save('infinity_ripple', hall(bell(d, 2349, 0.5, 7) * 0.4 + filt(N(d), 'band', (2000, 9000)) * decay(d, 30) * 0.4, 0.9, 0.4))
