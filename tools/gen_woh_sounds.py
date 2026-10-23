"""
Synthesises every sound of The World Over Heaven from scratch (run from the project root; needs numpy, scipy, ffmpeg):

    python3 tools/gen_woh_sounds.py

His sonic identity is glass, bells and clockwork: reality cracking like a pane, struck bells for every overwrite,
crystal tinkles for shards, a tolling bell and a dropping clock for time, a rising choir for ascension. Nothing is
sampled and nothing is shared with any other character. All mono so Minecraft places them in the world.
Writes assets/woh/sounds/*.ogg and assets/woh/sounds.json (keeping the user's own time_stop_voice).
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy.signal import lfilter, butter

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "woh")
OUT = os.path.join(ROOT, "sounds")
rng = np.random.default_rng(4096)


def T(d):
    return np.arange(int(SR * d)) / SR


def n(d):
    return rng.uniform(-1, 1, int(SR * d))


def band(x, lo, hi, order=2):
    b, a = butter(order, [lo / (SR / 2), min(hi, SR / 2.1) / (SR / 2)], btype="band")
    return lfilter(b, a, x)


def hpf(x, fc, order=2):
    b, a = butter(order, fc / (SR / 2), btype="high")
    return lfilter(b, a, x)


def lpf(x, fc, order=2):
    b, a = butter(order, min(fc, SR / 2.1) / (SR / 2), btype="low")
    return lfilter(b, a, x)


def env(d, a=0.005, decay=1.0):
    t = T(d)
    e = np.exp(-t / max(decay, 1e-3))
    att = np.clip(t / max(a, 1e-4), 0, 1)
    return e * att


def mix(length, *parts):
    """Place (signal, start_seconds, gain) parts into one buffer."""
    out = np.zeros(int(SR * length))
    for sig, at, g in parts:
        i = int(SR * at)
        j = min(len(out), i + len(sig))
        if j > i:
            out[i:j] += sig[: j - i] * g
    return out


# ------------------------------------------------------------------ voices
BELL_RATIOS = [0.5, 1.0, 1.19, 1.56, 2.0, 2.51, 2.66, 3.01, 4.1, 5.43]
BELL_DECAY = [2.6, 2.0, 1.6, 1.3, 1.1, 0.8, 0.75, 0.6, 0.4, 0.3]
BELL_AMP = [0.35, 1.0, 0.55, 0.45, 0.5, 0.3, 0.28, 0.22, 0.15, 0.1]


def bell(f0, d=3.0, bright=1.0, detune=0.003):
    """A struck bell: inharmonic partials, each beating slightly against a detuned twin."""
    t = T(d)
    s = np.zeros_like(t)
    for r, dc, am in zip(BELL_RATIOS, BELL_DECAY, BELL_AMP):
        f = f0 * r
        if f > SR / 2.2:
            continue
        a = am * (bright ** (r / 3))
        s += a * np.exp(-t / dc) * (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * (1 + detune) * t + 0.7))
    strike = band(n(0.02), 2000, 9000) * env(0.02, 0.0005, 0.004)
    s[: len(strike)] += strike * 0.6 * bright
    return s / (np.max(np.abs(s)) + 1e-9)


def ping(f, d=0.35, decay=0.08):
    t = T(d)
    return np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * f * 2.7 * t)) * np.exp(-t / decay)


def glass(d=0.6, density=60, lo=2500, hi=9000, decay=0.18):
    """Reality cracking like a pane: a bright noise snap and a scatter of high glassy pings."""
    out = np.zeros(int(SR * d))
    snap = hpf(n(0.03), 3000) * env(0.03, 0.0003, 0.006)
    out[: len(snap)] += snap * 1.4
    for _ in range(density):
        at = rng.exponential(d * 0.18)
        if at >= d - 0.02:
            continue
        p = ping(rng.uniform(lo, hi), min(0.4, d - at), rng.uniform(0.02, decay))
        i = int(SR * at)
        j = min(len(out), i + len(p))
        out[i:j] += p[: j - i] * rng.uniform(0.15, 0.6) * np.exp(-at * 3)
    return out / (np.max(np.abs(out)) + 1e-9)


def tinkle(d=1.0, count=40, lo=3000, hi=8000, spread=0.6):
    out = np.zeros(int(SR * d))
    for _ in range(count):
        at = rng.uniform(0, spread)
        p = ping(rng.uniform(lo, hi), 0.3, rng.uniform(0.03, 0.12))
        i = int(SR * at)
        j = min(len(out), i + len(p))
        out[i:j] += p[: j - i] * rng.uniform(0.2, 0.7)
    return out / (np.max(np.abs(out)) + 1e-9)


def sub(d, f0, f1, decay):
    t = T(d)
    f = f0 + (f1 - f0) * (1 - np.exp(-t * 6))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / decay)


def whoosh(d, lo, hi, rising=True):
    x = n(d)
    t = T(d)
    out = np.zeros_like(x)
    steps = 24
    for k in range(steps):
        a, b = int(len(x) * k / steps), int(len(x) * (k + 1) / steps)
        u = (k + 0.5) / steps
        c = lo + (hi - lo) * (u if rising else 1 - u)
        out[a:b] = band(x, c * 0.7, c * 1.4)[a:b]
    e = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 2
    return out * e / (np.max(np.abs(out)) + 1e-9)


def choir(d, root, chord=(1, 1.25, 1.5, 2.0), rise=0.0):
    """A wordless choir pad: detuned saws through vowel formants, slow swell."""
    t = T(d)
    s = np.zeros_like(t)
    for r in chord:
        for dt in (-0.004, 0.0, 0.005):
            f = root * r * (1 + dt) * (1 + rise * t / d)
            ph = 2 * np.pi * np.cumsum(f * np.ones_like(t)) / SR
            s += 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    v = band(s, 600, 1100) * 1.0 + band(s, 1000, 1500) * 0.6 + band(s, 2400, 3000) * 0.25   # an "ah"
    sw = np.clip(t / (d * 0.45), 0, 1) ** 1.5 * np.clip((d - t) / (d * 0.25), 0, 1)
    return v * sw / (np.max(np.abs(v)) + 1e-9)


def tick(f=1800, d=0.06):
    t = T(d)
    return (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.006) + hpf(n(d), 4000) * np.exp(-t / 0.003) * 0.6)


def reverse(x):
    return x[::-1]


def norm(x, peak=0.92):
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


# ------------------------------------------------------------------ the set (index order matches WCasts.SOUNDS)
def s_gather():      # power drawn in: a reversed bell swelling into the hand, with a rising shimmer
    return norm(mix(1.4, (reverse(bell(392, 1.2, 0.8)) * np.linspace(0, 1, int(SR * 1.2)) ** 2, 0.0, 0.8),
                    (whoosh(1.2, 1500, 7000), 0.1, 0.35), (tinkle(1.2, 25, 4000, 9000, 1.0), 0.2, 0.25)))


def s_overwrite():   # a single overwrite blow: pane cracks and a struck bell rings under it
    return norm(mix(1.6, (glass(0.5, 40), 0.0, 0.9), (bell(523, 1.5, 1.1), 0.0, 0.6), (sub(0.5, 110, 55, 0.12), 0.0, 0.7)))


def s_overwrite_big():
    return norm(mix(3.2, (glass(0.9, 120, 1800, 10000, 0.3), 0.0, 1.0), (bell(196, 3.0, 1.2), 0.0, 0.9),
                    (bell(293, 2.4, 0.9), 0.02, 0.5), (sub(1.2, 90, 32, 0.45), 0.0, 1.0)))


def s_blink():       # a step through reality: a short reversed shimmer that snaps shut
    w = whoosh(0.28, 6000, 1500, rising=False)
    return norm(mix(0.4, (reverse(tinkle(0.3, 12, 5000, 10000, 0.25)), 0.0, 0.5), (w, 0.0, 0.7), (tick(2400), 0.27, 0.5)))


def s_crystallize():
    return norm(mix(1.4, (tinkle(1.4, 60, 2500, 7000, 1.1), 0.0, 0.8), (choir(1.4, 440, (1, 1.5, 2.0)), 0.0, 0.3)))


def s_shard_throw():
    t = T(0.35)
    f = 3200 - 1800 * t / 0.35
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.15)
    return norm(mix(0.4, (s, 0.0, 0.6), (whoosh(0.3, 3000, 8000), 0.0, 0.5)))


def s_shard_hit():
    return norm(mix(0.6, (glass(0.4, 20, 3500, 9000, 0.1), 0.0, 0.8), (sub(0.2, 160, 80, 0.05), 0.0, 0.5)))


def s_shard_rain():
    return norm(mix(2.0, (tinkle(2.0, 140, 2500, 9500, 1.6), 0.0, 0.8), (glass(0.6, 50), 0.0, 0.5)))


def s_rend():        # space torn like a sheet of glass being pulled apart
    x = n(0.9)
    t = T(0.9)
    tear = band(x, 1200, 6000) * (0.5 + 0.5 * np.sin(2 * np.pi * 40 * t * (1 + t))) * np.exp(-t / 0.5)
    return norm(mix(1.0, (tear, 0.0, 0.7), (glass(0.7, 50), 0.05, 0.6)))


def s_seal():        # a seal stamped onto reality: a hard metallic ring
    return norm(mix(1.4, (bell(880, 1.3, 1.4, 0.006), 0.0, 0.8), (tick(900, 0.08), 0.0, 0.8), (sub(0.25, 140, 70, 0.07), 0.0, 0.6)))


def s_fissure():
    return norm(mix(2.0, (glass(1.2, 90, 1500, 8000, 0.25), 0.0, 0.8), (sub(1.6, 70, 30, 0.6), 0.0, 0.9),
                    (lpf(n(1.6), 300) * env(1.6, 0.05, 0.6), 0.0, 0.6)))


def s_slam():        # the ground struck: a low thud with glass debris settling
    return norm(mix(1.8, (sub(0.9, 120, 38, 0.3), 0.0, 1.0), (lpf(n(0.6), 500) * env(0.6, 0.001, 0.15), 0.0, 0.7),
                    (tinkle(1.5, 40, 2500, 7000, 1.2), 0.1, 0.35)))


def s_rupture():
    return norm(mix(3.0, (sub(2.2, 100, 26, 0.8), 0.0, 1.0), (lpf(n(2.5), 700) * env(2.5, 0.002, 0.7), 0.0, 0.8),
                    (glass(1.0, 100, 1500, 9000, 0.3), 0.0, 0.6), (bell(130, 2.8, 0.7), 0.0, 0.4)))


def s_collapse():    # Reality Collapse opening: a descending cluster of bells over a reversed swell
    sw = reverse(choir(1.6, 220, (1, 1.2, 1.5, 1.8))) * 0.8
    bells = mix(3.6, (bell(659, 2.6), 0.0, 0.5), (bell(554, 2.8), 0.18, 0.5), (bell(440, 3.0), 0.36, 0.6), (bell(330, 3.2), 0.54, 0.8))
    return norm(mix(5.2, (sw, 0.0, 0.7), (bells, 1.5, 1.0), (sub(2.0, 80, 40, 0.9), 1.5, 0.8)))


def s_lattice_hum():
    return norm(choir(4.0, 165, (1, 1.5, 2.0, 3.0)) * 0.9 + 0.1 * np.sin(2 * np.pi * 55 * T(4.0)))


def s_ascend():      # Heaven Ascension: a choir rising a fifth, crowned by bells
    return norm(mix(3.6, (choir(3.4, 262, (1, 1.25, 1.5, 2.0), rise=0.5), 0.0, 0.9),
                    (bell(1047, 2.0, 1.1), 1.6, 0.4), (bell(1319, 2.0, 1.1), 1.8, 0.35), (tinkle(2.0, 50, 4000, 9500, 1.5), 1.4, 0.3)))


def s_crack():       # a reality crack spreading
    return norm(glass(0.8, 70, 2000, 9000, 0.22))


def s_truth():       # the deepest toll he has: a huge bell with a reversed lead-in
    pre = reverse(bell(98, 1.4, 0.7)) * np.linspace(0, 1, int(SR * 1.4)) ** 3
    return norm(mix(6.0, (pre, 0.0, 0.6), (bell(98, 4.5, 1.0), 1.4, 1.0), (bell(147, 4.0, 0.9), 1.42, 0.6),
                    (sub(3.0, 60, 30, 1.4), 1.4, 0.9)))


def s_halt():        # time stopping: a clock tick, then everything drops in pitch and dies to silence
    t = T(1.6)
    f = 520 * np.exp(-t * 2.2) + 40
    drop = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.8)
    drop += 0.5 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * np.exp(-t / 0.6)
    return norm(mix(2.0, (tick(1500, 0.08), 0.0, 0.9), (tick(1200, 0.08), 0.18, 0.7), (drop, 0.3, 0.9),
                    (reverse(whoosh(0.3, 2000, 6000)), 0.0, 0.4)))


def s_impact():
    return norm(mix(0.8, (glass(0.3, 25), 0.0, 0.8), (sub(0.4, 140, 50, 0.1), 0.0, 0.9)))


def s_judgment():    # heavenly lightning: a bright crack of light and a long bell-like roll
    crack = hpf(n(0.12), 1500) * env(0.12, 0.0003, 0.03)
    roll = lpf(n(2.5), 400) * env(2.5, 0.05, 0.9)
    return norm(mix(3.0, (crack, 0.0, 1.0), (bell(740, 2.0, 1.3), 0.0, 0.5), (roll, 0.05, 0.8), (sub(1.5, 90, 40, 0.5), 0.0, 0.8)))


def s_chime():       # the title card: three clear bells
    return norm(mix(2.6, (bell(784, 2.0), 0.0, 0.6), (bell(988, 2.0), 0.15, 0.5), (bell(1175, 2.0), 0.3, 0.45)))


SET = [
    ("gather", s_gather, "Power gathers"), ("overwrite", s_overwrite, "Reality overwritten"),
    ("overwrite_big", s_overwrite_big, "Reality shatters"), ("blink", s_blink, "Reality step"),
    ("crystallize", s_crystallize, "Crystal forms"), ("shard_throw", s_shard_throw, "Shard thrown"),
    ("shard_hit", s_shard_hit, "Shard strikes"), ("shard_rain", s_shard_rain, "Shards fall"),
    ("rend", s_rend, "Reality tears"), ("seal", s_seal, "Seal struck"),
    ("fissure", s_fissure, "The ground splits"), ("slam", s_slam, "Impact"),
    ("rupture", s_rupture, "Rupture"), ("collapse", s_collapse, "Reality collapses"),
    ("lattice_hum", s_lattice_hum, "The lattice hums"), ("ascend", s_ascend, "Ascension"),
    ("crack", s_crack, "Reality cracks"), ("truth", s_truth, "The world's truth"),
    ("halt", s_halt, "Time halts"), ("impact", s_impact, "Overwrite impact"),
    ("judgment", s_judgment, "Heavenly lightning"), ("chime", s_chime, "The World Over Heaven"),
]


def write_ogg(name, sig):
    sig = np.clip(sig, -1, 1)
    pcm = (sig * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tf:
        path = tf.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", path, "-c:a", "libvorbis", "-q:a", "5",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    keep = {"time_stop_voice.ogg"}
    for f in os.listdir(OUT):
        if f.endswith(".ogg") and f not in keep:
            os.remove(os.path.join(OUT, f))
    sounds = {}
    for name, fn, sub_ in SET:
        write_ogg(name, fn())
        sounds[name] = {"subtitle": "subtitles.woh." + name, "sounds": [{"name": "woh:" + name, "stream": False}]}
        print("wrote", name)
    sounds["time_stop_voice"] = {"subtitle": "subtitles.woh.time_stop_voice",
                                 "sounds": [{"name": "woh:time_stop_voice", "stream": False}]}
    with open(os.path.join(ROOT, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
    lang_path = os.path.join(ROOT, "lang", "en_us.json")
    lang = {"subtitles.woh." + nm: sb for nm, _, sb in SET}
    lang["subtitles.woh.time_stop_voice"] = "The world stops"
    os.makedirs(os.path.dirname(lang_path), exist_ok=True)
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=1)
    print("done:", len(SET), "sounds + time_stop_voice")
