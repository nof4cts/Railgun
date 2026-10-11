"""Synthesizes every Orbital Arsenal sound from scratch (no samples): the uplink's clicks and beeps, the climb to orbit,
mirrors unfolding, sunlight gathering, the lance firing and roaring, the sky cracking, a breach opening, another world
approaching, and the touch. Writes mono 44.1 kHz Ogg Vorbis into assets/orbital_arsenal/sounds via ffmpeg."""
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


def beep(d, f, amp=1.0):
    t = t_(d)
    return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * f * 2 * t)) * env(d, 0.004, d, 6) * amp


# --- the uplink ---------------------------------------------------------------------------------------------------
d = 0.35
save('select', mixl(tick_click(0.05, 2600), at(beep(0.12, 1320, 0.6), 0.04), at(beep(0.14, 1760, 0.5), 0.13)), 0.7)

d = 0.9
arm = mixl(tick_click(0.06, 1800) * 1.2, at(tick_click(0.05, 2400), 0.09), at(beep(0.5, 880, 0.7) * env(0.5, 0.01, 0.5, 3), 0.2),
           at(lp(noise(0.4), 300) * env(0.4, 0.01, 0.4, 6) * 0.6, 0.2))
save('arm', reverb(pad(arm, d), 0.6, 0.2, 5000), 0.8)

# five beeps quickening, then a long tone
d = 4.2
cd = np.zeros(int(SR * d))
times = [0.0, 0.8, 1.45, 1.95, 2.35, 2.65]
for i, tt in enumerate(times):
    cd = mixl(cd, at(beep(0.16, 990 if i < 5 else 1480, 0.7), tt))
cd = mixl(cd, at(beep(1.2, 1480, 0.5) * env(1.2, 0.01, 1.2, 1.5), 2.9))
save('countdown', reverb(pad(cd, d), 0.9, 0.25, 6000), 0.7)

# the climb to orbit: wind rising to a roar, then cut to the hush of space
d = 3.2
t = t_(d)
climb = sweep_bp(noise(d), 200, 3200, 1.4) * np.minimum(1, t / 2.2) ** 2 * (t < 2.6) + lp(brown(d), 150) * 0.6 * np.minimum(1, t / 2.0) * (t < 2.6)
climb = mixl(climb, at(shimmer(0.8, 700, 1400, 6, 0.25) * env(0.8, 0.05, 0.75, 3), 2.55))
save('ascent', reverb(climb, 1.5, 0.3, 4000), 0.85)

# --- Helios -------------------------------------------------------------------------------------------------------
# twelve mirrors unfolding: servos whining, joints locking one after another, a metal groan under it
d = 2.6
unf = bp(noise(d), 80, 400) * env(d, 0.4, 2.0, 1.4) * 0.6
for i in range(12):
    tt = 0.15 + i * 0.16
    whine = zap(0.22, 900 + i * 40, 1800 + i * 60, 0.25)
    unf = mixl(unf, at(whine, tt), at(tick_click(0.05, 2000 + 120 * i) * 0.7, tt + 0.2))
unf = mixl(unf, ring(d, [92, 141, 233], [1.2, 1.6, 2.2], [0.35, 0.25, 0.15]))
save('helios_unfold', reverb(unf, 2.2, 0.4, 3500), 0.8)

# sunlight gathering: a choir of partials climbing and thickening to a white hum
d = 1.8
t = t_(d)
ch = shimmer(d, 220, 880, 8, 0.6) * np.minimum(1, t / 1.2) + sum(np.sin(2 * np.pi * f * t * (1 + 0.6 * t / d)) for f in (110, 165, 220)) * 0.15 * t / d
ch = ch * env(d, 1.2, 0.6, 2)
save('helios_charge', reverb(ch, 2.4, 0.45, 6000), 0.85)

# the lance fires: a crack, a falling scream, the tail of a thunderclap
d = 2.4
fire = mixl(crack(0.06) * 1.4, zap(1.0, 2600, 180, 0.8), hp(noise(0.9), 1200) * env(0.9, 0.002, 0.9, 4) * 0.8, sub(1.6, 140, 38, 2.2, 2.4) * 0.9)
save('helios_fire', reverb(pad(fire, d), 2.2, 0.4, 5000))

# the lance itself: a roar of burning air, four seconds that loop
d = 4.0
save('helios_beam', roar(d, 80, 1600, 0.25), 0.8)

# where it lands: silence broken by a white hiss and a bass you feel
d = 3.6
land = mixl(hp(noise(0.6), 2000) * env(0.6, 0.001, 0.6, 3) * 0.7, sub(2.6, 90, 26, 1.6, 3.2), crack(0.12) * 0.9,
            at(bp(noise(2.4), 200, 2000) * env(2.4, 0.05, 2.3, 2) * 0.6, 0.1))
save('helios_impact', reverb(pad(land, d), 3.0, 0.45, 2500))

# --- Antipode -----------------------------------------------------------------------------------------------------
# the sky cracking: glass under stress, ice splitting, a long ring
d = 3.0
ck = np.zeros(int(SR * d))
for k in range(26):
    tt = (k / 26) ** 1.6 * 2.2
    ck = mixl(ck, at(crack(0.05 + rng.random() * 0.05) * (0.3 + 0.7 * k / 26), tt))
ck = mixl(ck, at(ring(1.8, [1420, 2210, 3170, 4380], [2.0, 2.6, 3.2, 4.0], [0.4, 0.3, 0.2, 0.15]), 2.2))
save('antipode_crack', reverb(ck, 2.6, 0.45, 7000), 0.8)

# the breach opening: a reversed swell into a deep tearing tone
d = 3.4
sw = reverse(reverb(mixl(sub(1.4, 70, 40, 2.0), crack(0.1)), 1.2, 0.6, 3000))[:int(SR * 1.4)] * 0.9
tear = sweep_bp(noise(2.0), 2000, 120, 2.2) * env(2.0, 0.02, 1.9, 2.0) * 0.8
br = mixl(sw, at(tear, 1.35), at(shimmer(1.8, 300, 180, 6, 0.4) * env(1.8, 0.1, 1.6, 2), 1.4))
save('antipode_breach', reverb(br, 3.0, 0.45, 2500), 0.85)

# another world approaching: sub-bass rising, a dissonant drone thickening, wind
d = 6.0
t = t_(d)
k = t / d
drone = sum(np.sin(2 * np.pi * f * (1 + 0.04 * k) * t + rng.uniform(0, 6)) for f in (41.2, 43.6, 61.7, 87.3, 92.5)) * 0.25
ap = (drone + lp(brown(d), 120) * 0.7 + sweep_bp(noise(d), 300, 1400, 1.2) * 0.3) * (0.2 + 0.8 * k ** 1.5) * env(d, 2.5, 3.5, 0.6)
save('antipode_approach', reverb(ap, 3.0, 0.35, 1800), 0.85)

# the touch: an inverted silence, then the heaviest hit, a glassy shatter and a rolling tail
d = 5.0
touch = mixl(reverse(hp(noise(0.5), 3000) * env(0.5, 0.001, 0.5, 6)) * 0.4, at(sub(3.6, 70, 22, 1.2, 3.6), 0.48),
             at(crack(0.2) * 1.2, 0.48), at(ring(2.4, [880, 1311, 1987, 2903], [1.4, 1.9, 2.6, 3.4], [0.35, 0.3, 0.2, 0.15]), 0.5),
             at(lp(brown(4.0), 260) * env(4.0, 0.05, 3.9, 1.4) * 0.9, 0.5))
save('antipode_contact', reverb(touch, 3.5, 0.45, 2500))

# the other world's hum after: a low chord that will not resolve, a crackle of settling
d = 5.0
t = t_(d)
hum = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f in (55, 58.3, 82.4, 116.5)) * 0.25 * env(d, 0.8, 4.2, 1.2)
for k2 in range(16):
    hum = mixl(hum, at(crack(0.03) * 0.25, rng.random() * 4.5))
save('antipode_after', reverb(hum, 3.0, 0.4, 2000), 0.7)

# a boom heard from far away: low, late, rolling
d = 4.0
boom = mixl(sub(3.0, 60, 24, 1.3, 2.5), lp(brown(3.5), 180) * env(3.5, 0.03, 3.4, 1.6) * 0.8)
save('boom', reverb(boom, 3.5, 0.5, 1200))
print('sounds ok')
