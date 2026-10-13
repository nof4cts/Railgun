"""Synthesizes Requiem Rose's sound effects (assets/requiem/sounds/*.ogg) from noise, oscillators, filters and a
convolution hall: whooshes for the scythe, ringing 'shing' for every cut, the lash, the tear in space, the great drones
of the Last Bloom. (The aura and the Calamity Ray's charge are the author's own recordings and are not touched here.)
Usage: make_rose_sounds.py <assets/requiem/sounds>   (needs numpy, scipy, ffmpeg)"""
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
rng = np.random.default_rng(1307)


def t_(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.standard_normal(int(d * SR))


def bp(x, lo, hi, order=2):
    b, a = sig.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band')
    return sig.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = sig.butter(order, f / (SR / 2), 'low')
    return sig.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = sig.butter(order, f / (SR / 2), 'high')
    return sig.lfilter(b, a, x)


def sweep_bp(x, f0, f1, q=3.0, steps=64):
    """A band-pass whose centre glides from f0 to f1 (log), processed in blocks with overlap-add."""
    n = len(x)
    out = np.zeros(n)
    win = int(n / steps) * 2 or 2
    hop = win // 2
    w = np.hanning(win)
    for i, s in enumerate(range(0, n - hop, hop)):
        k = s / max(1, n - win)
        f = f0 * (f1 / f0) ** min(1, k)
        lo, hi = f / (1 + 1 / q), f * (1 + 1 / q)
        seg = x[s:s + win]
        if len(seg) < win:
            seg = np.pad(seg, (0, win - len(seg)))
        y = bp(seg, max(30, lo), min(SR / 2 - 200, hi)) * w
        out[s:s + win] += y[:len(out[s:s + win])]
    return out


def env(d, a, r, shape=2.0):
    t = t_(d)
    e = np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) / max(r, 1e-4))
    return e ** (shape / 2)


def bell(d, peak=0.5, width=0.25):
    t = t_(d) / d
    return np.exp(-((t - peak) / width) ** 2)


def sine(f, d, phase=0):
    if callable(f):
        t = t_(d)
        ph = np.cumsum(f(t)) / SR * 2 * np.pi
        return np.sin(ph + phase)
    return np.sin(2 * np.pi * f * t_(d) + phase)


def saw(f, d):
    t = t_(d)
    ph = np.cumsum(f(t) if callable(f) else np.full(len(t), f)) / SR
    return 2 * (ph % 1) - 1


def ring(freqs, d, decay, amps=None):
    """Inharmonic ringing partials: the 'shing' of an energy blade."""
    t = t_(d)
    out = np.zeros(len(t))
    for i, f in enumerate(freqs):
        a = amps[i] if amps else 1 / (1 + i * 0.6)
        out += a * np.sin(2 * np.pi * f * t + rng.random() * 6) * np.exp(-t / (decay * (1 - i * 0.08)))
    return out


def sub(d, f0=72, f1=32, decay=0.5):
    t = t_(d)
    f = f1 + (f0 - f1) * np.exp(-t / 0.12)
    return sine(lambda tt: f1 + (f0 - f1) * np.exp(-tt / 0.12), d) * np.exp(-t / decay) * np.minimum(1, t / 0.004)


def crack(d=0.04):
    x = hp(noise(d), 1500) * env(d, 0.0005, 0.008)
    return x


def clicks(d, rate0, rate1, width=0.004):
    t = t_(d)
    out = np.zeros(len(t))
    pos = 0.0
    while pos < d:
        k = pos / d
        rate = rate0 + (rate1 - rate0) * k
        i = int(pos * SR)
        c = crack(width * 4) * (0.6 + 0.4 * rng.random())
        out[i:i + len(c)] += c[:len(out) - i]
        pos += 1.0 / max(rate, 1) * (0.6 + 0.8 * rng.random())
    return out


def hall(x, length=1.8, mix=0.25, bright=6000):
    n = int(length * SR)
    ir = noise(length) * np.exp(-t_(length) / (length / 5.5))
    ir = lp(ir, bright)
    ir /= np.sqrt((ir ** 2).sum())
    wet = sig.fftconvolve(x, ir)[:len(x) + n]
    dry = np.pad(x, (0, len(wet) - len(x)))
    return dry * (1 - mix * 0.5) + wet * mix


def sat(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def pad(x, d):
    n = int(d * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:max(n, len(x))]


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def at(x, sec, total=None):
    pre = np.zeros(int(sec * SR))
    y = np.concatenate([pre, x])
    return y if total is None else pad(y, total)


def save(name, x, peak=0.92):
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    # trim the silent tail of the hall
    loud = np.nonzero(np.abs(x) > 0.003)[0]
    if len(loud):
        x = x[:min(len(x), loud[-1] + int(0.05 * SR))]
    # fade the very end so nothing clicks
    f = min(len(x), int(0.01 * SR))
    x[-f:] *= np.linspace(1, 0, f)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav.write(tmp.name, SR, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', tmp.name, '-c:a', 'libvorbis', '-q:a', '5',
                        os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(tmp.name)
    print(name, round(len(x) / SR, 2))


# ------------------------------------------------------------------------------------------------ the scythe

def whoosh(d, f0, f1, peak=0.45, q=2.5, gain=1.0):
    return sweep_bp(noise(d), f0, f1, q) * bell(d, peak, 0.22) * gain


def shing(base, d=0.6, decay=0.22):
    fr = [base, base * 2.37, base * 3.91, base * 5.12, base * 6.8]
    return ring(fr, d, decay) * np.minimum(1, t_(d) / 0.002)


swing = mix(whoosh(0.32, 500, 2600, 0.5, 2.2), 0.15 * shing(2900, 0.32, 0.08) * bell(0.32, 0.55, 0.2))
save('swing', hall(swing, 0.8, 0.18))

swing_heavy = mix(whoosh(0.55, 220, 1500, 0.55, 2.0), 0.5 * lp(noise(0.55), 300) * bell(0.55, 0.55, 0.25), 0.12 * shing(2200, 0.55, 0.1))
save('swing_heavy', hall(swing_heavy, 1.0, 0.2))

cut = mix(1.2 * crack(0.05), 0.55 * shing(1850, 0.7, 0.18), 0.8 * sub(0.25, 140, 60, 0.07), 0.3 * hp(noise(0.12), 2500) * env(0.12, 0.001, 0.03))
save('cut', sat(hall(cut, 1.0, 0.22) / np.max(np.abs(cut)), 2.4))

cut_heavy = mix(1.4 * crack(0.06), 0.6 * shing(1300, 1.2, 0.32), 1.1 * sub(0.7, 110, 38, 0.22), 0.5 * lp(noise(0.5), 900) * env(0.5, 0.002, 0.09),
                0.3 * clicks(0.35, 200, 30))
save('cut_heavy', sat(hall(cut_heavy, 1.6, 0.3), 1.4))

# ------------------------------------------------------------------------------------------------ crescent

throw = mix(whoosh(0.5, 400, 3500, 0.35, 2.4, 1.2), 0.35 * at(shing(2600, 0.6, 0.15), 0.12))
save('crescent_throw', hall(throw, 0.9, 0.2))

d = 1.5
t = t_(d)
spin = sweep_bp(noise(d), 1800, 700, 2.0) * (0.5 + 0.5 * np.sin(2 * np.pi * 13 * t) ** 2) * np.exp(-t / 0.9) * np.minimum(1, t / 0.03)
spin += 0.12 * sine(lambda tt: 2600 - 600 * tt, d) * np.exp(-t / 0.7) * (0.5 + 0.5 * np.sin(2 * np.pi * 13 * t))
save('crescent_spin', hall(spin, 0.8, 0.2))

catch = mix(1.0 * crack(0.04), 0.6 * shing(2400, 0.8, 0.2), 0.3 * sub(0.2, 160, 90, 0.05))
save('crescent_catch', sat(hall(catch, 0.9, 0.22) / np.max(np.abs(catch)), 2.0))

# ------------------------------------------------------------------------------------------------ tether

d = 0.6
zipn = sweep_bp(noise(d), 600, 5000, 3) * env(d, 0.02, 0.25)
tether_fire = mix(zipn, 0.6 * clicks(0.5, 25, 140), 0.25 * sine(lambda tt: 900 + 2600 * tt / 0.6, d) * env(d, 0.01, 0.2))
save('tether_fire', hall(tether_fire, 0.9, 0.2))

d = 0.9
bind = mix(0.5 * sine(lambda tt: 2400 * np.exp(-tt * 2.2) + 400, d) * env(d, 0.005, 0.3),
           0.4 * sine(lambda tt: 3100 * np.exp(-tt * 2.0) + 500, d) * env(d, 0.005, 0.3), 0.7 * clicks(0.3, 120, 40), 0.7 * crack(0.05),
           0.5 * sub(0.3, 120, 60, 0.08))
save('tether_bind', sat(hall(bind, 1.2, 0.25) / np.max(np.abs(bind)), 1.8))

d = 0.7
yank = mix(sweep_bp(noise(d), 300, 1800, 2.0) * (t_(d) / d) ** 2 * np.exp(-np.maximum(0, t_(d) - 0.55) / 0.04), 0.5 * clicks(0.6, 40, 160),
           0.6 * at(sub(0.25, 90, 45, 0.08), 0.55))
save('tether_yank', hall(yank, 0.9, 0.2))

# ------------------------------------------------------------------------------------------------ vanish

d = 0.45
swell = sweep_bp(noise(d), 300, 4000, 2.0) * (t_(d) / d) ** 3
vanish = mix(swell, 0.9 * at(crack(0.05), d - 0.01), 0.3 * at(shing(3400, 0.5, 0.12), d), 0.4 * at(sub(0.2, 120, 70, 0.05), d))
save('vanish', sat(hall(vanish, 1.0, 0.25) / np.max(np.abs(vanish)), 1.8))

# ------------------------------------------------------------------------------------------------ requiem

d = 1.0
t = t_(d)
trem = (0.5 + 0.5 * np.sin(2 * np.pi * 14 * t))
strings = sum(lp(saw(lambda tt, f=f: f * (1 + 0.004 * np.sin(5 * tt)), d), 2400) for f in (220, 233.1, 329.6, 349.2, 440, 466.2))
gather = mix(0.25 * strings * trem * (t / d) ** 2.2, 0.3 * sweep_bp(noise(d), 200, 3000, 2) * (t / d) ** 3)
save('requiem_gather', hall(gather, 1.5, 0.3))

d = 3.0
blast = mix(1.3 * sub(2.0, 90, 26, 0.9), 0.9 * lp(noise(1.2), 1400) * env(1.2, 0.003, 0.35), 0.8 * crack(0.08),
            0.4 * clicks(1.4, 300, 8), *(0.35 * at(shing(900 + 260 * i, 1.4, 0.4), 0.02 * i) for i in range(6)))
save('requiem_burst', sat(hall(pad(blast, d), 2.4, 0.35), 1.6))

# ------------------------------------------------------------------------------------------------ rift

d = 1.4
t = t_(d)
tear = bp(clicks(1.2, 600, 120, 0.002), 900, 6000) * 1.2
tear = mix(tear, 0.5 * sweep_bp(noise(1.2), 2500, 500, 1.5) * env(1.2, 0.005, 0.4), 1.2 * sub(1.2, 100, 30, 0.5), 1.2 * crack(0.07),
           0.4 * shing(700, 1.4, 0.5))
save('rift_tear', sat(hall(pad(tear, d), 2.0, 0.3), 1.5))

d = 3.2
t = t_(d)
drone = mix(0.6 * lp(saw(lambda tt: 45 + 1.5 * np.sin(0.7 * tt), d), 300), 0.5 * lp(saw(lambda tt: 67.6 + 2 * np.sin(0.5 * tt + 1), d), 400),
            0.35 * bp(noise(d), 300, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.6 * t)), 0.12 * sine(lambda tt: 1800 + 200 * np.sin(3 * tt), d))
drone *= np.minimum(1, t / 0.4) * np.minimum(1, (d - t) / 0.6)
save('rift_hum', hall(drone, 2.0, 0.3))

d = 2.4
t = t_(d)
implode = sweep_bp(noise(0.5), 5000, 300, 2) * (t_(0.5) / 0.5) ** 3
snap = mix(implode, at(mix(1.4 * crack(0.08), 1.4 * sub(1.6, 120, 28, 0.6), 0.8 * lp(noise(0.8), 1100) * env(0.8, 0.002, 0.2),
                            0.4 * shing(1100, 1.2, 0.35)), 0.5))
save('rift_snap', sat(hall(pad(snap, d), 2.2, 0.32), 1.6))

# ------------------------------------------------------------------------------------------------ last bloom

d = 2.0
t = t_(d)
rise = mix(0.4 * lp(saw(lambda tt: 55 * (1 + tt), d), 600) * np.minimum(1, t / 0.4),
           0.25 * sum(sine(lambda tt, f=f: f * (1 + 0.6 * tt / 2), d) for f in (880, 1320, 1760)) * (t / d) ** 2,
           0.4 * sweep_bp(noise(d), 300, 2500, 2) * (t / d) ** 2)
rise *= np.minimum(1, (d - t) / 0.3)
save('bloom_rise', hall(rise, 2.0, 0.35))

d = 4.0
t = t_(d)
cluster = sum(lp(saw(lambda tt, f=f: f * (1 + 0.003 * np.sin(3 * tt + f)), d), 700) for f in (41.2, 43.6, 61.7, 82.4, 87.3))
skyw = mix(0.3 * cluster * np.minimum(1, t / 1.5), 0.5 * sweep_bp(noise(d), 150, 700, 1.5) * np.minimum(1, t / 2.0),
           0.15 * sine(lambda tt: 600 + 40 * np.sin(tt), d) * np.minimum(1, t / 2.5))
skyw *= np.minimum(1, (d - t) / 1.0)
save('bloom_sky', hall(skyw, 2.5, 0.4))

d = 3.2
t = t_(d)
grow = mix(0.5 * sum(bp(saw(lambda tt, f=f: f * (1 + 1.4 * tt / d), d), 200, 3000) for f in (110, 164.8, 220)) * (t / d),
           0.6 * lp(noise(d), 200) * np.minimum(1, t / 0.6), 0.25 * clicks(d, 20, 120), 0.2 * ring([1400, 2100, 3300], d, 1.5) * (t / d) ** 2)
grow *= np.minimum(1, (d - t) / 0.25)
save('bloom_grow', sat(hall(grow, 2.0, 0.3), 1.3))

d = 1.6
bcut = mix(1.2 * whoosh(1.2, 120, 900, 0.65, 1.6), 0.35 * at(shing(1500, 1.0, 0.35), 0.8), 1.0 * at(sub(0.8, 80, 30, 0.4), 0.85))
save('bloom_cut', hall(pad(bcut, d), 2.4, 0.35))

d = 6.0
t = t_(d)
boom = mix(1.6 * sub(4.0, 70, 22, 1.6), 1.0 * lp(noise(2.5), 900) * env(2.5, 0.004, 0.6), 1.0 * crack(0.1), 0.5 * clicks(3.5, 200, 5),
           0.6 * lp(noise(5), 180) * env(5, 0.05, 1.8), *(0.25 * at(shing(600 + 190 * i, 2.0, 0.6), 0.03 * i) for i in range(5)))
save('bloom_impact', sat(hall(pad(boom, d), 3.5, 0.4), 1.8))

# ------------------------------------------------------------------------------------------------ the small things

save('sub', hall(mix(1.5 * sub(1.0, 76, 30, 0.45), 0.4 * crack(0.03)), 1.2, 0.18))

d = 0.9
lub = lp(sub(0.25, 70, 45, 0.06) + 0.4 * lp(noise(0.25), 120) * env(0.25, 0.003, 0.05), 300)
heart = mix(lub, 0.7 * at(lub, 0.24))
save('heartbeat', hall(pad(heart, d), 1.0, 0.15))

d = 1.4
t = t_(d)
summon = mix(0.3 * sum(sine(lambda tt, f=f: f * (1 + 0.5 * tt / d), d) for f in (330, 495, 742)) * np.minimum(1, t / 0.5) * np.exp(-t / 0.8),
             0.5 * sweep_bp(noise(d), 200, 1800, 2) * bell(d, 0.4, 0.3), 0.4 * lp(saw(lambda tt: 55 + 20 * tt, d), 300) * bell(d, 0.4, 0.35))
save('summon', hall(summon, 1.8, 0.35))

d = 2.4
t = t_(d)
roar = bp(noise(d), 300, 3800) * (0.75 + 0.25 * np.sin(2 * np.pi * 21 * t)) * np.minimum(1, t / 0.02) * np.minimum(1, (d - t) / 0.4)
fire = mix(1.5 * crack(0.08), 1.3 * sub(1.2, 90, 30, 0.5), 0.8 * roar, 0.18 * sine(lambda tt: 2200 + 150 * np.sin(7 * tt), d) * np.minimum(1, (d - t) / 0.4),
           0.4 * lp(noise(0.6), 900) * env(0.6, 0.002, 0.15))
save('ray_fire', sat(hall(fire, 1.8, 0.3), 1.6))

d = 1.6
t = t_(d)
rend = mix(0.6 * hp(noise(d), 2000) * np.exp(-t / 0.5), 0.4 * clicks(1.2, 120, 10), 0.9 * sub(0.5, 90, 40, 0.15), 0.4 * bp(noise(d), 200, 800) * np.exp(-t / 0.4))
save('ray_end', hall(rend, 1.6, 0.3))
print('sounds ok')
