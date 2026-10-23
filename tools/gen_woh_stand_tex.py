"""The Stand's texture (256x128): left half white marble with soft grey veins, right half brushed gold."""
import os
import numpy as np
from PIL import Image

rng = np.random.default_rng(77)
W, H = 256, 128
HW = W // 2
img = np.zeros((H, W, 3))


def value_noise(h, w, cell, seed):
    r = np.random.default_rng(seed)
    gh, gw = h // cell + 2, w // cell + 2
    g = r.random((gh, gw))
    y = np.arange(h)[:, None] / cell
    x = np.arange(w)[None, :] / cell
    y0, x0 = np.floor(y).astype(int), np.floor(x).astype(int)
    fy, fx = y - y0, x - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a, b = g[y0, x0], g[y0, x0 + 1]
    c, d = g[y0 + 1, x0], g[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


# marble: a warm white with veins following a turbulent sine
y, x = np.mgrid[0:H, 0:HW]
turb = value_noise(H, HW, 32, 1) * 1.0 + value_noise(H, HW, 16, 2) * 0.5 + value_noise(H, HW, 8, 3) * 0.25 + value_noise(H, HW, 4, 6) * 0.12
veins = np.abs(np.sin((x * 0.09 + y * 0.14) + turb * 6.0))
vein = np.clip(1.0 - veins / 0.12, 0, 1) ** 1.5
base = np.array([238, 233, 224]) / 255.0
marble = base[None, None, :] * (0.94 + 0.06 * value_noise(H, HW, 12, 4)[..., None])
marble = marble * (1 - 0.26 * vein[..., None]) + np.array([150, 146, 150])[None, None, :] / 255.0 * 0.0
img[:, :HW] = marble

# gold: a warm gradient, brushed horizontally, with a few bright scratches
brush = value_noise(H, HW, 1, 5)[:, :1] * np.ones((1, HW))
grad = np.linspace(0.85, 1.05, H)[:, None]
gold = np.stack([np.full((H, HW), 0.95), np.full((H, HW), 0.72), np.full((H, HW), 0.26)], -1)
gold = gold * (grad * (0.88 + 0.18 * brush))[..., None]
spark = (rng.random((H, HW)) > 0.985) * 0.2
gold = gold + spark[..., None] * np.array([1, 0.9, 0.6])
img[:, HW:] = gold

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "woh", "textures", "entity", "stand.png")
Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(out)
print("wrote", out)
