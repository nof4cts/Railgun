"""
Synthesises every Modulo Yuji sound from scratch (run from the project root; needs numpy, scipy and ffmpeg):

    python3 tools/gen_sounds.py

Nothing is sampled from anything. Black Flash is a torn-air crack, crackling black sparks and a sub drop; blood is
filtered noise and bubbling sine blips; cuts are noise transients with inharmonic rings; the cataclysms are layered
explosions and rumbles. All mono, so Minecraft places them in the world. Also writes assets/yuji/sounds.json.
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy.signal import lfilter

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "yuji")
OUT = os.path.join(ROOT, "sounds")
rng = np.random.default_rng(1968)


def T(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def lp1(x, fc):
    a = 1 - np.exp(-2 * np.pi * min(max(fc, 1), SR / 2.2) / SR)
    return lfilter([a], [1, a - 1], x)


def lp(x, fc, order=2):
    """Low-pass; fc may be an array (time-varying), processed in blocks."""
    if np.ndim(fc) == 0:
        for _ in range(order):
            x = lp1(x, fc)
        return x
    fc = np.asarray(fc, float)
    y = np.zeros_like(x)
    zs = [np.zeros(1) for _ in range(order)]
    for i in range(0, len(x), 256):
        blk = x[i:i + 256]
        c = float(np.mean(fc[i:i + 256]))
        a = 1 - np.exp(-2 * np.pi * min(max(c, 1), SR / 2.2) / SR)
        for k in range(order):
            blk, zs[k] = lfilter([a], [1, a - 1], blk, zi=zs[k])
        y[i:i + 256] = blk
    return y


def hp(x, fc):
    return x - lp(x, fc, 1)


def bp(x, lo, hi):
    return hp(lp(x, hi), lo)


def sin(freq):
    return np.sin(2 * np.pi * np.cumsum(np.asarray(freq, float)) / SR)


def saw(freq):
    ph = np.cumsum(np.asarray(freq, float)) / SR
    return 2 * (ph % 1.0) - 1


def place(dst, src, at, g=1.0):
    i = int(at * SR)
    n = min(len(src), len(dst) - i)
    if n > 0 and i >= 0:
        dst[i:i + n] += src[:n] * g


def drop(d, f0, f1, decay, drive=2.0):
    t = T(d)
    f = f1 + (f0 - f1) * np.exp(-t / (decay * 0.4))
    return np.tanh(drive * sin(f)) * np.exp(-t / decay)


def crack(d=0.25, bright=1.0):
    """Torn air: a broadband clap whose top end dies first."""
    t = T(d)
    x = noise(d) * np.exp(-t / 0.03)
    return np.tanh(3 * (hp(x, 600) * bright + lp(x, 900) * 0.6))


def sparks(d, rate, decay):
    """Black lightning: a crackle of tiny, bright clicks thinning out."""
    t = T(d)
    x = np.zeros_like(t)
    n = int(rate * d)
    for _ in range(n):
        at = rng.exponential(decay)
        if at >= d:
            continue
        click = hp(noise(0.004), 2500) * np.exp(-T(0.004) / 0.0012)
        place(x, click, at, rng.uniform(0.3, 1.0))
    return x


def slice_(d=0.5, pitch=1.0):
    t = T(d)
    x = np.zeros_like(t)
    place(x, hp(noise(0.012), 4000) * 2.0, 0)
    ring = sum(sin(np.full_like(t, f * pitch)) * np.exp(-t / dec) * g
               for f, dec, g in ((3600, 0.15, 1.0), (5420, 0.10, 0.7), (7100, 0.06, 0.5), (2300, 0.2, 0.35)))
    air = hp(lp(noise(d), 9000), 1500) * np.exp(-t / 0.05)
    return x + ring * 0.3 + air


def boom(d, size=1.0):
    """An explosion: the clap, a sub drop, a roaring low body and falling debris."""
    x = np.zeros(int(SR * d))
    t = T(d)
    place(x, crack(0.3, 1.2), 0, 1.0)
    place(x, drop(min(d, 1.5 + size), 110, 26, 0.35 + 0.35 * size, 3.0), 0, 1.4)
    body = lp(noise(d), 300 + 1800 * np.exp(-t / (0.2 * size))) * np.exp(-t / (0.5 + 0.6 * size))
    x += np.tanh(body * 3) * 0.9
    deb = sparks(d, 140 * size, 0.6 * size) * 0.25
    x += lp(deb, 5000)
    return x


def fades(x, i_ms=2, o_ms=40):
    x = x.copy()
    ni, no = int(SR * i_ms / 1000), int(SR * o_ms / 1000)
    if ni:
        x[:ni] *= np.linspace(0, 1, ni)
    if no:
        x[-no:] *= np.linspace(1, 0, no)
    return x


NAMES = []


def write(name, x, peak=0.95):
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(OUT, exist_ok=True)
    x = np.nan_to_num(x)
    x = fades(x / (np.max(np.abs(x)) or 1.0) * peak)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        wp = f.name
    with wave.open(wp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "6", path], check=True)
    os.remove(wp)
    NAMES.append(name)
    print("wrote", name, f"{len(x) / SR:.2f}s")


def black_flash(big):
    d = 3.2 if big else 1.7
    x = np.zeros(int(SR * d))
    t = T(d)
    place(x, crack(0.35, 1.5), 0, 1.3)
    place(x, sparks(1.2 if big else 0.7, 900 if big else 600, 0.18 if big else 0.1), 0.0, 0.9)
    # space tearing: a ring-modulated snarl
    tear = sin(np.full_like(t, 190)) * hp(noise(d), 300) * np.exp(-t / (0.35 if big else 0.18))
    x += np.tanh(tear * 3) * 0.5
    place(x, drop(d, 95, 24 if big else 32, 0.9 if big else 0.4, 3.5), 0, 1.6)
    if big:
        roar = lp(noise(d), 260 + 900 * np.exp(-t / 0.4)) * np.exp(-t / 1.2)
        x += np.tanh(roar * 4) * 0.8
        place(x, sparks(1.0, 300, 0.4), 0.25, 0.5)
    return x


def main():
    # 0 charge: power drawn into a fist
    d = 1.4
    t = T(d)
    sw = (t / d) ** 2
    hum = np.tanh(2 * saw(70 + 110 * sw) * 0.5) * (0.2 + 0.8 * sw)
    suck = bp(noise(d), 400, 400 + 5000 * sw) * sw ** 1.5
    write("charge", lp(hum, 1800) * 0.8 + suck * 0.7 + sparks(d, 80, 1.2) * 0.25 * sw)
    # 1, 2 Black Flash
    write("black_flash", black_flash(False))
    write("black_flash_big", black_flash(True))
    # 3 dash: a whoosh
    d = 0.45
    t = T(d)
    env = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 2
    write("dash", bp(noise(d), 300, 600 + 4500 * env) * env + drop(d, 140, 60, 0.12) * 0.3)
    # 4 blood_form: liquid gathering
    d = 0.9
    x = lp(noise(d), 500) * 0.5 * np.sin(np.pi * T(d) / d)
    for _ in range(26):
        f0 = rng.uniform(250, 900)
        b = sin(f0 * np.exp(-T(0.06) * 18) + 120) * np.exp(-T(0.06) / 0.02)
        place(x, b, rng.uniform(0, d - 0.06), rng.uniform(0.2, 0.6))
    write("blood_form", x)
    # 5 blood_fire: a pressurised jet
    d = 0.6
    t = T(d)
    x = hp(noise(d), 2500) * np.exp(-t / 0.08) * 1.2 + np.tanh(3 * hp(noise(d), 600) * np.exp(-t / 0.02))
    x += sin(3200 * np.exp(-t * 12) + 300) * np.exp(-t / 0.06) * 0.5
    write("blood_fire", x)
    # 6 blood_hit: a heavy wet impact
    d = 0.8
    t = T(d)
    x = np.tanh(3 * lp(noise(d), 700) * np.exp(-t / 0.08)) + drop(d, 120, 45, 0.18) * 0.8
    for _ in range(14):
        b = sin(rng.uniform(300, 700) * np.exp(-T(0.05) * 25) + 100) * np.exp(-T(0.05) / 0.015)
        place(x, b, rng.uniform(0.02, 0.5), 0.3)
    write("blood_hit", x)
    # 7 blood_rain: a downpour of blood bolts
    d = 4.0
    x = lp(noise(d), 1500) * 0.15
    for _ in range(140):
        at = rng.uniform(0, d - 0.3)
        s = np.tanh(2 * lp(noise(0.25), rng.uniform(500, 1500)) * np.exp(-T(0.25) / 0.04))
        place(x, s, at, rng.uniform(0.2, 0.7))
    write("blood_rain", x * np.minimum(1, (d - T(d)) / 0.6))
    # 8 dismantle, 9 cleave, 10 world_splitter
    write("dismantle", slice_(0.5, 1.0))
    d = 0.7
    x = np.zeros(int(SR * d))
    place(x, slice_(0.3, 0.8), 0)
    place(x, slice_(0.3, 1.15), 0.05, 0.8)
    place(x, drop(0.5, 160, 50, 0.12), 0.02, 0.9)
    write("cleave", x)
    d = 3.5
    t = T(d)
    x = np.zeros(int(SR * d))
    place(x, slice_(1.5, 0.6), 0, 1.4)
    x += sin(np.full_like(t, 1720)) * np.exp(-t / 0.9) * 0.35 + sin(np.full_like(t, 2590)) * np.exp(-t / 0.6) * 0.25
    x += np.tanh(2 * lp(noise(d), 160) * np.exp(-t / 1.4)) * 0.9
    place(x, drop(2.5, 70, 22, 1.0), 0.05, 1.0)
    write("world_splitter", x)
    # 11 crater, 12 eruption
    write("crater", boom(2.5, 1.0))
    write("eruption", boom(4.5, 2.0))
    # 13 domain: a deep bell over a swell, and a chord
    d = 5.0
    t = T(d)
    swell = lp(noise(d), 200 + 3000 * np.clip(t / 1.0, 0, 1) ** 2) * np.clip(t / 1.0, 0, 1) ** 3 * (t < 1.0)
    bell = sum(sin(np.full_like(t, 55 * r)) * np.exp(-np.clip(t - 1.0, 0, None) / dec) * g * (t >= 1.0)
               for r, dec, g in ((1, 3.5, 1.0), (2.76, 2.2, 0.6), (5.4, 1.4, 0.4), (8.93, 0.9, 0.25), (13.3, 0.5, 0.15)))
    chord = sum(sin(np.full_like(t, f)) for f in (110, 164.8, 220, 261.6)) * 0.12 * np.clip((t - 1.0) / 0.6, 0, 1) * np.exp(-np.clip(t - 1.6, 0, None) / 2.5)
    write("domain", swell * 0.8 + np.tanh(bell * 1.5) + chord)
    # 14 domain_hum: the domain's drone
    d = 4.0
    t = T(d)
    write("domain_hum", (sin(np.full_like(t, 55)) * 0.6 + sin(np.full_like(t, 82.4) * (1 + 0.003 * np.sin(t * 3))) * 0.3
                         + lp(noise(d), 300) * 0.3) * np.sin(np.pi * t / d) ** 0.5)
    # 15 overdrive: a rising roar
    d = 2.6
    t = T(d)
    rise = np.clip(t / 1.2, 0, 1)
    roar = np.tanh(3 * (saw(55 + 90 * rise) + saw(82 + 130 * rise) * 0.7)) * (0.3 + 0.7 * rise)
    flame = bp(noise(d), 200, 800 + 4000 * rise) * rise
    x = lp(roar, 1400) * 0.7 + flame * 0.6
    place(x, crack(0.3), 1.2, 1.2)
    place(x, drop(1.4, 120, 30, 0.6), 1.2, 1.2)
    write("overdrive", x)
    # 16 wave: a wall of force washing out
    d = 4.0
    t = T(d)
    env = np.clip(t / 0.15, 0, 1) * np.exp(-t / 1.4)
    write("wave", np.tanh(2 * lp(noise(d), 120 + 2500 * np.exp(-t / 0.5)) * env) + drop(d, 80, 20, 1.2) * 0.8)
    # 17 world_ender
    d = 9.0
    t = T(d)
    x = np.zeros(int(SR * d))
    place(x, black_flash(True), 0, 1.0)
    place(x, boom(6.0, 3.0), 0.2, 1.2)
    rum = np.tanh(3 * lp(noise(d), 90)) * np.exp(-t / 4.0)
    x += rum * 0.9
    x += sin(np.full_like(t, 3100)) * np.exp(-t / 3.0) * 0.06 * (t > 0.5)
    write("world_ender", x)
    # 18 rumble: the earth trembling
    d = 4.0
    t = T(d)
    write("rumble", np.tanh(3 * lp(noise(d), 70 + 30 * np.sin(t * 2))) * np.sin(np.pi * t / d) + lp(sparks(d, 60, 2.0), 3000) * 0.2)
    # 19 impact: a generic heavy hit
    x = drop(0.8, 130, 40, 0.2) * 1.2
    place(x, np.tanh(2 * crack(0.2) * 0.8), 0)
    write("impact", x)
    # 20 meteor: the screaming descent, then the crash
    d = 2.6
    t = T(d)
    whistle = sin(1800 * np.exp(-t * 0.9) + 200) * np.clip(t / 0.4, 0, 1) * (t < 0.55) * 0.4
    roar = bp(noise(d), 150, 3000) * np.clip(t / 0.5, 0, 1) * (t < 0.55)
    x = whistle + roar
    place(x, boom(2.0, 1.5), 0.5, 1.4)
    write("meteor", x)
    # 21 card: a soft chime for the title card
    d = 0.8
    t = T(d)
    write("card", (sin(np.full_like(t, 880)) * np.exp(-t / 0.3) + sin(np.full_like(t, 1320)) * np.exp(-t / 0.2) * 0.5) * np.clip(t / 0.005, 0, 1), 0.5)

    sounds = {n: {"subtitle": "subtitles.yuji." + n, "sounds": [{"name": "yuji:" + n, "stream": n in ("world_ender", "domain", "eruption")}]}
              for n in NAMES}
    with open(os.path.join(ROOT, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
    print("sounds.json:", len(sounds))


if __name__ == "__main__":
    main()
