"""Synthesizes every sound in the mod. Run from the project root. Needs numpy, scipy, ffmpeg (libvorbis)."""
import os, subprocess, json
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
OUT = 'src/main/resources/assets/ironlung/sounds'
TMP = '/tmp/ironlung_wav'
os.makedirs(OUT, exist_ok=True); os.makedirs(TMP, exist_ok=True)
rng = np.random.default_rng(1337)

def t_(d): return np.arange(int(SR * d)) / SR
def noise(d): return rng.standard_normal(int(SR * d))
def lp(x, f, o=2): b, a = signal.butter(o, f / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2): b, a = signal.butter(o, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def bp(x, lo, hi, o=2): b, a = signal.butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band'); return signal.lfilter(b, a, x)
def env(d, a=0.01, r=None, shape=3.0):
    t = t_(d); n = len(t)
    e = np.ones(n)
    ai = max(1, int(a * SR)); e[:ai] = np.linspace(0, 1, ai)
    if r is None: r = d - a
    ri = int(r * SR)
    if ri > 0: e[-ri:] *= np.exp(-shape * np.linspace(0, 1, ri)) * np.linspace(1, 0, ri) ** 0.3
    return e
def norm(x, peak=0.9): m = np.max(np.abs(x)) + 1e-9; return x / m * peak
def reverb(x, d=1.2, mix=0.35, damp=2500, seed=0):
    r = np.random.default_rng(seed)
    n = int(SR * d); ir = r.standard_normal(n) * np.exp(-np.linspace(0, 7, n))
    ir = lp(ir, damp); ir[:200] *= np.linspace(0, 1, 200)
    wet = signal.fftconvolve(x, ir)
    wet = np.concatenate([wet, np.zeros(len(x) + n - len(wet))])[:len(x) + n]
    dry = np.concatenate([x, np.zeros(n)])
    wet = wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(x))
    return dry * (1 - mix) + wet * mix
def pad(x, d): return np.concatenate([x, np.zeros(int(SR * d))])
def metal_partials(d, f0, ratios, decays, amps, seed=0, glide=0.0):
    t = t_(d); out = np.zeros_like(t); r = np.random.default_rng(seed)
    for rt, dc, am in zip(ratios, decays, amps):
        f = f0 * rt * (1 + glide * (np.exp(-t * 2) - 1))
        ph = 2 * np.pi * np.cumsum(f) / SR + r.random() * 6
        out += am * np.sin(ph) * np.exp(-t * dc)
    return out
def thud(d, f=60, decay=12, click=0.3):
    t = t_(d)
    f_t = f * (1 + 1.5 * np.exp(-t * 40))
    s = np.sin(2 * np.pi * np.cumsum(f_t) / SR) * np.exp(-t * decay)
    s += click * lp(noise(d), 900) * np.exp(-t * 60)
    return s

sounds = {}
def save(name, x, loop=False, stream=False, peak=0.9, subtitle=True, variants=1):
    x = norm(np.asarray(x, dtype=np.float64), peak)
    wav = f'{TMP}/{name}.wav'
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', f'{OUT}/{name}.ogg'], check=True)
    return name

def register(event, files, stream=False, subtitle=True):
    entry = {'sounds': [{'name': f'ironlung:{f}', 'stream': stream} for f in files]}
    if subtitle: entry['subtitle'] = f'subtitles.ironlung.{event}'
    sounds[event] = entry

# ---------- loops (made periodic so they loop without a seam)
def loopify(x, xfade=1.0):
    n = int(SR * xfade)
    head, tail = x[:n], x[-n:]
    fade = np.linspace(0, 1, n)
    body = x[n:-n] if len(x) > 2 * n else x
    return np.concatenate([tail * (1 - fade) + head * fade, body])

d = 26.0; t = t_(d)
drone = 0.5 * np.sin(2 * np.pi * 36 * t + 0.4 * np.sin(2 * np.pi * t / 13)) + 0.35 * np.sin(2 * np.pi * 54.5 * t) * (0.6 + 0.4 * np.sin(2 * np.pi * t / 6.5))
drone += 0.22 * np.sin(2 * np.pi * 73 * t + np.sin(2 * np.pi * t / 26) * 2) * (0.5 + 0.5 * np.sin(2 * np.pi * t / 8.67 + 1))
rumble = lp(noise(d), 90, 3) * 3.5
swell = lp(noise(d), 300, 2) * (0.5 + 0.5 * np.sin(2 * np.pi * t / 13 - 1)) ** 4 * 2
drone = drone + rumble + swell
drone = lp(drone, 400)
save('ambient_drone', loopify(drone, 2.0), peak=0.7); register('ambient_drone', ['ambient_drone'], stream=True, subtitle=False)

d = 4.0; t = t_(d)
eng = sum(a * np.sin(2 * np.pi * f * t) for f, a in [(47.5, 0.8), (95, 0.5), (142.5, 0.3), (237.5, 0.15)])
eng *= 1 + 0.25 * np.sin(2 * np.pi * 11.75 * t)
eng += lp(noise(d), 600) * 0.4 * (1 + 0.5 * np.sin(2 * np.pi * 23.5 * t))
save('engine_hum', loopify(lp(eng, 900), 0.5), peak=0.6); register('engine_hum', ['engine_hum'], subtitle=False)

d = 3.0; t = t_(d)
buzz = signal.sawtooth(2 * np.pi * 120 * t) * 0.5 + np.sin(2 * np.pi * 240 * t) * 0.3 + np.sin(2 * np.pi * 360 * t) * 0.15
crackle = (rng.random(len(t)) < 0.0015) * rng.standard_normal(len(t)) * 3
buzz = bp(buzz, 90, 2500) + hp(lp(crackle, 5000), 800)
save('electric_buzz', loopify(buzz, 0.3), peak=0.55); register('electric_buzz', ['electric_buzz'], subtitle=True)

# ---------- hull
groans = []
for i in range(3):
    d = 4.5; t = t_(d); r = np.random.default_rng(100 + i)
    f0 = 38 + r.random() * 20
    g = np.zeros_like(t)
    for k, rt in enumerate([1, 1.52, 2.31, 3.07, 4.4]):
        glide = f0 * rt * (1 + 0.12 * np.sin(2 * np.pi * t / (3 + r.random() * 3)) - 0.08 * t / d)
        g += np.sin(2 * np.pi * np.cumsum(glide) / SR) / (k + 1) * (0.6 + 0.4 * np.sin(2 * np.pi * t * (0.3 + r.random())))
    stick = lp(noise(d), 1500) * (np.sin(2 * np.pi * t * (5 + r.random() * 6)) > 0.6) * 0.4
    g = (g + stick) * env(d, 0.6, 2.5, 2.5)
    g = reverb(lp(g, 1200), 1.5, 0.4, 1500, seed=i)
    groans.append(save(f'hull_groan{i + 1}', g, peak=0.85))
register('hull_groan', groans)

creaks = []
for i in range(3):
    d = 1.6; t = t_(d); r = np.random.default_rng(200 + i)
    rate = 18 + 25 * np.abs(np.sin(np.pi * t / d * (1 + r.random()))) + r.random() * 10
    ph = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float) * (0.5 + r.random(len(t)))
    c = bp(pulses, 300 + r.random() * 300, 2200, 2) * 3 + bp(pulses, 900, 1400, 4) * 2
    c *= env(d, 0.1, 0.8, 2)
    creaks.append(save(f'hull_creak{i + 1}', reverb(c, 0.8, 0.35, 2500, seed=10 + i), peak=0.7))
register('hull_creak', creaks)

knocks = []
for i in range(3):
    d = 1.8; r = np.random.default_rng(300 + i)
    k = thud(d, 55 + r.random() * 20, 9, 0.5) + 0.25 * metal_partials(d, 180 + r.random() * 60, [1, 2.76, 5.4, 8.9], [8, 12, 18, 25], [1, .6, .4, .2], seed=i)
    k = lp(k, 700)
    knocks.append(save(f'hull_knock{i + 1}', reverb(k, 1.6, 0.5, 900, seed=20 + i), peak=0.85))
register('hull_knock', knocks)

impacts = []
for i in range(2):
    d = 3.0
    k = thud(d, 48, 5, 1.0) * 1.2 + 0.6 * metal_partials(d, 110 + i * 25, [1, 1.47, 2.09, 2.76, 3.9, 5.4, 6.8], [2, 2.5, 3, 4, 6, 8, 11], [1, .8, .7, .5, .4, .3, .2], seed=i + 5, glide=0.02)
    k += lp(noise(d), 3000) * np.exp(-t_(d) * 18) * 0.8
    impacts.append(save(f'hull_impact{i + 1}', reverb(lp(k, 3500), 2.2, 0.45, 1800, seed=30 + i), peak=0.95))
register('hull_impact', impacts)

d = 2.2; t = t_(d)
scr = bp(noise(d), 200, 1600, 2) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * t * 7.3))) * (1 + 0.6 * np.sin(2 * np.pi * t * 31))
scr += metal_partials(d, 310, [1, 1.83, 2.9], [0.6, 0.8, 1.2], [0.4, 0.3, 0.2], seed=8, glide=-0.05) * 0.5
save('hull_scrape', reverb(scr * env(d, 0.3, 1.0, 1.5), 1.2, 0.4, 1600, seed=40), peak=0.8); register('hull_scrape', ['hull_scrape'])

d = 2.4
hiss = hp(noise(d), 2500) * env(d, 0.02, 1.8, 2.0) + bp(noise(d), 500, 1200) * 0.3 * env(d, 0.02, 0.8, 3)
save('pressure_hiss', hiss, peak=0.6); register('pressure_hiss', ['pressure_hiss'])

d = 0.55; t = t_(d)
drp = []
for i in range(3):
    f = (900 + i * 250) * (1 + 1.5 * t / d)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 28) * 0.6 + lp(noise(d), 4000) * np.exp(-t * 90) * 0.3
    drp.append(save(f'drip{i + 1}', reverb(s, 0.7, 0.45, 3000, seed=50 + i), peak=0.6))
register('drip', drp)

# ---------- electrical
d = 2.0; t = t_(d)
f = 420 * np.exp(-t * 2.2) + 30
pd = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.2) * 0.5
pd = lp(pd, 1500) + thud(d, 45, 14, 0.6) * 0.9
save('power_down', reverb(pd, 1.0, 0.3, 1500, seed=60), peak=0.85); register('power_down', ['power_down'])

d = 2.2; t = t_(d)
f = 40 + 380 * (1 - np.exp(-t * 1.6))
pu = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR) * (1 - np.exp(-t * 3)) * 0.4
for c in [0.05, 0.4, 0.75, 1.3]:
    idx = int(c * SR); pu[idx:idx + 600] += lp(noise(600 / SR), 3000) * np.exp(-np.linspace(0, 8, 600)) * 1.5
pu = lp(pu, 2500) * env(d, 0.01, 0.4, 3)
save('power_up', pu, peak=0.7); register('power_up', ['power_up'])

d = 0.7
bc = thud(d, 90, 25, 1.2) + metal_partials(d, 600, [1, 2.4, 3.9], [30, 40, 55], [0.4, 0.3, 0.2], seed=70)
save('breaker_clunk', reverb(bc, 0.6, 0.3, 2500, seed=70), peak=0.9); register('breaker_clunk', ['breaker_clunk'])

d = 0.16
cl = hp(noise(d), 1500) * np.exp(-t_(d) * 120) + metal_partials(d, 2400, [1, 1.7], [80, 120], [0.5, 0.3], seed=71)
save('helm_click', cl, peak=0.6); register('helm_click', ['helm_click'])

d = 1.0
ss = bp(noise(d), 800, 8000) * (0.4 + 0.6 * (rng.random(int(SR * d)) < 0.3)) * env(d, 0.005, 0.6, 2)
save('static_burst', ss, peak=0.55); register('static_burst', ['static_burst'])

# ---------- camera
d = 1.1; t = t_(d)
sh = np.zeros(int(SR * d))
for c, a in [(0.0, 1.0), (0.09, 0.8)]:
    i0 = int(c * SR); n = int(0.05 * SR)
    sh[i0:i0 + n] += (hp(noise(0.05), 1200) * np.exp(-np.linspace(0, 10, n)) * a)
    sh[i0:i0 + n] += metal_partials(0.05, 1800, [1, 2.3], [60, 90], [0.6, 0.4], seed=int(c * 100))[:n]
whirr = signal.sawtooth(2 * np.pi * 180 * t) * 0.15 * ((t > 0.18) & (t < 0.9)) * (1 + 0.3 * np.sin(2 * np.pi * 34 * t))
sh = sh + lp(whirr, 2000)
save('camera_shutter', reverb(sh, 0.5, 0.25, 3000, seed=80), peak=0.85); register('camera_shutter', ['camera_shutter'])

d = 2.0; t = t_(d)
dv = signal.sawtooth(2 * np.pi * (140 + 20 * np.sin(2 * np.pi * t * 0.7)) * t) * 0.3
gears = (np.sin(2 * np.pi * 18 * t) > 0.97).astype(float) * hp(noise(d), 2000) * 0.8
dv = lp(dv, 1600) + gears
dv *= env(d, 0.1, 0.3, 4)
save('camera_develop', dv, peak=0.5); register('camera_develop', ['camera_develop'])

d = 0.5; t = t_(d)
sv = signal.sawtooth(2 * np.pi * (320 + 120 * t) * t) * 0.4
save('camera_servo', lp(sv, 2200) * env(d, 0.04, 0.25, 3), peak=0.45); register('camera_servo', ['camera_servo'])

# ---------- alarms
d = 0.45; t = t_(d)
pg = np.sin(2 * np.pi * 1180 * t) * np.exp(-t * 9) + 0.3 * np.sin(2 * np.pi * 2360 * t) * np.exp(-t * 14)
save('proximity_ping', reverb(pg, 0.8, 0.3, 4000, seed=90), peak=0.6); register('proximity_ping', ['proximity_ping'])

d = 1.4; t = t_(d)
f = np.where((t % 0.7) < 0.35, 440, 330)
al = signal.square(2 * np.pi * np.cumsum(f) / SR) * 0.5
al = lp(al, 1800) * env(d, 0.02, 0.2, 3)
save('alarm', reverb(al, 0.6, 0.25, 2500, seed=91), peak=0.6); register('alarm', ['alarm'])

d = 1.2; t = t_(d)
wd = bp(noise(d), 1500, 9000) * ((rng.random(int(SR * d)) < 0.08) * 2 + 0.25) * env(d, 0.02, 0.3, 2)
save('weld', wd, peak=0.5); register('weld', ['weld'])

# ---------- the things outside
d = 9.0; t = t_(d)
lv = np.sin(2 * np.pi * np.cumsum(28 + 6 * np.sin(2 * np.pi * t / 9)) / SR) * 1.0
moan_f = 70 + 25 * np.sin(np.pi * t / d) - 10 * t / d
moan = signal.sawtooth(2 * np.pi * np.cumsum(moan_f) / SR)
moan = bp(moan, 120, 420, 2) * 1.6 + bp(moan, 500, 800, 2) * 0.5
lv = lv + moan + lp(noise(d), 120, 3) * 4
lv *= env(d, 2.5, 4.0, 2.0)
save('leviathan', reverb(lp(lv, 700), 3.5, 0.5, 600, seed=100), peak=0.95); register('leviathan', ['leviathan'], stream=True)

wails = []
for i in range(2):
    d = 6.5; t = t_(d); r = np.random.default_rng(400 + i)
    f = 95 + 60 * np.sin(np.pi * t / d) ** 2 - 30 * (t / d) + 6 * np.sin(2 * np.pi * t * 0.9)
    wl = signal.sawtooth(2 * np.pi * np.cumsum(f * (0.8 + 0.4 * i)) / SR)
    wl = bp(wl, 150, 500, 2) + bp(wl, 600, 900, 2) * 0.4
    wl *= env(d, 1.5, 3.5, 2)
    wails.append(save(f'distant_wail{i + 1}', reverb(lp(wl, 500), 4.0, 0.65, 500, seed=110 + i), peak=0.7))
register('distant_wail', wails, stream=True)

d = 3.2
hk = np.zeros(int(SR * d))
for c in [0.0, 0.9, 1.8]:
    k = thud(0.9, 70, 10, 0.8) + 0.4 * metal_partials(0.9, 210, [1, 2.7, 4.1], [9, 14, 20], [1, .5, .3], seed=int(c * 10))
    i0 = int(c * SR); hk[i0:i0 + len(k)] += k
save('hatch_knock', reverb(lp(hk, 1200), 1.2, 0.35, 1200, seed=120), peak=0.9); register('hatch_knock', ['hatch_knock'])

d = 4.5; t = t_(d)
im = thud(d, 32, 1.5, 2.0) * 1.5 + lp(noise(d), 2500) * np.exp(-t * 3) * 1.4
im += metal_partials(d, 160, [1, 1.3, 1.9, 2.6, 3.5], [1, 1.2, 1.6, 2, 3], [.8, .7, .6, .5, .3], seed=130, glide=-0.3)
save('implosion', reverb(im, 2.5, 0.4, 1200, seed=130), peak=1.0); register('implosion', ['implosion'])


# ---------- the finale
d = 2.6; t = t_(d)
hb = thud(d, 40, 3, 2.0) * 1.4 + metal_partials(d, 140, [1, 1.41, 2.2, 3.3, 4.7, 6.1], [1.5, 2, 2.5, 3.5, 5, 7], [1, .9, .8, .6, .4, .3], seed=140, glide=-0.15)
tear = bp(noise(d), 300, 2500) * np.exp(-t * 2.5) * (1 + np.sin(2 * np.pi * 19 * t)) * 1.2
hb += tear + signal.sawtooth(2 * np.pi * np.cumsum(900 - 600 * t / d) / SR) * 0.25 * np.exp(-t * 1.5)
save('hatch_breach', reverb(lp(hb, 5000), 1.6, 0.35, 2000, seed=141), peak=1.0); register('hatch_breach', ['hatch_breach'])

d = 2.4; t = t_(d)
sc = np.zeros(len(t))
for k, det in enumerate([1.0, 1.013, 0.987, 1.5, 0.75]):
    f = (380 + 520 * (1 - np.exp(-t * 9))) * det * (1 + 0.04 * np.sin(2 * np.pi * 31 * t))
    sc += signal.sawtooth(2 * np.pi * np.cumsum(f) / SR) * (0.7 if k < 3 else 0.35)
sc = bp(sc, 300, 7000, 2) * 1.1
# a human-ish scream: jittery glottal source through 'aah' formants
f0 = (310 + 140 * (1 - np.exp(-t * 6))) * (1 + 0.03 * rng.standard_normal(len(t)).cumsum() / np.sqrt(np.arange(1, len(t) + 1)))
glot = signal.sawtooth(2 * np.pi * np.cumsum(f0) / SR)
voice = bp(glot, 700, 1100, 2) * 1.0 + bp(glot, 1100, 1500, 2) * 0.8 + bp(glot, 2400, 3000, 2) * 0.5
sc += voice * 1.3
# teeth chattering
chat = (np.sin(2 * np.pi * 23 * t) > 0.92).astype(float) * hp(noise(d), 2500) * 1.5
sc += chat + hp(noise(d), 1500) * 0.5 + thud(d, 32, 4, 1.5) * 2.2
sc *= np.minimum(1, t * 90) * np.exp(-np.maximum(0, t - 1.4) * 3)
sc = np.tanh(sc * 1.9)
save('jumpscare', reverb(sc, 1.0, 0.25, 4000, seed=150), peak=1.0); register('jumpscare', ['jumpscare'])

d = 3.4; t = t_(d)
br = np.zeros(len(t))
for c0, dur, inhale in [(0.0, 1.3, True), (1.5, 1.6, False)]:
    i0, n = int(c0 * SR), int(dur * SR)
    e = np.sin(np.linspace(0, np.pi, n)) ** 1.5
    seg = bp(noise(dur), 250 if inhale else 180, 1400 if inhale else 900, 2) * e
    gurgle = (np.sin(2 * np.pi * np.linspace(0, dur, n) * (9 if inhale else 13)) > 0.7) * lp(noise(dur), 500) * e * 1.2
    br[i0:i0 + n] += seg + gurgle
br = lp(br, 2200)
save('breathing', reverb(br, 0.7, 0.3, 1800, seed=160), peak=0.9); register('breathing', ['breathing'])

with open('src/main/resources/assets/ironlung/sounds.json', 'w') as f:
    json.dump(sounds, f, indent=2)
print('sounds ok', len(sounds), 'events', len(os.listdir(OUT)), 'files')
