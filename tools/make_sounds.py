"""Synthesizes every Made in Heaven sound from scratch (no samples): clocks, fists, knives, time, the end of the universe.
Writes mono 44.1 kHz Ogg Vorbis into assets/madeinheaven/sounds via ffmpeg."""
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


# ------------------------------------------------------------------ the stand
# summon: time catching its breath - a rising rush, a soft thump, two clock bells a fifth apart
d = 1.6
rush = sweep_bp(noise(d), 300, 6500, 2.2) * env(d, 0.55, 1.0, 4)
save('summon', reverb(mixl(rush * 0.7, at(thump(0.6, 130, 50, 7) * 0.9, 0.5), at(bell(1.6, 880, 1.5, 0.5), 0.52),
                           at(bell(1.4, 1318.5, 1.8, 0.35), 0.6)), 1.6, 0.3, 5000))

for i, p in enumerate([1.0, 1.12, 0.9], 1):
    body = thump(0.32, 190 * p, 60 * p, 11)
    save(f'punch_{i}', reverb(mixl(crack(0.035) * 1.1, body, whoosh(0.12, 800 * p, 3500 * p, 2) * 0.5), 0.5, 0.12))

for i, p in enumerate([1.0, 1.2, 0.85, 1.35], 1):
    save(f'jab_{i}', mixl(crack(0.025) * 1.2, thump(0.16, 230 * p, 90 * p, 20) * 0.8))

d = 1.8
save('finisher', reverb(mixl(whoosh(0.3, 300, 3000, 2) * 0.6, at(crack(0.07) * 1.4, 0.22), at(thump(1.4, 140, 32, 3.2) * 1.3, 0.22),
                            at(lp(noise(1.2), 1800) * env(1.2, 0.003, 1.2, 4) * 0.7, 0.22)), 1.8, 0.3, 4000))

# knives: a flick, then three blades singing past
d = 0.7
flick = whoosh(0.18, 1500, 7000, 3) * 0.7
sing = sweep_bp(noise(0.5), 7000, 2500, 8) * env(0.5, 0.03, 0.45, 5)
save('knife_throw', reverb(mixl(flick, at(sing, 0.05), at(sing * 0.8, 0.09), at(sing * 0.6, 0.13),
                                at(ring(0.5, [4200, 6100, 8800], [9, 12, 16], [0.4, 0.25, 0.15]), 0.02)), 0.6, 0.15))
save('knife_hit', reverb(mixl(crack(0.02), thump(0.2, 320, 140, 22) * 0.7, ring(0.4, [3600, 5300], [14, 20], [0.3, 0.2])), 0.5, 0.12))

# the speed slice: silence torn by a hiss, a ring that hangs
d = 1.6
hiss = hp(noise(0.35), 4500) * env(0.35, 0.002, 0.33, 7) * 0.8
save('slice', reverb(mixl(crack(0.02) * 1.3, hiss, whoosh(0.2, 2000, 9000, 4) * 0.7,
                         ring(1.6, [2950, 4480, 6710, 9020], [1.8, 2.4, 3.3, 4.6], [0.7, 0.45, 0.28, 0.15])), 1.6, 0.3))

# vanish: the air closes where he was
d = 0.5
zip_ = sweep_bp(noise(d), 9000, 600, 1.5) * env(d, 0.01, 0.48, 6)
save('vanish', reverb(mixl(crack(0.03) * 0.9, zip_, lp(noise(0.3), 200) * env(0.3, 0.005, 0.3, 8) * 0.8), 0.6, 0.18))

# a clock's tick, and its heavier tock
save('tick', tick_click(0.06, 3300), peak=0.6)
save('tock', mixl(tick_click(0.09, 2100), thump(0.1, 300, 160, 30) * 0.3), peak=0.6)

# double acceleration: a gear winding to a scream
d = 1.6
t = t_(d)
rate = 8 + 70 * (t / d) ** 2
clicks = np.zeros_like(t)
ph = np.cumsum(rate) / SR
idx = np.where(np.diff(np.floor(ph)) > 0)[0]
for i in idx:
    seg = tick_click(0.03, 2800 + 2000 * t[i] / d)
    clicks[i:i + len(seg)] += seg[: max(0, len(clicks) - i)] * 0.6
whine = sweep_bp(noise(d), 400, 5000, 6) * np.minimum(1, t / d) ** 2 * 0.6
save('double_accel', reverb(mixl(clicks, whine, at(thump(0.8, 110, 40, 5), 1.35), at(bell(1.5, 1046.5, 1.6, 0.5), 1.36)), 1.6, 0.3))

# heaven's judgement: the last blow
d = 3.0
save('judgement', reverb(mixl(crack(0.1) * 1.5, thump(d, 100, 24, 1.6) * 1.4, lp(noise(d), 2200) * env(d, 0.004, d, 2.4),
                             bp(noise(d), 200, 1400) * env(d, 0.2, d, 2.0) * 0.5, at(bell(2.6, 523.3, 1.2, 0.6), 0.05)), 2.6, 0.35, 3500))

# ------------------------------------------------------------------ time
# time acceleration: a deep swell, a bell, and the world starting to run
d = 4.0
t = t_(d)
chord = np.zeros_like(t)
for f in (65.4, 98.0, 130.8, 196.0):
    for det in (-0.8, 0.0, 0.9):
        chord += np.sin(2 * np.pi * (f + det) * t * (1 + 0.25 * (t / d) ** 2) + rng.uniform(0, 6)) / (1 + f / 90)
swell = np.minimum(1, t / 2.2) * np.exp(-np.maximum(0, t - 3.2) * 3)
save('time_accel', reverb(mixl(np.tanh(chord * 0.8) * swell, at(bell(3.0, 659.3, 0.9, 0.8), 0.2), at(bell(3.0, 987.8, 1.1, 0.6), 0.25),
                             sweep_bp(noise(d), 200, 4000, 3) * swell * 0.4), 3.0, 0.4, 4000))

# the universe ending: a cosmic swell that keeps rising
d = 7.0
t = t_(d)
f = 40 + 260 * (t / d) ** 2.4
saw = np.zeros_like(t)
for k, det in enumerate((0.0, 0.4, -0.5, 1.1)):
    saw += signal.sawtooth(2 * np.pi * np.cumsum(f * (1 + det * 0.01)) / SR + k)
pad_ = lp(saw, 1500) * 0.4
air = sweep_bp(noise(d), 150, 6000, 2) * 0.45
choir = np.zeros_like(t)
for fr in (220, 277.2, 329.6, 440):
    choir += np.sin(2 * np.pi * fr * t * (1 + 0.5 * (t / d) ** 3)) * 0.18
rise = np.minimum(1, t / 5.5) ** 1.6
save('reset_rise', reverb((pad_ + air + choir) * rise, 3.5, 0.45, 5000))

# the singularity: everything at once
d = 4.0
t = t_(d)
rev_swell = (np.exp(-np.maximum(0, 0.6 - t) * 0) * 0)  # placeholder silence before the hit
hit = mixl(crack(0.12) * 1.6, thump(d, 90, 18, 1.2) * 1.6, lp(noise(d), 3000) * env(d, 0.002, d, 2.0) * 1.2,
           hp(noise(d), 6000) * env(d, 0.01, 3.0, 2.2) * 0.25, bell(3.5, 261.6, 0.8, 0.6))
save('singularity', reverb(hit, 3.5, 0.45, 3000))

# the new universe: a bright chord ringing out of white
d = 5.0
t = t_(d)
chord = mixl(bell(d, 523.3, 0.6, 0.6), bell(d, 659.3, 0.65, 0.5), bell(d, 784.0, 0.7, 0.45), bell(d, 1046.5, 0.8, 0.35))
shine = hp(noise(d), 7000) * env(d, 0.4, 4.0, 2) * 0.12
save('new_universe', reverb(mixl(chord, shine), 4.0, 0.5, 6000))

# menacing: the low rumble under ゴゴゴ
d = 2.5
t = t_(d)
rum = lp(noise(d), 120) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * t)) * 2.0
save('menacing', reverb(rum * env(d, 0.4, 2.0, 2.0) + np.sin(2 * np.pi * 41 * t) * env(d, 0.5, 2.0, 2.2) * 0.5, 1.5, 0.3, 1500))

# ================================================================== round two: richer, heavier, bass on speed
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


# the bass under every speed move
for i, (f0, f1, d) in enumerate([(78, 31, 1.6), (90, 36, 1.3), (66, 28, 1.9)], 1):
    hitc = crack(0.02) * 0.6
    save(f'bass_{i}', mixl(sub(d, f0, f1, 2.4, 2.2) * 1.3, hitc, lp(noise(0.25), 300) * env(0.25, 0.002, 0.25, 9) * 0.6), peak=0.98)

# a dash of light: air torn by something brighter than sound, a glassy tail
for i, p_ in enumerate([1.0, 1.18, 0.88], 1):
    d = 0.7
    zip_ = sweep_bp(noise(0.32), 1500 * p_, 11000, 3) * env(0.32, 0.01, 0.3, 5)
    glass = shimmer(0.6, 2400 * p_, 5200 * p_, 5, 0.25) * env(0.6, 0.005, 0.55, 5)
    save(f'dash_{i}', reverb(mixl(crack(0.015) * 0.8, zip_, at(glass, 0.03), sub(0.45, 120, 50, 7) * 0.5), 0.8, 0.25, 7000))

# the Stand appears: a breath in, crystal rising, a bass bloom and a clock chime
d = 2.0
t = t_(d)
inhale = sweep_bp(noise(0.8), 300, 4000, 2) * env(0.8, 0.7, 0.1, 3)
save('stand_summon', reverb(mixl(inhale * 0.7, at(shimmer(1.3, 600, 2400, 7, 0.35) * env(1.3, 0.05, 1.2, 2.2), 0.6),
                                 at(sub(1.3, 85, 38, 2.6, 2.0), 0.62), at(bell(1.4, 1318.5, 1.6, 0.4), 0.66), at(crack(0.02), 0.62)), 2.0, 0.35, 6000))
save('stand_dismiss', reverb(mixl(reverse(shimmer(0.7, 900, 2800, 6, 0.3) * env(0.7, 0.02, 0.68, 3)),
                                  at(sweep_bp(noise(0.4), 4000, 400, 2) * env(0.4, 0.01, 0.39, 4) * 0.6, 0.55)), 1.2, 0.3))

# fists: a hard transient, a body, a sub
for i, p_ in enumerate([1.0, 1.12, 0.9], 1):
    save(f'fist_{i}', reverb(mixl(crack(0.03) * 1.3, thump(0.3, 210 * p_, 70 * p_, 12), sub(0.35, 95, 45, 9) * 0.7,
                                   whoosh(0.1, 900 * p_, 4000, 2) * 0.4), 0.5, 0.12))
# the barrage's bed: a storm of fists for 2.4 s
d = 2.4
bed = np.zeros(int(SR * d))
k = 0.0
while k < d - 0.12:
    j = mixl(crack(0.02) * 0.9, thump(0.12, 240 * rng.uniform(0.85, 1.25), 90, 22) * 0.8)
    pos = int(k * SR)
    bed[pos:pos + len(j)] += j[: max(0, len(bed) - pos)] * rng.uniform(0.6, 1.0)
    k += rng.uniform(0.035, 0.06)
air = sweep_bp(noise(d), 800, 2600, 1.5) * 0.25
save('barrage_rush', reverb(mixl(bed, air) * env(d, 0.05, 2.3, 1.2), 0.8, 0.2))

# knives: a blade hung in still time, a volley loosed
save('knife_hang', reverb(mixl(ring(1.2, [3700, 5480, 8020], [3.0, 4.0, 5.5], [0.5, 0.32, 0.2]), crack(0.01) * 0.4,
                               shimmer(0.9, 3000, 3600, 4, 0.12) * env(0.9, 0.02, 0.85, 3)), 1.2, 0.3, 8000), peak=0.7)
d = 1.0
vol = np.zeros(int(SR * d))
for i in range(12):
    w = whoosh(0.22, 2500, 9000, 3) * 0.7
    pos = int(i * 0.045 * SR)
    vol[pos:pos + len(w)] += w[: max(0, len(vol) - pos)]
save('knife_volley', reverb(mixl(vol, sub(0.9, 90, 40, 3.5) * 0.8), 0.9, 0.2))
save('knife_flesh', reverb(mixl(crack(0.02), thump(0.18, 300, 120, 22) * 0.8, bp(noise(0.12), 900, 3000) * env(0.12, 0.002, 0.12, 10) * 0.5), 0.4, 0.1))

# Heaven's Judgement: each dash a cut, the last blow everything at once
for i, p_ in enumerate([1.0, 1.1, 0.92, 1.2], 1):
    cut = mixl(crack(0.02) * 1.0, sweep_bp(noise(0.3), 1800 * p_, 10000, 4) * env(0.3, 0.004, 0.28, 6),
               ring(0.9, [3100 * p_, 4700 * p_, 7020 * p_], [3.5, 4.5, 6.0], [0.4, 0.25, 0.15]), sub(0.4, 110, 48, 8) * 0.6)
    save(f'judge_cut_{i}', reverb(cut, 0.9, 0.25, 7000))

# infinite speed: the world pulled out of time (a reversed swell into a bass drop) and snapped back
d = 2.4
t = t_(d)
swell = reverse(mixl(hp(noise(0.3), 3000) * env(0.3, 0.005, 0.29, 3) * 0.6, ring(0.3, [880, 1320, 1980], [6, 7, 8], [0.4, 0.3, 0.2])))
drone = (np.sin(2 * np.pi * 55 * t) + 0.5 * np.sin(2 * np.pi * 82.5 * t * (1 - 0.1 * t / d))) * env(d, 0.3, 2.0, 1.4) * 0.5
save('infinite_start', reverb(mixl(swell, at(sub(1.6, 80, 30, 2.0, 2.4) * 1.3, 0.28), at(crack(0.03), 0.28), at(drone, 0.3)), 2.0, 0.35, 5000))
d = 3.0
save('detonate', reverb(mixl(crack(0.1) * 1.5, sub(d, 70, 25, 1.5, 2.6) * 1.4, lp(noise(d), 3500) * env(d, 0.003, d, 2.6),
                            hp(noise(1.5), 5000) * env(1.5, 0.01, 1.4, 3) * 0.3, at(shimmer(1.2, 3000, 1200, 6, 0.25) * env(1.2, 0.02, 1.1, 3), 0.05)),
                       2.6, 0.4, 4000))

# lightspeed rush: a pressure wave you can feel
d = 1.6
save('rush', reverb(mixl(crack(0.03) * 1.1, sweep_bp(noise(0.9), 500, 9000, 1.6) * env(0.9, 0.04, 0.85, 4), sub(1.4, 85, 33, 2.4, 2.3) * 1.2,
                         at(shimmer(0.7, 2000, 4500, 5, 0.2) * env(0.7, 0.01, 0.65, 4), 0.05)), 1.4, 0.3, 6000))

# a crisp hit of light
for i, p_ in enumerate([1.0, 1.25], 1):
    save(f'light_hit_{i}', reverb(mixl(crack(0.02) * 1.0, ring(0.5, [2600 * p_, 3900 * p_, 5800 * p_], [8, 10, 13], [0.5, 0.3, 0.2]),
                                       thump(0.2, 200 * p_, 90, 18) * 0.6), 0.6, 0.2, 8000))

# the universe: reality cracking, the galaxy's hum, the plunge into the core
save('crack', reverb(mixl(crack(0.06) * 1.6, *[at(ring(0.9, [rng.uniform(2000, 9000) for _ in range(4)], [6, 8, 10, 12], [0.3, 0.2, 0.15, 0.1]),
                                                 rng.uniform(0, 0.25)) for _ in range(9)], sub(1.2, 60, 28, 2.5) * 0.9), 1.8, 0.4, 7000))
d = 7.0
t = t_(d)
hum = np.zeros_like(t)
for f, a in ((41.2, 1.0), (61.7, 0.6), (82.4, 0.45), (123.5, 0.25)):
    hum += a * np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 0.11 * t))
choir = shimmer(d, 440, 660, 6, 0.12)
save('galaxy', reverb((np.tanh(hum * 0.6) * 0.7 + choir + bp(noise(d), 200, 1200) * 0.12) * env(d, 1.5, 5.0, 0.6), 3.5, 0.5, 4000))
d = 2.2
save('plunge', reverb(mixl(sweep_bp(noise(d), 150, 9000, 1.4) * np.minimum(1, t_(d) / d) ** 2 * 1.2, shimmer(d, 300, 3200, 6, 0.25) * np.minimum(1, t_(d) / d) ** 2), 1.5, 0.3))

# double acceleration and time acceleration get their bass too
save('accel_bass', mixl(sub(2.0, 72, 28, 1.6, 2.6) * 1.3, crack(0.04), at(shimmer(1.0, 1800, 600, 6, 0.25) * env(1.0, 0.01, 0.9, 3), 0.02)), peak=0.98)
print('sounds written:', len(os.listdir(OUT)))
