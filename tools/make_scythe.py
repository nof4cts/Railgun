"""Draws Requiem Rose's scythe textures (assets/requiem/textures/fx/scythe.png and scythe_glow.png).
Left half (x 0-31): the shaft, wrapped round its length (x = round, y = along, top of the shaft at y 0): black-violet
obsidian with a soft sheen, rose-gold collars and a leather-dark grip wrap, fine engraved channels.
Right half (x 32-63): rose-gold caps and the crown. The glow texture holds only the engraving, drawn full-bright.
Usage: make_scythe.py <assets/requiem>"""
import os, sys
import numpy as np
from PIL import Image
out = os.path.join(sys.argv[1], 'textures', 'fx'); os.makedirs(out, exist_ok=True)
N = 64
rng = np.random.default_rng(9)
img = np.zeros((N, N, 4)); glow = np.zeros((N, N, 4))
yy, xx = np.mgrid[0:N, 0:32]
# obsidian: deep violet-black, a sheen running down one side of the round, faint mineral noise
sheen = np.exp(-((xx - 9) / 4.5) ** 2) * 0.55 + np.exp(-((xx - 25) / 7.0) ** 2) * 0.12
noise = rng.random((N, 32)) * 0.05
base = np.stack([0.07 + 0.05 * sheen + noise, 0.02 + 0.02 * sheen + noise * 0.4, 0.07 + 0.07 * sheen + noise], -1)
gold = np.array([0.93, 0.62, 0.55]); gold_dk = np.array([0.55, 0.30, 0.28])
def collar(y0, y1):
    for y in range(y0, y1):
        k = (y - y0) / max(1, (y1 - y0 - 1))
        edge = 1.0 - 0.45 * (abs(k - 0.5) * 2) ** 3
        base[y, :, :] = gold_dk + (gold - gold_dk) * (0.5 + 0.5 * sheen[0][:, None] / 0.67 * 0) * 1.0
        base[y, :, :] = (gold * (0.65 + 0.35 * sheen[0][:, None] / sheen.max()) * edge)
for a, b in ((0, 3), (10, 12), (30, 32), (44, 46), (60, 64)):
    collar(a, b)
# the grip: dark wrap in diagonal bands between y 33 and 43
for y in range(33, 44):
    for x in range(32):
        band = ((x + y * 2) % 6) < 3
        base[y, x] = [0.10, 0.03, 0.06] if band else [0.05, 0.01, 0.03]
        base[y, x] *= 0.8 + 0.6 * sheen[0, x] / sheen.max()
img[:, :32, :3] = base; img[:, :32, 3] = 1
# engraving: two thin channels spiralling down the upper shaft, glowing rose
for y in range(3, 30):
    for s in (0, 16):
        x = int((y * 0.9 + s) % 32)
        img[y, x, :3] = [0.9, 0.25, 0.55]
        glow[y, x] = [1.0, 0.35, 0.68, 1.0]
        glow[y, (x + 1) % 32] = [0.85, 0.15, 0.45, 0.6]
for y in range(47, 60):
    x = int((-y * 1.3) % 32)
    img[y, x, :3] = [0.9, 0.25, 0.55]
    glow[y, x] = [1.0, 0.35, 0.68, 1.0]
# right half: rose gold with a hammered texture, and an inlaid ring of rose light at y 20-23
ry, rx = np.mgrid[0:N, 0:32]
ham = rng.random((N, 32)) * 0.12
rg = np.stack([gold[0] - ham, gold[1] - ham * 0.8, gold[2] - ham * 0.8], -1) * (0.75 + 0.25 * np.cos(rx / 32 * 6.28))[..., None]
img[:, 32:, :3] = rg; img[:, 32:, 3] = 1
img[20:24, 32:, :3] = [1.0, 0.4, 0.7]; glow[20:24, 32:] = [1.0, 0.4, 0.7, 1.0]
Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(out, 'scythe.png'))
Image.fromarray((np.clip(glow, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(out, 'scythe_glow.png'))
print('scythe ok')
