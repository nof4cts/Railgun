"""Draws the giant: colossus.png (basalt plates split by deep crevices, ash, soot and scorch) and colossus_glow.png
(molten light in the deepest crevices, burning eye sockets and a mouth like a furnace seam), both 1024x1024.

Every body part samples the shared rock field; the head has its own region with the face painted on its front.
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage

OUT = sys.argv[1]
N = 1024
rng = np.random.default_rng(1337)


def save(arr, path):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA').save(full, optimize=True)


def fbm(shape, octaves=6, base=8, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros(shape)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cells = base * 2 ** o
        g = r.random((cells + 1, cells + 1))
        z = ndimage.zoom(g, (shape[0] / cells, shape[1] / cells), order=3)[:shape[0], :shape[1]]
        out += z * amp
        total += amp
        amp *= 0.5
    return out / total


def voronoi(shape, points, seed):
    """Distance to the nearest and second-nearest seed: plates, and the seams between them."""
    r = np.random.default_rng(seed)
    pts = r.random((points, 2)) * np.array(shape)
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    d1 = np.full(shape, 1e9)
    d2 = np.full(shape, 1e9)
    idx = np.zeros(shape, int)
    for i, (py, px) in enumerate(pts):
        # tile so plates wrap
        for oy in (-shape[0], 0, shape[0]):
            for ox in (-shape[1], 0, shape[1]):
                d = np.hypot(yy - py - oy, xx - px - ox)
                closer = d < d1
                d2 = np.where(closer, d1, np.minimum(d2, d))
                idx = np.where(closer, i, idx)
                d1 = np.where(closer, d, d1)
    return d1, d2, idx


# --- the rock field -------------------------------------------------------------------------------
warp = fbm((N, N), 5, 6, 3)
d1, d2, idx = voronoi((N, N), 260, 7)
seam = d2 - d1                     # small along the seams between plates
plate_tone = np.random.default_rng(9).random(400)[idx]
grain = fbm((N, N), 7, 16, 11)
fine = fbm((N, N), 4, 96, 12)

crevice = np.clip(1 - seam / (5 + 4 * warp), 0, 1) ** 1.5      # 1 in a seam
deep = np.clip(1 - seam / (2.2 + 1.5 * warp), 0, 1) ** 2         # the deepest line in a seam

# basalt: near-black blue-grey with warm ash; each plate its own tone, worn lighter on the plate's raised centre
centre = np.clip(d1 / 40.0, 0, 1)
base = 22 + 22 * plate_tone + 26 * grain + 14 * fine - 12 * centre
r = base * 1.05 + 6
g = base * 0.98 + 2
b = base * 0.95
# ash streaks running down
ash = np.clip(fbm((N, N), 5, 4, 21) * 1.6 - 0.7, 0, 1) * (0.6 + 0.4 * fine)
r += 28 * ash; g += 26 * ash; b += 24 * ash
# scorch near the seams: the rock is burnt where the heat comes through
scorch = np.clip(1 - seam / 16.0, 0, 1) * 0.8
r = r * (1 - 0.35 * scorch) + 40 * scorch * (deep > 0.1)
g = g * (1 - 0.45 * scorch)
b = b * (1 - 0.55 * scorch)
# the crevice itself: black
r = r * (1 - crevice) + 6 * crevice
g = g * (1 - crevice) + 4 * crevice
b = b * (1 - crevice) + 4 * crevice
# plate edges catch light (a bevel above the seam)
edge = np.clip(1 - np.abs(seam - 7) / 3.0, 0, 1) * 0.5
r += 30 * edge; g += 28 * edge; b += 26 * edge

rock = np.stack([r, g, b, np.full((N, N), 255.0)], -1)

# molten light lives in some seams, not all: the hot ones
hot_plates = np.random.default_rng(31).random(400)
hot = (hot_plates[idx] > 0.35) * (fbm((N, N), 4, 5, 41) > 0.42)
heat = deep * hot
heat = np.maximum(heat, ndimage.gaussian_filter(heat, 2.5) * 1.6)
heat = np.clip(heat * (0.75 + 0.5 * fbm((N, N), 4, 32, 51)), 0, 1)
gr = 255 * np.clip(heat * 1.4, 0, 1)
gg = 255 * np.clip(heat * heat * 1.2 - 0.05, 0, 1) * 0.8 + 60 * heat
gb = 255 * np.clip(heat ** 3 - 0.3, 0, 1)
glow = np.stack([gr, gg, gb, 255 * np.clip(heat * 1.5, 0, 1)], -1)
# the rock is darker where it glows (the light is coming from inside)
rock[..., :3] = rock[..., :3] * (1 - 0.2 * heat[..., None]) + np.stack([90 * heat, 30 * heat, 0 * heat], -1)

# --- the face (head box at texOffs 640,0; its front face is the 64x64 at 704,64) --------------------
fx0, fy0 = 704, 64


def face_px(u, v):
    return fy0 + v, fx0 + u


yy, xx = np.mgrid[0:64, 0:64]
# brow shadow across the top third, a long jaw seam, sunken cheeks
face = rock[fy0:fy0 + 64, fx0:fx0 + 64].copy()
brow = np.clip(1 - np.abs(yy - 22) / 6.0, 0, 1)
face[..., :3] *= (1 - 0.45 * brow[..., None])
cheek = np.clip(1 - (np.abs(xx - 32) - 10) / 8.0, 0, 1) * np.clip((yy - 34) / 10, 0, 1) * np.clip((54 - yy) / 10, 0, 1)
face[..., :3] *= (1 - 0.25 * cheek[..., None])
gface = np.zeros((64, 64, 4))
for ex in (18, 46):
    d = np.hypot((xx - ex) / 1.15, (yy - 31) * 1.25)
    socket = np.clip(1 - d / 9.0, 0, 1)
    face[..., :3] *= (1 - 0.92 * socket[..., None])
    eye = np.clip(1 - d / 4.2, 0, 1)
    ring = np.clip(1 - np.abs(d - 5) / 2.0, 0, 1) * 0.5
    e = np.maximum(eye, ring)
    gface[..., 0] = np.maximum(gface[..., 0], 255 * np.clip(e * 1.3, 0, 1))
    gface[..., 1] = np.maximum(gface[..., 1], 255 * np.clip(e * 1.1, 0, 1) * (0.55 + 0.45 * eye))
    gface[..., 2] = np.maximum(gface[..., 2], 255 * np.clip(eye * 1.4 - 0.5, 0, 1))
    gface[..., 3] = np.maximum(gface[..., 3], 255 * np.clip(e * 1.6, 0, 1))
# the mouth: a jagged seam with heat behind it
mouth_y = 50 + 2.0 * np.sin(xx * 0.55) + np.where(xx % 7 < 2, 1.5, 0)
mouth = np.clip(1 - np.abs(yy - mouth_y) / 1.8, 0, 1) * np.clip(1 - np.abs(xx - 32) / 22.0, 0, 1)
face[..., :3] *= (1 - 0.9 * mouth[..., None])
gface[..., 0] = np.maximum(gface[..., 0], 255 * mouth)
gface[..., 1] = np.maximum(gface[..., 1], 150 * mouth)
gface[..., 2] = np.maximum(gface[..., 2], 30 * mouth)
gface[..., 3] = np.maximum(gface[..., 3], 230 * mouth)
rock[fy0:fy0 + 64, fx0:fx0 + 64] = face
glow[fy0:fy0 + 64, fx0:fx0 + 64] = np.maximum(glow[fy0:fy0 + 64, fx0:fx0 + 64], gface)

save(rock, 'entity/colossus.png')
save(glow, 'entity/colossus_glow.png')
print('textures ok')
