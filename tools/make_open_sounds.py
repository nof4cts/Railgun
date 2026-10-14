"""Open's sounds, made for it: the film's soundtrack (scored to the shots of fire_arrow_open.blend) and the four sounds of
the move as it happens in the film. Synthesised here (noise, oscillators, filters) and mixed with Sukuna's own Fuga
sounds from the mod. Usage: python make_open_sounds.py <dir with ignite/draw/release/impact .ogg> <out dir>"""
import numpy as np, subprocess, sys, os, wave
SR = 48000
rng = np.random.default_rng(1337)
src, out = sys.argv[1], sys.argv[2]

def load(name):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', os.path.join(src, name + '.ogg'), '-f', 's16le', '-ac', '2', '-ar', str(SR), '-'],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.int16).reshape(-1, 2).astype(np.float32) / 32768.0

def lowpass(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.empty_like(x); s = 0.0
    for i in range(len(x)):
        s = (1 - a) * x[i] + a * s; y[i] = s
    return y

def onepole(x, fc):
    # vectorised-ish one pole via scipy-free recursion in chunks (fast enough for these lengths)
    return lowpass(x, fc)

def band(x, lo, hi):
    return onepole(x, hi) - onepole(x, lo)

def env(n, a, r, shape=1.0):
    t = np.arange(n) / SR; T = n / SR
    e = np.minimum(1, t / max(a, 1e-4)) * np.clip((T - t) / max(r, 1e-4), 0, 1)
    return e ** shape

def noise(n):
    return rng.standard_normal(n).astype(np.float32)

def roar(dur, lo=90, hi=1400, grit=0.35):
    n = int(dur * SR)
    b = band(noise(n), lo, hi)
    # flame turbulence: slow random amplitude flicker
    flick = onepole(np.abs(noise(n)), 6.0); flick /= flick.max() + 1e-9
    c = crackle(dur, 140) * grit
    return (b / (np.abs(b).max() + 1e-9)) * (0.55 + 0.45 * flick) + c

def crackle(dur, rate):
    n = int(dur * SR); x = np.zeros(n, np.float32)
    k = rng.poisson(rate * dur)
    for p in rng.integers(0, max(1, n - 400), k):
        L = int(rng.integers(40, 300)); x[p:p + L] += noise(L) * np.exp(-np.arange(L) / (L / 5)) * rng.uniform(0.2, 1.0)
    return band(x, 900, 7000) * 2.0

def whoosh(dur, up=True):
    n = int(dur * SR); t = np.linspace(0, 1, n)
    x = noise(n); y = np.zeros(n, np.float32)
    seg = 2400
    for i in range(0, n, seg):
        k = t[i] if up else 1 - t[i]
        f = 300 + 3500 * k * k
        y[i:i + seg] = band(x[i:i + seg], f * 0.5, f)
    return y / (np.abs(y).max() + 1e-9) * np.sin(np.pi * t) ** 1.5

def boom(dur=2.5, f0=70, f1=28):
    n = int(dur * SR); t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t * 3.0)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.6)
    hit = onepole(noise(n), 900) * np.exp(-t * 7.0) * 3.0
    return (s + hit) / 1.2

def drone(dur, f=41.0, gain=1.0):
    n = int(dur * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 1.5 * t + 1.3) + 0.25 * np.sin(2 * np.pi * f * 2.01 * t)
    s *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.13 * t)
    air = onepole(noise(n), 260) * 0.8
    return (s * 0.5 + air) * gain * env(n, 1.5, 1.5)

def riser(dur, f0=80, f1=420):
    n = int(dur * SR); t = np.arange(n) / SR; k = t / dur
    f = f0 * (f1 / f0) ** (k ** 1.6)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.4 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR)
    w = whoosh(dur, True)
    return (s * 0.5 + w * 0.6) * (k ** 1.4)

def sting(dur=1.6, f=880):
    n = int(dur * SR); t = np.arange(n) / SR
    return (np.sin(2 * np.pi * f * t) * 0.5 + np.sin(2 * np.pi * f * 1.498 * t) * 0.3 + np.sin(2 * np.pi * f * 2.0 * t) * 0.2) * np.exp(-t * 2.2)

def hum(dur, f=110):
    n = int(dur * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t + 3 * np.sin(2 * np.pi * 5.5 * t)) * 0.6 + np.sin(2 * np.pi * f * 2 * t) * 0.2
    return s * env(n, 0.4, 0.4) * (0.6 + 0.4 * t / dur)

class Mix:
    def __init__(self, dur):
        self.b = np.zeros((int(dur * SR), 2), np.float32)
    def add(self, t, sig, gain=1.0, pan=0.0):
        if sig.ndim == 1:
            l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
            sig = np.stack([sig * l * 1.414, sig * r * 1.414], 1)
        i = int(t * SR); j = min(len(self.b), i + len(sig))
        if i < len(self.b):
            self.b[i:j] += sig[:j - i] * gain
    def write(self, path, peak=0.9):
        x = self.b / max(1e-6, np.abs(self.b).max()) * peak
        # soft clip for weight
        x = np.tanh(x * 2.2) / np.tanh(2.2)
        wav = path + '.wav'
        with wave.open(wav, 'wb') as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes((x * 32767).astype(np.int16).tobytes())
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', path + '.ogg'], check=True)
        os.remove(wav)

ig, dr, rl, im = load('ignite'), load('draw'), load('release'), load('impact')
F = lambda frame: (frame - 1) / 60.0   # the film's 60 fps frames to seconds

# ------------------------------------------------------------------------------------------------ the film's score
film = Mix(49.6)
film.add(0.0, drone(23.0, 41.0, 0.55))                      # S01-S10: the arena's dread
film.add(0.0, crackle(23.0, 25) * 0.25)
film.add(F(137), whoosh(1.2, False), 0.35)                  # S02 cut to black
film.add(F(217), ig, 0.9)                                   # S03 ignite
film.add(F(217), crackle(1.6, 260), 0.5)
film.add(F(315), boom(2.2, 55, 25), 0.7)                    # S04 the golem
film.add(F(433) - 0.25, whoosh(0.6, True), 0.5)             # S05 the arm catches fire
film.add(F(433), roar(F(523) - F(433) + 0.4), 0.75)
film.add(F(529), roar(F(727) - F(529) + 0.6, 70, 1800, 0.5), 0.85, -0.2)  # S06 "fire aspect": the spiral
for k, f in enumerate((625, 660, 695)):                     # its slashes
    film.add(F(f), whoosh(0.45, k % 2 == 0), 0.6, 0.5 if k % 2 else -0.5)
film.add(F(729), boom(2.5, 50, 24), 0.55)                   # S07 golem close-up
film.add(F(729), drone(5.0, 36.0, 0.5))
film.add(F(977), sting(2.0, 660), 0.45)                     # S08 the hero's eyes
film.add(F(1063), boom(2.8, 60, 22), 0.8)                   # S09 the golem pushes
film.add(F(1063), roar(2.0, 50, 500, 0.1), 0.4)
film.add(F(1189), drone(3.2, 46.0, 0.6))                    # S10 the towers
film.add(F(1189), onepole(noise(int(3.2 * SR)), 500) * 0.4 * env(int(3.2 * SR), 0.8, 1.0))
film.add(F(1441) - 0.4, riser(0.6, 120, 500), 0.4)          # S12 the fists
film.add(F(1441), roar(F(1489) - F(1441) + 0.2, 120, 2000), 0.7)
film.add(F(1489), boom(1.6, 90, 35), 0.85)
film.add(F(1489), roar(1.2, 150, 3000, 0.6), 0.6)
film.add(F(1555), hum(F(1777) - F(1555) + 1.5, 98), 0.4, 0.3)  # S13 the golem's orb charging
film.add(F(1555), riser(F(1777) - F(1555), 60, 220), 0.35, 0.3)
film.add(F(1675), dr, 0.95)                                  # S14 the draw
for k in range(6):
    film.add(F(1675) + k * 0.12, whoosh(0.35, True), 0.3, (-1) ** k * 0.6)
film.add(F(1675), crackle(1.8, 300), 0.4)
film.add(F(1891), sting(1.2, 1320), 0.3)                     # S16 the cube is summoned
film.add(F(1927), whoosh(1.4, True), 0.4)
film.add(F(2043), drone(F(2323) - F(2043), 33.0, 0.8)[:int((F(2323) - F(2043)) * SR)])   # S17 the standoff (cut dead at the shot)
film.add(F(2043), riser(F(2323) - F(2043), 70, 600), 0.65)
film.add(F(2043), roar(F(2323) - F(2043), 80, 1200, 0.3), 0.35)
# the arrow leaves the bow in silence: everything stops dead the moment he fires
film.b[int(F(2310) * SR):int(F(2365) * SR)] = 0.0
# S22: the shot makes no sound at all. The standoff's rise cuts to dead silence, the golem is simply gone; only
# the smallest crackle of what's left creeps back in.
film.add(F(2365) + 1.5, crackle(F(2905) - F(2365) - 1.5, 60) * env(int((F(2905) - F(2365) - 1.5) * SR), 2.5, 3.0), 0.18)
film.add(F(2685), drone(F(2966) - F(2685) + 0.6, 41.0, 0.45))  # S23 the hero, after
film.write(os.path.join(out, 'film'))

# ------------------------------------------------------------------------------------------------ the move, no film
a = Mix(1.9); a.add(0.0, whoosh(0.5, True), 0.5); a.add(0.1, roar(1.7), 0.85); a.add(0.0, ig, 0.6); a.write(os.path.join(out, 'armfire'))
b = Mix(1.4); b.add(0.0, roar(0.5, 120, 2000), 0.6); b.add(0.25, boom(1.1, 90, 35), 0.9); b.add(0.25, roar(1.0, 150, 3000, 0.6), 0.6); b.write(os.path.join(out, 'fists'))
c = Mix(1.8); c.add(0.0, riser(1.6, 90, 520), 0.6); c.add(0.0, crackle(1.7, 280), 0.45)
for k in range(5):
    c.add(k * 0.14, whoosh(0.35, True), 0.35, (-1) ** k * 0.6)
c.write(os.path.join(out, 'form'))
d = Mix(7.0); d.add(0.0, boom(4.5, 75, 20), 1.0); d.add(0.0, roar(6.8, 60, 2500, 0.6) * env(int(6.8 * SR), 0.05, 4.5, 1.5), 0.9)
d.add(0.8, crackle(6.0, 160) * env(int(6.0 * SR), 0.5, 3.0), 0.5); d.write(os.path.join(out, 'inferno'))
print('ok')
