"""Paints the blight's creatures on their models' box layouts, each with a glow map (eyes, veins, the light inside them)
that renders at full brightness. Colours are the strike's: grey-violet dead skin, crimson flesh, magenta light, teal.
Usage: make_mobs.py <assets/orbital_arsenal/textures>"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

OUT = os.path.join(sys.argv[1], 'entity')
os.makedirs(OUT, exist_ok=True)

SKIN = np.array([156, 118, 136.0])
FLESH = np.array([132, 26, 58.0])
FLESH_HI = np.array([186, 58, 96.0])
CHITIN = np.array([38, 18, 40.0])
BONE = np.array([226, 214, 196.0])
MAG = np.array([255, 92, 186.0])
TEAL = np.array([60, 220, 205.0])
DARK = np.array([30, 6, 14.0])


def noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((h // scale + 2, w // scale + 2))
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((w + 2 * scale, h + 2 * scale), Image.BICUBIC)
    return np.asarray(im, dtype=np.float64)[scale:scale + h, scale:scale + w] / 255


def mix(a, b, t):
    return a * (1 - t[..., None]) + b * t[..., None]


def faces(u, v, W, H, D):
    return {'top': (u + D, v, W, D), 'bottom': (u + D + W, v, W, D), 'right': (u, v + D, D, H), 'front': (u + D, v + D, W, H),
            'left': (u + D + W, v + D, D, H), 'back': (u + 2 * D + W, v + D, W, H)}


class Tex:
    def __init__(self, size, seed):
        self.n = size
        self.rgb = np.zeros((size, size, 3))
        self.a = np.zeros((size, size))
        self.glow = np.zeros((size, size, 3))
        self.seed = seed

    def fill(self, box, base, hi, rot=0.25, torn=0.0, veins=0.0):
        u, v, W, H, D = box
        for k, (x, y, w, h) in faces(u, v, W, H, D).items():
            if w <= 0 or h <= 0:
                continue
            x2, y2 = min(self.n, x + w), min(self.n, y + h)
            w, h = x2 - x, y2 - y
            if w <= 0 or h <= 0:
                continue
            self.seed += 1
            n = noise(h, w, 3, self.seed) * 0.7 + noise(h, w, 1, self.seed + 50) * 0.3
            c = mix(base * (1 - rot * 0.5), hi, n)
            if torn > 0:
                t = noise(h, w, 4, self.seed + 9)
                m = (t > 1 - torn).astype(float)
                c = mix(c, mix(FLESH * 0.85, FLESH_HI, noise(h, w, 1, self.seed + 3)), m)
            # darker toward the edges of each face
            yy, xx = np.mgrid[0:h, 0:w]
            ex = np.minimum(xx, w - 1 - xx) / max(1, w * 0.2)
            ey = np.minimum(yy, h - 1 - yy) / max(1, h * 0.2)
            c *= (1 - 0.18 * (1 - np.clip(np.minimum(ex, ey), 0, 1)))[..., None]
            self.rgb[y:y2, x:x2] = c
            self.a[y:y2, x:x2] = 255
            if veins > 0:
                vm = self.veinmask(h, w, int(2 + veins * 4))
                self.rgb[y:y2, x:x2] = mix(self.rgb[y:y2, x:x2], MAG * 0.8, vm * 0.7)
                self.glow[y:y2, x:x2] = np.maximum(self.glow[y:y2, x:x2], MAG[None, None, :] * vm[..., None] * veins)

    def veinmask(self, h, w, count):
        r = np.random.default_rng(self.seed + 77)
        m = np.zeros((h, w))
        for _ in range(count):
            x, y, a = r.random() * w, r.random() * h, r.random() * 6.28
            for _ in range(int((w + h) * 1.2)):
                if 0 <= int(y) < h and 0 <= int(x) < w:
                    m[int(y), int(x)] = 1
                a += (r.random() - 0.5) * 1.0
                x += np.cos(a) * 0.8
                y += np.sin(a) * 0.8
        return m

    def face(self, box, which):
        u, v, W, H, D = box
        return faces(u, v, W, H, D)[which]

    def draw(self, fn):
        img = Image.fromarray(np.clip(self.rgb, 0, 255).astype(np.uint8))
        gl = Image.fromarray(np.clip(self.glow, 0, 255).astype(np.uint8))
        fn(ImageDraw.Draw(img), ImageDraw.Draw(gl))
        self.rgb = np.asarray(img, dtype=np.float64).copy()
        self.glow = np.asarray(gl, dtype=np.float64).copy()

    def save(self, name):
        Image.fromarray(np.concatenate([np.clip(self.rgb, 0, 255), self.a[..., None]], -1).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name + '.png'))
        g = np.clip(self.glow, 0, 255)
        ga = (g.max(-1) > 4) * 255
        Image.fromarray(np.concatenate([g, ga[..., None]], -1).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name + '_glow.png'))


# ---------------------------------------------------------------- the Inverted
t = Tex(64, 100)
head, body, flapL, flapR, arm, leg, spike = (0, 0, 8, 8, 8), (16, 16, 9, 13, 5), (44, 16, 4, 9, 1), (44, 26, 4, 9, 1), (40, 36, 3, 21, 3), \
    (0, 32, 4, 14, 4), (0, 54, 2, 2, 3)
t.fill(head, SKIN * 0.9, SKIN * 1.1, torn=0.35, veins=0.3)
t.fill(body, SKIN * 0.9, SKIN * 1.1, torn=0.4, veins=0.4)
t.fill(flapL, SKIN * 0.85, SKIN * 1.05, torn=0.3)
t.fill(flapR, SKIN * 0.85, SKIN * 1.05, torn=0.3)
t.fill(arm, SKIN * 0.85, SKIN * 1.05, torn=0.45, veins=0.3)
t.fill(leg, SKIN * 0.85, SKIN * 1.05, torn=0.4)
t.fill(spike, BONE * 0.85, BONE)


def inverted(d, g):
    x, y, w, h = t.face(head, 'front')
    # (the head hangs upside down, so the face is drawn the right way up and shows inverted)
    for ex in (x + 1, x + 5):
        d.rectangle((ex, y + 2, ex + 1, y + 3), fill=(20, 6, 12))
        g.rectangle((ex, y + 2, ex + 1, y + 3), fill=(255, 200, 230))
    d.rectangle((x + 1, y + 5, x + 6, y + 6), fill=(30, 6, 12))
    for k in range(3):
        d.point((x + 2 + k * 2, y + 5), fill=(230, 220, 200))
    # the chest: a seam of light where the flaps part
    bx, by, bw, bh = t.face(body, 'front')
    d.rectangle((bx + 3, by + 3, bx + 5, by + 12), fill=(60, 8, 24))
    g.rectangle((bx + 4, by + 3, bx + 4, by + 12), fill=(255, 92, 186))
    g.rectangle((bx + 3, by + 6, bx + 5, by + 9), fill=(200, 60, 150))
    for f in (flapL, flapR):
        fx, fy, fw, fh = t.face(f, 'back')
        d.rectangle((fx, fy, fx + fw - 1, fy + fh - 1), fill=(150, 30, 60))
        for k in range(0, fh, 2):
            d.line((fx, fy + k, fx + fw - 1, fy + k), fill=(230, 210, 195))


t.draw(inverted)
t.save('inverted')

# ---------------------------------------------------------------- the Skitter
t = Tex(64, 200)
sbody, sac, shead, up, lo, mand = (0, 0, 10, 6, 14), (0, 20, 7, 4, 8), (30, 20, 6, 5, 5), (0, 36, 9, 2, 2), (0, 42, 11, 2, 2), (30, 32, 1, 1, 4)
t.fill(sbody, CHITIN, CHITIN * 2.2, veins=0.2)
t.fill(sac, FLESH, FLESH_HI)
t.fill(shead, CHITIN, CHITIN * 2.0)
t.fill(up, CHITIN, FLESH * 0.8)
t.fill(lo, CHITIN, CHITIN * 1.8)
t.fill(mand, BONE * 0.8, BONE)


def skitter(d, g):
    for k in faces(*sac).values():
        x, y, w, h = k
        g.rectangle((x, y, x + w - 1, y + h - 1), fill=(40, 170, 160))
        for i in range(0, w, 3):
            g.line((x + i, y, x + i, y + h - 1), fill=(120, 255, 235))
    x, y, w, h = t.face(shead, 'front')
    for ex, ey in ((x + 1, y + 1), (x + 4, y + 1), (x + 2, y + 2), (x + 3, y + 2)):
        d.point((ex, ey), fill=(255, 92, 186))
        g.point((ex, ey), fill=(255, 140, 210))
    # plates across the back
    x, y, w, h = t.face(sbody, 'top')
    for i in range(0, h, 3):
        d.line((x, y + i, x + w - 1, y + i), fill=(110, 26, 60))


t.draw(skitter)
t.save('skitter')

# ---------------------------------------------------------------- the Bloater
t = Tex(64, 300)
bbody, lump0, lump1, bleg = (0, 0, 14, 14, 14), (0, 28, 8, 4, 7), (30, 28, 5, 6, 3), (0, 40, 3, 4, 3)
t.fill(bbody, FLESH_HI * 0.9, np.array([226, 140, 170.0]), veins=0.8)
t.fill(lump0, np.array([220, 170, 180.0]), np.array([250, 220, 220.0]), veins=0.5)
t.fill(lump1, np.array([220, 170, 180.0]), np.array([250, 220, 220.0]), veins=0.5)
t.fill(bleg, FLESH * 0.8, FLESH)


def bloater(d, g):
    x, y, w, h = t.face(bbody, 'front')
    # a ring of small mouths, light inside each
    for i in range(6):
        a = i / 6 * 6.283
        cx, cy = x + w / 2 + np.cos(a) * 4, y + h / 2 + np.sin(a) * 4
        d.ellipse((cx - 1.2, cy - 1.2, cx + 1.2, cy + 1.2), fill=(40, 6, 16))
        g.ellipse((cx - 0.8, cy - 0.8, cx + 0.8, cy + 0.8), fill=(255, 120, 200))
    d.ellipse((x + w / 2 - 2, y + h / 2 - 2, x + w / 2 + 2, y + h / 2 + 2), fill=(30, 4, 12))
    g.ellipse((x + w / 2 - 1.2, y + h / 2 - 1.2, x + w / 2 + 1.2, y + h / 2 + 1.2), fill=(255, 92, 186))


t.draw(bloater)
t.save('bloater')

# ---------------------------------------------------------------- the Maw
t = Tex(128, 400)
hips, torso, inner, jaw, tooth, uarm, fore, claw, mspike, mleg = (0, 0, 16, 6, 10), (0, 16, 20, 22, 12), (64, 24, 14, 18, 1), (64, 0, 9, 18, 2), \
    (100, 0, 1, 2, 2), (32, 64, 7, 18, 7), (64, 64, 6, 16, 6), (100, 10, 1, 8, 1), (100, 24, 2, 6, 2), (0, 64, 7, 16, 7)
t.fill(hips, SKIN * 0.75, SKIN, torn=0.45, veins=0.3)
t.fill(torso, SKIN * 0.75, SKIN, torn=0.5, veins=0.4)
t.fill(inner, DARK, FLESH, veins=1.0)
t.fill(jaw, SKIN * 0.75, SKIN, torn=0.4)
t.fill(tooth, BONE * 0.85, BONE)
t.fill(uarm, SKIN * 0.7, SKIN * 0.95, torn=0.55, veins=0.3)
t.fill(fore, SKIN * 0.7, SKIN * 0.95, torn=0.5)
t.fill(claw, BONE * 0.8, BONE)
t.fill(mspike, BONE * 0.8, BONE)
t.fill(mleg, SKIN * 0.7, SKIN * 0.95, torn=0.5)


def maw(d, g):
    x, y, w, h = t.face(inner, 'front')
    g.rectangle((x + 3, y + 1, x + w - 4, y + h - 2), fill=(170, 40, 120))
    g.rectangle((x + 6, y + 3, x + w - 7, y + h - 4), fill=(255, 92, 186))
    for k in range(0, h, 3):
        d.line((x, y + k, x + 2, y + k), fill=(230, 220, 200))
        d.line((x + w - 3, y + k, x + w - 1, y + k), fill=(230, 220, 200))
    for side in ('back',):
        jx, jy, jw, jh = t.face(jaw, side)
        d.rectangle((jx, jy, jx + jw - 1, jy + jh - 1), fill=(150, 30, 60))
    # bone plates on the forearms
    fx, fy, fw, fh = t.face(fore, 'front')
    for k in range(3):
        d.rectangle((fx, fy + 2 + k * 5, fx + fw - 1, fy + 4 + k * 5), fill=(212, 198, 180))


t.draw(maw)
t.save('maw')
print('mobs ok')
