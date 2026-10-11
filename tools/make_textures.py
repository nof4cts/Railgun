"""Draws the item textures and the plain white texture the Stand's hand-built model is tinted from."""
import math, os, sys
from PIL import Image, ImageDraw
OUT = sys.argv[1]

def save(img, path):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)

save(Image.new('RGBA', (16, 16), (255, 255, 255, 255)), 'misc/white.png')

# the Stand DISC: a pearly disc with a clock face and a gold rim, light catching it
S = 16
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
px = img.load()
for y in range(S):
    for x in range(S):
        dx, dy = x - 7.5, y - 7.5
        r = math.hypot(dx, dy)
        if r <= 7.4:
            ang = math.atan2(dy, dx)
            shine = 0.5 + 0.5 * math.cos(ang * 2 + 0.8)
            base = (int(205 + 40 * shine), int(215 + 35 * shine), int(230 + 25 * shine))
            if r > 6.3:
                base = (232, 190, 72) if (x + y) % 2 == 0 else (200, 156, 50)
            elif r < 1.3:
                base = (40, 40, 48)
            px[x, y] = base + (255,)
# clock marks and hands
for i in range(12):
    a = i / 12 * math.tau
    x, y = 7.5 + math.cos(a) * 5.0, 7.5 + math.sin(a) * 5.0
    px[int(round(x)), int(round(y))] = (30, 30, 36, 255)
for (length, ang, col) in ((4.2, -math.pi / 2, (20, 20, 26, 255)), (3.0, 0.4, (230, 60, 60, 255))):
    for k in range(1, int(length * 3)):
        t = k / 3
        x, y = 7.5 + math.cos(ang) * t, 7.5 + math.sin(ang) * t
        px[int(round(x)), int(round(y))] = col
save(img, 'item/stand_disc.png')

# the knife: steel blade, a gold guard, dark grip, diagonal like a vanilla sword
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
px = img.load()
for i in range(10):
    x, y = 4 + i, 11 - i
    if 0 <= x < S and 0 <= y < S:
        px[x, y] = (226, 232, 240, 255)
        if y + 1 < S:
            px[x, y + 1] = (170, 178, 190, 255)
px[14, 1] = (250, 252, 255, 255)
for d in (-1, 0, 1):
    px[4 + d, 12 + d] = (232, 190, 72, 255)
px[3, 12] = (232, 190, 72, 255)
px[4, 13] = (232, 190, 72, 255)
for i in range(3):
    px[3 - i, 13 + i] = (60, 40, 30, 255)
    px[2 - i, 13 + i] = (42, 28, 22, 255) if 2 - i >= 0 else (0, 0, 0, 0)
save(img, 'item/knife.png')
print('textures written')
