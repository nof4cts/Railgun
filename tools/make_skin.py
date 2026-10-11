"""Paints the giant's skin (giant.png, 1024x1024) on the box layout of GiantModel: bare skin with soft shading and
blotches, muscle lines on the chest and belly, knees and elbows, a face with staring eyes and a fixed, too-wide grin,
hair with a ragged fringe (cut out with alpha), ears, nails. Per-giant tints are applied in the renderer, so skin is
painted in one light neutral tone; hair is dark enough to stay dark under any tint.

Box faces (Minecraft's layout) for a box at (u, v) of size W x H x D texels:
  top (u+D, v, W, D)   bottom (u+D+W, v, W, D)
  right (u, v+D, D, H) front (u+D, v+D, W, H) left (u+D+W, v+D, D, H) back (u+2D+W, v+D, W, H)
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1]
N = 1024
rng = np.random.default_rng(7)
img = np.zeros((N, N, 4), dtype=np.float64)

SKIN = np.array([236, 196, 170.0])

# box name: (u, v, W, H, D) in texels
B = {
    'hips': (0, 0, 112, 48, 56), 'thigh': (0, 110, 44, 128, 44), 'shin': (180, 110, 36, 120, 36), 'foot': (330, 110, 48, 16, 64),
    'abdomen': (340, 0, 96, 72, 48), 'chest': (630, 0, 128, 88, 64), 'neck': (560, 180, 36, 28, 36), 'head': (0, 300, 60, 68, 64),
    'hair': (260, 300, 64, 24, 68), 'ear': (740, 300, 8, 20, 16), 'jaw': (540, 300, 52, 20, 44), 'upper': (0, 480, 40, 128, 40),
    'deltoid': (170, 480, 48, 40, 48), 'fore': (370, 480, 36, 120, 36), 'hand': (520, 480, 40, 44, 32), 'finger': (680, 480, 9, 36, 12),
}


def faces(name):
    u, v, W, H, D = B[name]
    return {'top': (u + D, v, W, D), 'bottom': (u + D + W, v, W, D), 'right': (u, v + D, D, H), 'front': (u + D, v + D, W, H),
            'left': (u + D + W, v + D, D, H), 'back': (u + 2 * D + W, v + D, W, H)}


def blotch(h, w, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((max(2, h // scale + 2), max(2, w // scale + 2)))
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im, dtype=np.float64) / 255.0


def skin_face(x, y, w, h, seed, edge_dark=0.16, base=SKIN):
    """Bare skin: a little blotchy, darker toward the box's edges (soft occlusion)."""
    b1 = blotch(h, w, 24, seed)
    b2 = blotch(h, w, 6, seed + 1)
    yy, xx = np.mgrid[0:h, 0:w]
    ex = np.minimum(xx, w - 1 - xx) / max(1, w * 0.18)
    ey = np.minimum(yy, h - 1 - yy) / max(1, h * 0.18)
    occ = 1 - edge_dark * (1 - np.clip(np.minimum(ex, ey), 0, 1))
    col = base[None, None, :] * (0.92 + 0.1 * b1[..., None]) * (0.97 + 0.04 * b2[..., None])
    col *= occ[..., None]
    # faint warm/cool patches
    col[..., 0] *= 1 + 0.04 * (b1 - 0.5)
    col[..., 2] *= 1 - 0.05 * (b1 - 0.5)
    img[y:y + h, x:x + w, :3] = col
    img[y:y + h, x:x + w, 3] = 255


def paint_box(name, seed, edge_dark=0.16):
    for i, (k, (x, y, w, h)) in enumerate(faces(name).items()):
        skin_face(x, y, w, h, seed + i * 7, edge_dark)


for i, n in enumerate(B):
    paint_box(n, 10 + i * 31)


def line(draw_rect, x0, y0, x1, y1, col, width):
    pass


def shade(x, y, w, h, mask, amount, colour=(120, 70, 60)):
    """Darken (or tint) a face region by a 0..1 mask."""
    reg = img[y:y + h, x:x + w, :3]
    c = np.array(colour, dtype=np.float64)
    img[y:y + h, x:x + w, :3] = reg * (1 - amount * mask[..., None]) + c * (amount * mask[..., None]) * 0.35


def soft(mask, r):
    im = Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))
    return np.asarray(im, dtype=np.float64) / 255.0


def canvas(w, h):
    im = Image.new('L', (w, h), 0)
    return im, ImageDraw.Draw(im)


def arr(im):
    return np.asarray(im, dtype=np.float64) / 255.0


# ---- chest: collarbones, pectorals, a breastbone line
x, y, w, h = faces('chest')['front']
im, d = canvas(w, h)
d.arc((6, 30, w // 2 + 2, 82), 200, 340, fill=255, width=3)
d.arc((w // 2 - 2, 30, w - 6, 82), 200, 340, fill=255, width=3)
d.line((w // 2, 34, w // 2, 80), fill=200, width=2)
d.line((10, 10, w // 2 - 6, 16), fill=180, width=2)
d.line((w - 10, 10, w // 2 + 6, 16), fill=180, width=2)
shade(x, y, w, h, soft(arr(im), 2.0), 0.35)
for cx in (w // 2 - 30, w // 2 + 30):
    im, d = canvas(w, h)
    d.ellipse((cx - 3, 58, cx + 3, 63), fill=255)
    shade(x, y, w, h, soft(arr(im), 1.0), 0.35, (160, 90, 80))
# back: shoulder blades and spine
x, y, w, h = faces('chest')['back']
im, d = canvas(w, h)
d.line((w // 2, 0, w // 2, h), fill=200, width=2)
d.arc((14, 14, w // 2 - 6, 70), 300, 60, fill=200, width=2)
d.arc((w // 2 + 6, 14, w - 14, 70), 120, 240, fill=200, width=2)
shade(x, y, w, h, soft(arr(im), 2.0), 0.28)

# ---- belly: the line down the middle, a navel, the edges of the ribs
x, y, w, h = faces('abdomen')['front']
im, d = canvas(w, h)
d.line((w // 2, 0, w // 2, h - 20), fill=170, width=2)
for k in range(2):
    d.line((w // 2 - 22, 14 + k * 18, w // 2 + 22, 14 + k * 18), fill=90, width=2)
d.arc((4, -30, w - 4, 22), 20, 160, fill=170, width=2)
shade(x, y, w, h, soft(arr(im), 2.0), 0.28)
im, d = canvas(w, h)
d.ellipse((w // 2 - 3, h - 26, w // 2 + 3, h - 19), fill=255)
shade(x, y, w, h, soft(arr(im), 1.0), 0.55)
# hips: smooth, a crease where the legs begin (nothing more: these are giants, not anatomy charts)
x, y, w, h = faces('hips')['front']
im, d = canvas(w, h)
d.line((8, 10, w // 2 - 4, h - 6), fill=200, width=2)
d.line((w - 8, 10, w // 2 + 4, h - 6), fill=200, width=2)
shade(x, y, w, h, soft(arr(im), 2.5), 0.25)

# ---- knees and elbows
for name, yk in (('thigh', None), ('shin', 14)):
    if yk is None:
        continue
    x, y, w, h = faces(name)['front']
    im, d = canvas(w, h)
    d.arc((6, yk - 10, w - 6, yk + 12), 20, 160, fill=255, width=2)
    shade(x, y, w, h, soft(arr(im), 1.5), 0.3)
x, y, w, h = faces('fore')['back']
im, d = canvas(w, h)
d.arc((8, 2, w - 8, 18), 0, 180, fill=255, width=2)
shade(x, y, w, h, soft(arr(im), 1.5), 0.3)
# toes and nails
x, y, w, h = faces('foot')['top']
im, d = canvas(w, h)
for k in range(1, 5):
    d.line((k * w // 5, 0, k * w // 5, 12), fill=255, width=1)
shade(x, y, w, h, soft(arr(im), 0.8), 0.35)
x, y, w, h = faces('finger')['front']
im, d = canvas(w, h)
d.rectangle((1, h - 8, w - 2, h - 2), fill=255)
reg = img[y:y + h, x:x + w, :3]
m = arr(im)[..., None]
img[y:y + h, x:x + w, :3] = reg * (1 - 0.6 * m) + np.array([250, 225, 215.0]) * 0.6 * m

# ---- the face
x, y, w, h = faces('head')['front']        # 60 x 68
fx, fy = x, y
face = Image.fromarray(img[y:y + h, x:x + w, :3].clip(0, 255).astype(np.uint8))
d = ImageDraw.Draw(face)
# brow shadow and cheekbones
im, dd = canvas(w, h)
dd.rectangle((6, 22, w - 6, 27), fill=255)
shade(x, y, w, h, soft(arr(im), 2.0), 0.25)
face = Image.fromarray(img[y:y + h, x:x + w, :3].clip(0, 255).astype(np.uint8))
d = ImageDraw.Draw(face)
for cx in (17, 43):
    # sockets
    d.ellipse((cx - 9, 25, cx + 9, 37), fill=(150, 100, 90))
    # wide, staring eyes: white, a small pupil looking straight ahead
    d.ellipse((cx - 7, 27, cx + 7, 35), fill=(246, 244, 236))
    d.ellipse((cx - 3, 28, cx + 3, 34), fill=(70, 48, 34))
    d.ellipse((cx - 1, 30, cx + 1, 32), fill=(10, 8, 8))
    d.point((cx + 1, 29), fill=(255, 255, 255))
    # brows
    d.line((cx - 9, 22, cx + 8, 21 + (1 if cx < 30 else 0)), fill=(70, 46, 32), width=2)
# nose
d.line((30, 30, 28, 44), fill=(200, 150, 130), width=1)
d.line((26, 46, 34, 46), fill=(170, 115, 100), width=1)
d.point((27, 45), fill=(120, 70, 60))
d.point((33, 45), fill=(120, 70, 60))
# the grin, ear to ear: lips pulled back over a row of upper teeth
d.arc((2, 30, w - 2, 66), 15, 165, fill=(150, 70, 70), width=2)
d.rectangle((7, 56, w - 7, 67), fill=(70, 18, 24))
d.rectangle((8, 56, w - 8, 58), fill=(176, 72, 80))
for k in range(10):
    tx = 9 + k * 4.4
    d.rectangle((int(tx), 58, int(tx + 2.8), 65 - (1 if k in (0, 9) else 0)), fill=(244, 238, 222))
# smile creases running up the cheeks
d.line((5, 52, 2, 42), fill=(185, 125, 110), width=1)
d.line((w - 5, 52, w - 2, 42), fill=(185, 125, 110), width=1)
img[y:y + h, x:x + w, :3] = np.asarray(face, dtype=np.float64)

# the jaw's front: the lower teeth, then the lip, then a chin
x, y, w, h = faces('jaw')['front']         # 52 x 20
jaw = Image.fromarray(img[y:y + h, x:x + w, :3].clip(0, 255).astype(np.uint8))
d = ImageDraw.Draw(jaw)
d.rectangle((3, 0, w - 3, 8), fill=(70, 18, 24))
for k in range(9):
    tx = 5 + k * 4.8
    d.rectangle((int(tx), 1, int(tx + 3), 6), fill=(238, 232, 214))
d.line((3, 8, w - 3, 8), fill=(160, 80, 80), width=2)
d.arc((14, 12, w - 14, 22), 200, 340, fill=(190, 135, 115), width=1)
img[y:y + h, x:x + w, :3] = np.asarray(jaw, dtype=np.float64)

# ---- ears: a darker inner curl
x, y, w, h = faces('ear')['left']
img[y + 4:y + h - 4, x + 4:x + w - 4, :3] *= 0.78
x, y, w, h = faces('ear')['right']
img[y + 4:y + h - 4, x + 4:x + w - 4, :3] *= 0.78

# ---- hair: dark, coarse, a ragged fringe cut out with alpha
HAIR = np.array([42, 30, 24.0])


def hair_face(x, y, w, h, fringe, seed):
    r = np.random.default_rng(seed)
    strands = blotch(h, w, 3, seed)
    col = HAIR[None, None, :] * (0.7 + 0.6 * strands[..., None])
    img[y:y + h, x:x + w, :3] = col
    img[y:y + h, x:x + w, 3] = 255
    if fringe:
        # a jagged lower edge: the hair hangs to different lengths
        for xx in range(w):
            length = int(h * (0.35 + 0.55 * r.random())) if xx % 3 == 0 else None
            if length is None:
                length = int(h * (0.45 + 0.3 * r.random()))
            img[y + length:y + h, x + xx, 3] = 0


for k, (x, y, w, h) in faces('hair').items():
    hair_face(x, y, w, h, k in ('front', 'left', 'right', 'back'), 99 + len(k))
# the back hangs longer: full
x, y, w, h = faces('hair')['back']
img[y:y + h, x:x + w, 3] = 255

os.makedirs(os.path.join(OUT, 'entity'), exist_ok=True)
Image.fromarray(img.clip(0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'entity', 'giant.png'), optimize=True)
print('skin ok')
