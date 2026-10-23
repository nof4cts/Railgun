"""The Stand's texture (64x32): left half white marble with soft grey veins, right half brushed gold."""
import os
import numpy as np
from PIL import Image

rng = np.random.default_rng(77)
W, H = 64, 32
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
y, x = np.mgrid[0:H, 0:32]
turb = value_noise(H, 32, 8, 1) * 1.0 + value_noise(H, 32, 4, 2) * 0.5 + value_noise(H, 32, 2, 3) * 0.25
veins = np.abs(np.sin((x * 0.35 + y * 0.55) + turb * 6.0))
vein = np.clip(1.0 - veins / 0.18, 0, 1) ** 1.5
base = np.array([238, 233, 224]) / 255.0
marble = base[None, None, :] * (0.94 + 0.06 * value_noise(H, 32, 3, 4)[..., None])
marble = marble * (1 - 0.32 * vein[..., None]) + np.array([150, 146, 150])[None, None, :] / 255.0 * 0.0
img[:, :32] = marble

# gold: a warm gradient, brushed horizontally, with a few bright scratches
brush = value_noise(H, 32, 1, 5)[:, :1] * np.ones((1, 32))
grad = np.linspace(0.85, 1.05, H)[:, None]
gold = np.stack([np.full((H, 32), 0.95), np.full((H, 32), 0.72), np.full((H, 32), 0.26)], -1)
gold = gold * (grad * (0.88 + 0.18 * brush))[..., None]
spark = (rng.random((H, 32)) > 0.97) * 0.25
gold = gold + spark[..., None] * np.array([1, 0.9, 0.6])
img[:, 32:] = gold

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "woh", "textures", "entity", "stand.png")
Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(out)
print("wrote", out)
