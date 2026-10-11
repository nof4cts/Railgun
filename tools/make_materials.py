"""Seamless materials for the sculpted giants, each 512x512 and tileable in both directions (the sculpted forms wrap
them around and around):

  bronze.png  living bronze: dark polished metal, verdigris mottling that has run down in streaks, hammer dimples
  marble.png  the sovereign's body: pale veined stone, faintly warm
  gold.png    crown, staff and mantle plates: brushed, bright gold
  cloth.png   the sovereign's cloak: heavy deep-crimson weave with long folds
  glow.png    a single soft white-gold fill for everything that burns (eyes, the crown's jewel, the staff's head)
"""
import os, sys
import numpy as np
from PIL import Image

OUT = sys.argv[1]
N = 512


def save(arr, name):
    full = os.path.join(OUT, 'entity', name)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA').save(full, optimize=True)


def noise(seed, power=2.2, stretch=(1.0, 1.0)):
    """Periodic fractal noise: white noise filtered in frequency space, so it tiles perfectly. A larger stretch on an
    axis smooths along that axis (stretch=(4, 0.5) gives long vertical streaks)."""
    r = np.random.default_rng(seed)
    fy = np.fft.fftfreq(N)[:, None] * stretch[0]
    fx = np.fft.fftfreq(N)[None, :] * stretch[1]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1
    spec = np.fft.fft2(r.normal(size=(N, N))) / f ** (power / 2 + 0.5)
    spec[0, 0] = 0
    z = np.real(np.fft.ifft2(spec))
    lo, hi = np.percentile(z, 1), np.percentile(z, 99)
    z = np.clip((z - lo) / (hi - lo), 0, 1)
    return z


def mix(a, b, t):
    return a * (1 - t[..., None]) + b * t[..., None]


def rgba(rgb):
    return np.concatenate([rgb, np.full(rgb.shape[:2] + (1,), 255.0)], -1)


# ---- bronze ------------------------------------------------------------------------------------------------
base = noise(1, 2.0)
fine = noise(2, 1.2)
dark = np.array([88, 54, 30.0])
bright = np.array([176, 118, 62.0])
metal = mix(dark, bright, np.clip(base * 1.1 - 0.05 + (fine - 0.5) * 0.25, 0, 1))
# verdigris: mottled, and run downward in streaks (v is down the body)
pat = noise(3, 2.4)
runs = noise(4, 2.0, stretch=(5.0, 0.6))
green = np.clip((pat * 0.65 + runs * 0.55 - 0.62) * 3.2, 0, 1)
verd = mix(np.array([58, 112, 92.0]), np.array([104, 160, 132.0]), noise(5, 1.6))
metal = mix(metal, verd, green * 0.85)
# hammer dimples: soft dark rings
dim = noise(6, 0.9)
metal *= (0.9 + 0.1 * np.clip(dim * 1.6 - 0.3, 0, 1))[..., None]
save(rgba(metal), 'bronze.png')

# ---- marble ------------------------------------------------------------------------------------------------
m0 = noise(11, 2.2)
veins = np.abs(np.sin((noise(12, 1.6) * 7 + m0 * 2) * np.pi))
vein = np.clip(1 - veins / 0.14, 0, 1) * 0.6 + np.clip(1 - veins / 0.05, 0, 1) * 0.4
stone = mix(np.array([214, 206, 192.0]), np.array([238, 233, 222.0]), m0)
stone = mix(stone, np.array([150, 136, 124.0]), vein * 0.55)
stone *= (0.95 + 0.05 * noise(13, 1.0))[..., None]
save(rgba(stone), 'marble.png')

# ---- gold --------------------------------------------------------------------------------------------------
brush = noise(21, 1.6, stretch=(0.4, 8.0))
g0 = noise(22, 2.0)
gold = mix(np.array([150, 102, 26.0]), np.array([255, 214, 110.0]), np.clip(g0 * 0.7 + brush * 0.5 - 0.1, 0, 1))
save(rgba(gold), 'gold.png')

# ---- cloth -------------------------------------------------------------------------------------------------
yy, xx = np.mgrid[0:N, 0:N]
weave = 0.5 + 0.25 * np.sin(xx * np.pi / 2) * np.sin(yy * np.pi / 2)
folds = noise(31, 2.0, stretch=(8.0, 0.6))
cloth = mix(np.array([70, 10, 22.0]), np.array([150, 26, 44.0]), np.clip(folds * 1.3 - 0.15, 0, 1))
cloth *= (0.9 + 0.1 * weave)[..., None]
# a gold hem band every tile on v (the cloak's lower edge sits at a tile boundary)
hem = ((yy % N) > N - 26) & ((yy % N) < N - 8)
cloth[hem] = mix(cloth[hem][None], np.array([210, 160, 60.0]) * np.ones_like(cloth[hem])[None], np.full((1, hem.sum()), 0.85))[0]
save(rgba(cloth), 'cloth.png')

# ---- glow --------------------------------------------------------------------------------------------------
g = np.full((16, 16, 4), 255.0)
img = Image.fromarray(g.astype(np.uint8), 'RGBA')
full = os.path.join(OUT, 'entity', 'glow.png')
img.save(full)
print('materials ok')
