"""Cuts the supplied scream / pain recordings into bursts at their quietest points and loudness-normalizes
every user-supplied clip (GoreBox included) so they cut through the synthesized layers in game."""
import subprocess, sys, numpy as np
OUT, GOREBOX, SCREAMS, PAIN = sys.argv[1:5]
SR = 22050
def decode(f):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', f, '-ac', '1', '-ar', str(SR), '-f', 's16le', '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.int16).astype(float) / 32768
def cut_points(x, start, end, n):
    hop = SR // 50; rms = np.sqrt(np.convolve(x ** 2, np.ones(hop) / hop, 'same'))
    pts = [start]
    for k in range(1, n):
        tgt = start + (end - start) * k / n; lo, hi = int((tgt - 0.6) * SR), int((tgt + 0.6) * SR)
        pts.append((lo + int(np.argmin(rms[lo:hi]))) / SR)
    return pts + [end]
def export(src, name, a, b, extra='', punch=False):
    dyn = 'acompressor=threshold=-38dB:ratio=12:attack=1:release=60:makeup=10,alimiter=limit=0.9:level=disabled,' if punch else ''
    af = f'afade=t=in:d=0.01,afade=t=out:st={max(b - a - 0.06, 0)}:d=0.06,{dyn}acompressor=threshold=-24dB:ratio=4:attack=5:release=80,loudnorm=I=-11:TP=-0.5:LRA=7' + (',' + extra if extra else '')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{a:.3f}', '-t', f'{b - a:.3f}', '-i', src, '-ac', '1', '-ar', '44100', '-af', af, '-c:a', 'libvorbis', '-q:a', '6', f'{OUT}/{name}.ogg'], check=True)
x = decode(SCREAMS); p = cut_points(x, 0.8, 10.18, 4)
for i in range(4): export(SCREAMS, f'jumpscare_scream_{i+1}', p[i], p[i + 1])
export(SCREAMS, 'jumpscare_scream_long', 0.8, 10.18)
x = decode(PAIN); p = cut_points(x, 0.24, 12.1, 5)
for i in range(5): export(PAIN, f'pain_{i+1}', p[i], p[i + 1])
gore = [(0.30, 0.56), (1.68, 0.41), (2.95, 1.99), (5.55, 1.20), (7.43, 0.99), (9.17, 0.83)]
for i, (s, d) in enumerate(gore): export(GOREBOX, f'gore_{i+1}', s, s + d, punch=True)
export(GOREBOX, 'rip_tear', 2.95, 4.94, 'asetrate=44100*0.85,aresample=44100', punch=True)
export(GOREBOX, 'gush_1', 5.55, 6.75, 'asetrate=44100*0.75,aresample=44100', punch=True)
export(GOREBOX, 'gush_2', 7.43, 8.42, 'asetrate=44100*0.8,aresample=44100', punch=True)
print('ok')
