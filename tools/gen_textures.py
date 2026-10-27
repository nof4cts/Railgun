"""Procedural 16x16 textures for the Iron Lung mod. Run from the project root."""
import os, random
import numpy as np
from PIL import Image

OUT = 'src/main/resources/assets/ironlung/textures'
os.makedirs(OUT + '/block', exist_ok=True)
os.makedirs(OUT + '/item', exist_ok=True)

def noise(seed, s=16, scale=4):
    rng = np.random.default_rng(seed)
    small = rng.random((s // scale + 2, s // scale + 2))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((s + scale * 2, s + scale * 2), Image.BICUBIC)
    a = np.asarray(img, dtype=np.float32)[scale:scale + s, scale:scale + s] / 255.0
    return a

def metal(seed, base=(70, 58, 52), rust=0.45):
    rng = np.random.default_rng(seed)
    n1 = noise(seed, scale=4); n2 = noise(seed + 1, scale=2); n3 = rng.random((16, 16))
    tone = 0.75 + 0.35 * n1 + 0.12 * (n3 - 0.5)
    img = np.zeros((16, 16, 3), np.float32)
    for c in range(3):
        img[..., c] = base[c] * tone
    # rust blooms
    rmask = np.clip((n2 * 0.7 + n1 * 0.5 - (1.05 - rust)) * 3.0, 0, 1)
    rustcol = np.array([118, 52, 26], np.float32) * (0.7 + 0.5 * n3[..., None])
    img = img * (1 - rmask[..., None]) + rustcol * rmask[..., None]
    # dark pitting
    pits = n3 > 0.93
    img[pits] *= 0.55
    return img

def scratches(img, seed, count=3):
    rng = random.Random(seed)
    for _ in range(count):
        x, y = rng.randrange(16), rng.randrange(16)
        dx, dy = rng.choice([(1, 0), (1, 1), (0, 1), (1, -1)])
        for i in range(rng.randrange(3, 7)):
            xx, yy = x + dx * i, y + dy * i
            if 0 <= xx < 16 and 0 <= yy < 16:
                img[yy, xx] = np.minimum(255, img[yy, xx] * 1.35 + 8)
    return img

def edge_grime(img, k=0.75):
    for i in range(16):
        for j in range(16):
            d = min(i, j, 15 - i, 15 - j)
            if d == 0: img[i, j] *= k
            elif d == 1: img[i, j] *= (1 + k) / 2 + 0.1
    return img

def rivet(img, x, y):
    img[y, x] = [150, 128, 112]
    if x + 1 < 16 and y + 1 < 16: img[y + 1, x + 1] = [28, 22, 20]
    if x + 1 < 16: img[y, x + 1] = [96, 80, 70]
    if y + 1 < 16: img[y + 1, x] = [60, 48, 42]
    return img

def save(img, name):
    a = np.clip(img, 0, 255).astype(np.uint8)
    if a.shape[2] == 3:
        Image.fromarray(a, 'RGB').save(f'{OUT}/{name}.png')
    else:
        Image.fromarray(a, 'RGBA').save(f'{OUT}/{name}.png')

# ---------------- hull
img = edge_grime(scratches(metal(1), 11)); save(img, 'block/hull_plate')
img = edge_grime(metal(2))
for p in [(1, 1), (7, 1), (13, 1), (1, 13), (7, 13), (13, 13), (1, 7), (13, 7)]: rivet(img, *p)
save(scratches(img, 12, 2), 'block/hull_riveted')
img = metal(3, rust=0.55)
for x in range(16):
    y = 7 + (1 if x % 5 == 2 else 0)
    img[y, x] = [40, 30, 26]; img[y + 1, x] = [92, 70, 58]; img[y - 1, x] = img[y - 1, x] * 0.8
save(edge_grime(img), 'block/hull_seam')
img = edge_grime(metal(4, base=(62, 56, 52), rust=0.3))
img[2:14, 2:14] *= 1.08
for i in range(2, 14):
    img[2, i] *= 1.25; img[i, 2] *= 1.2; img[13, i] *= 0.6; img[i, 13] *= 0.6
for p in [(3, 3), (11, 3), (3, 11), (11, 11)]: rivet(img, *p)
save(img, 'block/bolted_panel')
# grate floor
img = metal(5, base=(48, 42, 40), rust=0.5) * 0.6
for i in range(16):
    for j in range(16):
        if i % 4 == 0 or j % 4 == 0:
            img[i, j] = img[i, j] * 1.35 + 6
        else:
            img[i, j] = img[i, j] * 0.35
save(img, 'block/grate_floor')
# warning plate: hazard stripes band + stencil marks
img = edge_grime(metal(6, rust=0.35))
for i in range(5, 11):
    for j in range(16):
        stripe = ((j + i) // 3) % 2 == 0
        img[i, j] = [150, 112, 30] if stripe else [30, 24, 20]
        img[i, j] = img[i, j] * (0.7 + 0.3 * noise(66)[i, j])
for j in range(3, 13, 2): img[2, j] = [140, 130, 115]
for j in range(4, 12, 3): img[13, j] = [140, 130, 115]
save(img, 'block/warning_plate')
# pipe
img = metal(7, base=(66, 58, 50), rust=0.6)
for i in range(16):
    shade = 0.55 + 0.75 * np.sin(np.pi * (i + 0.5) / 16)
    img[i, :] *= shade
img[:, 7] = img[:, 7] * 0.6
save(img, 'block/pipe')
img = metal(8, base=(66, 58, 50)) * 0.8
for i in range(16):
    for j in range(16):
        r = ((i - 7.5) ** 2 + (j - 7.5) ** 2) ** 0.5
        if r < 3.5: img[i, j] = [12, 8, 8]
        elif r < 5: img[i, j] *= 1.3
save(img, 'block/pipe_end')

# ---------------- console housing
side = edge_grime(metal(9, base=(56, 50, 46), rust=0.35))
for p in [(2, 2), (13, 2), (2, 13), (13, 13)]: rivet(side, *p)
save(side, 'block/console_side')
top = edge_grime(metal(10, base=(52, 46, 42)))
save(top, 'block/console_top')

def face(seed):
    f = edge_grime(metal(seed, base=(54, 48, 44), rust=0.3), 0.6)
    return f

def screen(f, x0, y0, x1, y1, on, glow=(150, 40, 28)):
    rng = np.random.default_rng(x0 * 7 + y0)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if on:
                v = 0.55 + 0.45 * rng.random()
                f[y, x] = [glow[0] * v, glow[1] * v, glow[2] * v]
                if (y - y0) % 2 == 1: f[y, x] *= 0.75
            else:
                f[y, x] = [14, 11, 10]
    for x in range(x0 - 1, x1 + 1):
        f[y0 - 1, x] = [20, 16, 15]; f[y1, x] = [92, 80, 72]
    for y in range(y0 - 1, y1 + 1):
        f[y, x0 - 1] = [20, 16, 15]; f[y, x1] = [92, 80, 72]
    return f

def lampdot(f, x, y, on, col=(220, 60, 30)):
    f[y, x] = col if on else (40, 18, 14)
    return f

def knob(f, x, y):
    f[y, x] = [110, 98, 90]; f[y, x + 1] = [70, 60, 55]; f[y + 1, x] = [70, 60, 55]; f[y + 1, x + 1] = [30, 24, 22]
    return f

def dial(f, cx, cy, r, on, needle_ang):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r: f[y, x] = [180, 168, 140] if on else [70, 64, 56]
            elif d <= r + 1: f[y, x] = [30, 24, 22]
    import math
    for t in np.linspace(0, r - 0.5, 8):
        x = int(round(cx + math.cos(needle_ang) * t)); y = int(round(cy + math.sin(needle_ang) * t))
        f[y, x] = [140, 20, 14] if on else [50, 20, 16]
    return f

for on in (True, False):
    sfx = 'on' if on else 'off'
    # helm: small numeric screen + two knobs + lamps
    f = face(20); screen(f, 3, 3, 13, 8, on); knob(f, 3, 11); knob(f, 11, 11)
    lampdot(f, 7, 11, on); lampdot(f, 8, 12, on, (200, 120, 30)); save(f, f'block/helm_{sfx}')
    # camera: big monitor
    f = face(21); screen(f, 2, 2, 14, 11, on, (120, 70, 55)); lampdot(f, 3, 13, on); knob(f, 11, 12); save(f, f'block/camera_{sfx}')
    # breaker: lever box
    f = face(22)
    f[3:13, 5:11] = [30, 26, 24]
    lev_y = 4 if on else 9
    f[lev_y:lev_y + 3, 6:10] = [150, 30, 20]
    f[3, 5:11] = [90, 80, 72]
    lampdot(f, 12, 3, on, (60, 200, 60)); lampdot(f, 12, 5, not on, (220, 40, 20))
    save(f, f'block/breaker_{sfx}')
    # ballast: two levers
    f = face(23)
    for lx, up in ((4, on), (10, not on)):
        f[3:13, lx:lx + 2] = [24, 20, 18]
        yy = 4 if up else 9
        f[yy:yy + 2, lx - 1:lx + 3] = [140, 110, 40]
    lampdot(f, 7, 2, on); save(f, f'block/ballast_{sfx}')
    # gauges
    f = face(24); dial(f, 7.5, 7.5, 5, on, 0.6 if on else 2.4); save(f, f'block/pressure_gauge_{sfx}')
    f = face(25); dial(f, 7.5, 6.5, 4, on, -0.9 if on else 2.4); screen(f, 4, 12, 12, 14, on); save(f, f'block/depth_gauge_{sfx}')
    # proximity: four lamps in a cross
    f = face(26); f[4:12, 6:10] = [30, 24, 22]
    for (x, y) in ((7, 2), (12, 7), (7, 12), (2, 7)):
        f[y:y + 2, x:x + 2] = [200, 50, 24] if on else [40, 16, 12]
    save(f, f'block/proximity_{sfx}')
# chart: paper pinned on steel
f = face(27)
paper = np.zeros((11, 11, 3), np.float32)
for y in range(11):
    for x in range(11):
        paper[y, x] = np.array([150, 138, 112]) * (0.85 + 0.15 * noise(77)[y, x])
        if x % 3 == 0 or y % 3 == 0: paper[y, x] *= 0.85
paper[3, 6] = [120, 20, 16]; paper[7, 3] = [120, 20, 16]; paper[5, 8] = [120, 20, 16]
f[2:13, 2:13] = paper
f[2, 2] = f[2, 12] = [180, 170, 150]
save(f, 'block/chart')
# sealant station: tank + nozzle + gauge
f = face(28)
for y in range(2, 14):
    for x in range(3, 9):
        f[y, x] = np.array([120, 110, 40]) * (0.6 + 0.4 * np.sin(np.pi * (x - 3 + 0.5) / 6))
f[7, 9:13] = [80, 72, 66]; f[6:9, 13] = [40, 34, 30]
f[3, 4:8] = [30, 26, 24]
save(f, 'block/sealant')
# hatch: round wheel
f = edge_grime(metal(29, base=(60, 54, 48), rust=0.5))
import math
for y in range(16):
    for x in range(16):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if 5 <= d <= 6.3: f[y, x] = [36, 30, 28]
        if 3.8 <= d <= 4.8: f[y, x] = [118, 104, 92]
for a in range(4):
    ang = a * math.pi / 2 + math.pi / 4
    for t in np.linspace(0, 4, 6):
        x = int(round(7.5 + math.cos(ang) * t)); y = int(round(7.5 + math.sin(ang) * t))
        f[y, x] = [118, 104, 92]
f[7:9, 7:9] = [60, 52, 46]
save(f, 'block/hatch')
# cage lamp
for on in (True, False):
    f = np.zeros((16, 16, 4), np.float32)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 8.5) ** 2) ** 0.5
            if d < 3.2:
                f[y, x] = [255, 214, 150, 255] if on else [60, 52, 40, 255]
                if on and d < 1.6: f[y, x] = [255, 245, 220, 255]
            if (x in (3, 7, 8, 12) or y in (4, 12)) and 3 <= x <= 12 and 4 <= y <= 13:
                f[y, x] = [42, 34, 30, 255]
    f[0:4, 7:9] = [48, 40, 36, 255]
    save(f, f'block/cage_lamp_{"on" if on else "off"}')

# ---------------- item: dive orders (sealed envelope, red stamp)
f = np.zeros((16, 16, 4), np.float32)
for y in range(3, 13):
    for x in range(1, 15):
        f[y, x] = [168, 150, 112, 255]
        if y == 3 or y == 12 or x == 1 or x == 14: f[y, x] = [110, 92, 66, 255]
for x in range(2, 14):
    y = 4 + abs(x - 7.5) * -0.6 + 4
    yy = int(round(4 + (6.5 - abs(x - 7.5)) * 0.55))
    f[yy, x] = [120, 100, 74, 255]
f[8:11, 6:10] = [140, 20, 16, 255]
f[9, 7:9] = [200, 40, 30, 255]
save(f, 'item/dive_orders')
print('textures ok', len(os.listdir(OUT + '/block')))
