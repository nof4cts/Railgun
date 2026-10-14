"""Synthesizes the Celestial Sage's sounds (assets/celestial/sounds/*.ogg): temple bells built from inharmonic partials,
choir-like drones (detuned voices through vowel formants), gravity (sub-bass sweeps and pressure), and erasure (sound
sucked backwards into silence). No samples: every sound is made here from oscillators and noise.
Usage: make_sage_sounds.py <assets/celestial/sounds>   (needs numpy, scipy, ffmpeg)"""
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
R = np.random.default_rng(9091)


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


# ---------------------------------------------------------------------------------------------------------- the Mantle
d = 4.6
g = choir(d, [110, 165, 220], 'u', 0.35) * env(d, 3.4, 1.0, 1.5) * np.linspace(0.2, 1, int(d * SR))
g += sub(d, 28, 52, 0.7) * env(d, 2.5, 0.6) * 0.9
g += filt(N(d), 'band', (300, 2400)) * np.linspace(0, 1, int(d * SR)) ** 3 * 0.5
for i, at in enumerate([0.6, 1.5, 2.3, 3.0, 3.5, 3.9]):
    g = g + mix(d, (at, bell(d - at, 520 + i * 40, 0.4, 3.5) * 0.12))
save('mantle_gather', hall(g, 3.0, 0.4))

d = 4.0
x = bell(d, 196, 1.2, 0.9) * 1.2
x += choir(d, [196, 294, 392, 587], 'a', 0.15) * env(d, 0.05, 3.4, 2.2) * 0.9
x += sub(d, 70, 24, 1.5) * decay(d, 1.8) * 1.4
x += filt(N(d), 'low', 900) * decay(d, 6) * 1.5
save('mantle_ignite', hall(x, 3.4, 0.45))

d = 2.4
x = reverse_suck(bell(d, 262, 0.8, 2.0)) * 0.7 + choir(d, [262, 196], 'o', 0.2) * env(d, 0.2, 2.0, 2) * 0.5
x += sub(d, 90, 40) * decay(d, 2.5) * 0.6
save('mantle_release', hall(x, 2.2, 0.4))

d = 4.0
x = choir(d, [55, 82.5, 110], 'u', 0.1, vib=3.0) * env(d, 1.0, 1.0, 1) + sub(d, 41, 41) * 0.5 * env(d, 1, 1, 1)
save('hum', hall(x, 2.0, 0.3))

# ---------------------------------------------------------------------------------------------------------- staff
d = 0.55
t = T(d)
f = 220 + 1600 * np.sin(np.pi * np.clip(t / 0.32, 0, 1))
sw = filt(N(d), 'band', (300, 3200)) * np.sin(np.pi * np.clip(t / 0.36, 0, 1)) ** 2
sw += np.sin(2 * np.pi * np.cumsum(f * 0.25) / SR) * np.sin(np.pi * np.clip(t / 0.36, 0, 1)) ** 3 * 0.25
save('staff_swing', hall(sw, 0.8, 0.2))

d = 1.6
x = filt(N(d), 'low', 2400) * decay(d, 28) * 2.2 + sub(d, 140, 45, 1.2) * decay(d, 7) * 1.5
x += bell(d, 880, 0.9, 4.5) * 0.35
save('staff_hit', hall(x, 1.4, 0.3))

# ---------------------------------------------------------------------------------------------------------- orbs
d = 0.9
x = sub(d, 380, 70, 2.5) * decay(d, 6) * 0.9 + filt(N(d), 'band', (120, 900)) * decay(d, 9) * 0.7
x = mix(d, (0, x), (0, sub(0.25, 1200, 300) * decay(0.25, 18) * 0.2))
save('orb_fire', hall(x, 0.9, 0.25))

d = 1.4
# a hole torn in the air: everything rushes inward (reversed) and stops dead, then a low void tone hangs
inrush = reverse_suck(filt(N(0.6), 'band', (200, 5000)) * decay(0.6, 7)) * np.linspace(0.3, 1, int(0.6 * SR))
void = sub(0.8, 48, 36) * decay(0.8, 3.0) * 1.2 + filt(N(0.8), 'low', 160) * decay(0.8, 4) * 0.8
save('orb_erase', hall(mix(d, (0, inrush), (0.6, void)), 1.4, 0.3))

# ---------------------------------------------------------------------------------------------------------- pull
d = 1.4
t = T(d)
thread = np.sin(2 * np.pi * np.cumsum(660 + 240 * t / d) / SR) * env(d, 0.02, 1.2, 2) * 0.5
thread += np.sin(2 * np.pi * np.cumsum(990 + 360 * t / d) / SR) * env(d, 0.02, 1.0, 2) * 0.25
thread += bell(d, 440, 0.6, 3.0) * 0.4 + sub(d, 60, 110) * env(d, 0.2, 1.0) * 0.6
save('pull_bind', hall(thread, 1.6, 0.35))

d = 2.0
x = sub(d, 90, 30, 1.0) * decay(d, 3.0) * 1.6 + filt(N(d), 'low', 1800) * decay(d, 14) * 2.0
x += choir(d, [147, 220], 'a', 0.3) * decay(d, 4) * 0.6 + bell(d, 294, 1.0, 2.6) * 0.5
save('pull_burst', hall(x, 2.0, 0.35))

# ---------------------------------------------------------------------------------------------------------- mirror
d = 0.8
x = reverse_suck(bell(d, 1175, 1.0, 5.0)) * 0.6 + filt(N(d), 'band', (2000, 9000)) * env(d, 0.4, 0.4, 2) * 0.3
save('mirror_vanish', hall(x, 1.2, 0.45))

d = 1.4
x = filt(N(d), 'band', (900, 7000)) * decay(d, 30) * 2.0 + bell(d, 1568, 0.9, 6) * 0.5 + sub(d, 200, 60) * decay(d, 9) * 1.0
save('mirror_strike', hall(x, 1.2, 0.3))

# ---------------------------------------------------------------------------------------------------------- starfall
d = 3.2
x = choir(d, [98, 147, 196, 247], 'o', 0.2) * env(d, 0.8, 1.2, 1.5)
x += bell(d, 98, 0.7, 0.8) * 0.8 + sub(d, 30, 60) * env(d, 1.0, 0.8) * 0.6
save('star_call', hall(x, 3.2, 0.45))

d = 4.4
t = T(d)
roar = filt(N(d), 'band', (60, 2600)) * (t / d) ** 1.6 * 1.4 + rumble(d) * (t / d) ** 2 * 0.8
whine = np.sin(2 * np.pi * np.cumsum(180 + 900 * (t / d) ** 2) / SR) * (t / d) ** 3 * 0.25
roar += whine + choir(d, [73, 110], 'u', 0.4) * (t / d) ** 2 * 0.5
save('star_roar', roar)

d = 6.0
x = filt(N(d), 'low', 3000) * decay(d, 9) * 3.0 + sub(d, 75, 18, 1.0) * decay(d, 0.9) * 2.4 + rumble(d, 20, 120) * decay(d, 0.7) * 1.2
x += filt(N(d), 'band', (400, 4000)) * decay(d, 1.4) * 0.35  # debris
x += bell(d, 65, 0.5, 0.6) * 0.5
save('star_impact', hall(x, 3.6, 0.35, 2200))

# ---------------------------------------------------------------------------------------------------------- thornwood
d = 2.4
x = np.zeros(int(d * SR))
for i in range(14):
    at = 0.04 * i + R.uniform(0, 0.03)
    crack = filt(N(0.5), 'band', (300 + R.uniform(0, 400), 3800)) * decay(0.5, 18 + R.uniform(0, 12)) * (1.0 - i / 20)
    groan = sub(0.6, 140 + R.uniform(-30, 30), 55) * decay(0.6, 5) * 0.5
    x = x + mix(d, (at, crack + fit(groan, 0.5)))
x += rumble(d) * decay(d, 1.6) * 0.8 + bell(d, 349, 0.5, 2.2) * 0.25
save('thorn_erupt', hall(x, 1.6, 0.3))

# ---------------------------------------------------------------------------------------------------------- colossus
d = 5.0
t = T(d)
x = choir(d, [65.4, 98, 130.8, 196, 261.6], 'a', 0.25) * np.clip(t / 3.0, 0, 1) ** 1.5 * env(d, 0.01, 0.8, 1)
x += sub(d, 25, 45) * np.clip(t / 2.0, 0, 1) * 1.0 + rumble(d) * np.clip(t / 3, 0, 1) * 0.5
for at in (0.8, 2.0, 3.0):
    x = x + mix(d, (at, bell(d - at, 131, 0.6, 1.0) * 0.4))
save('colossus_rise', hall(x, 3.6, 0.45))

d = 5.5
t = T(d)
woosh = filt(N(d), 'band', (150, 2200)) * np.exp(-((t - 0.35) / 0.18) ** 2) * 1.6
cut = filt(N(d), 'low', 2600) * np.where(t > 0.5, np.exp(-(t - 0.5) * 6), 0) * 2.4
x = woosh + cut + np.where(t > 0.5, sub(d, 60, 20, 1.0) * np.exp(-(t - 0.5) * 0.9), 0) * 2.0
x += np.where(t > 0.5, rumble(d, 20, 140) * np.exp(-(t - 0.5) * 0.6), 0) * 1.2
x += mix(d, (0.5, bell(d - 0.5, 87, 0.6, 0.7) * 0.5))
save('colossus_cut', hall(x, 3.8, 0.35, 2400))

# ---------------------------------------------------------------------------------------------------------- eclipse
d = 7.0
t = T(d)
# the light goes out: a falling choir, the air pressure dropping, a heartbeat of sub
x = choir(d, [110, 130.8, 164.8], 'u', 0.2, vib=2.5) * env(d, 2.0, 2.0, 1.5)
x *= 1 - 0.5 * (t / d)
x += sub(d, 55, 27, 0.6) * env(d, 1.5, 1.5) * 1.0
for at in np.arange(1.2, 6.5, 0.85):
    x = x + mix(d, (at, sub(0.35, 62, 38) * decay(0.35, 10) * 0.9))
x += filt(N(d), 'band', (1500, 6000)) * env(d, 3.0, 2.0) * 0.08
save('eclipse_cover', hall(x, 4.0, 0.45))

d = 6.0
t = T(d)
ring = bell(d, 147, 1.4, 0.55) * 1.2 + bell(d, 220, 1.0, 0.7) * 0.7
ring += choir(d, [147, 220, 294, 440, 587], 'a', 0.1) * env(d, 0.05, 4.5, 2) * 1.0
sweep = np.sin(2 * np.pi * np.cumsum(2000 - 1600 * np.clip(t / 2.5, 0, 1)) / SR) * decay(d, 1.2) * 0.12
ring += sweep + sub(d, 80, 30, 1.0) * decay(d, 1.0) * 1.4 + filt(N(d), 'low', 1200) * decay(d, 5) * 1.2
save('eclipse_ring', hall(ring, 4.4, 0.45))

d = 5.0
save('bell', hall(bell(d, 233, 1.0, 0.75), 3.4, 0.4))
