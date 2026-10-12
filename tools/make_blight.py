"""Draws Antipode's blight: 16x16 block textures in the strike's colours (crimson-violet ground, glowing magenta veins,
teal moss, pale tendrils), and writes their blockstates, block and item models and loot tables.
Usage: make_blight.py <src/main/resources>"""
import json, os, sys
import numpy as np
from PIL import Image

RES = sys.argv[1]
A = os.path.join(RES, 'assets', 'orbital_arsenal')
D = os.path.join(RES, 'data', 'orbital_arsenal')
N = 16
rng = np.random.default_rng(66)

CRIMSON = np.array([120, 22, 62.0])
CRIMSON_HI = np.array([168, 44, 92.0])
VIOLET = np.array([62, 24, 70.0])
MAGENTA = np.array([255, 92, 186.0])
TEAL = np.array([48, 190, 178.0])
PALE = np.array([244, 214, 228.0])


def noise(scale, seed, size=N):
    r = np.random.default_rng(seed)
    g = r.random((size // scale + 2, size // scale + 2))
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((size + 2 * scale, size + 2 * scale), Image.BICUBIC)
    a = np.asarray(im, dtype=np.float64)[scale:scale + size, scale:scale + size] / 255
    return a


def mix(a, b, t):
    return a * (1 - t[..., None]) + b * t[..., None]


def veins(seed, density=3, size=N):
    """Thin branching lines, wrapping at the edges so the texture tiles."""
    r = np.random.default_rng(seed)
    m = np.zeros((size, size))
    for _ in range(density):
        x, y = r.random() * size, r.random() * size
        ang = r.random() * 6.28
        for _ in range(size * 2):
            m[int(y) % size, int(x) % size] = 1
            ang += (r.random() - 0.5) * 0.9
            x += np.cos(ang) * 0.7
            y += np.sin(ang) * 0.7
    return m


def save(img, name, alpha=None):
    os.makedirs(os.path.join(A, 'textures', 'block'), exist_ok=True)
    rgba = np.concatenate([np.clip(img, 0, 255), np.full(img.shape[:2] + (1,), 255.0) if alpha is None else alpha[..., None]], -1)
    Image.fromarray(rgba.astype(np.uint8), 'RGBA').save(os.path.join(A, 'textures', 'block', name + '.png'))


def flesh_base(seed):
    n = noise(4, seed) * 0.7 + noise(2, seed + 1) * 0.3
    c = mix(CRIMSON * 0.75, CRIMSON_HI, n)
    # lumps: lighter spots with a dark rim
    lump = np.clip((noise(3, seed + 2) - 0.62) * 4, 0, 1)
    return c * (1 + 0.18 * lump[..., None])


# ground
sod = flesh_base(1)
v = veins(11, 2)
sod = mix(sod, MAGENTA, v * 0.9)
teal = np.clip((noise(3, 12) - 0.72) * 5, 0, 1)
sod = mix(sod, TEAL * 0.8, teal)
save(sod, 'antipode_sod_top')

flesh = flesh_base(2) * 0.85
save(flesh, 'antipode_flesh')

side = flesh.copy()
yy, xx = np.mgrid[0:N, 0:N]
fringe = (yy < 3 + (noise(2, 21)[0] * 3).astype(int)[None, :]).astype(float)
side = mix(side, sod, fringe)
save(side, 'antipode_sod_side')

rock = mix(VIOLET * 0.7, VIOLET * 1.25, noise(3, 3) * 0.8 + noise(1, 4) * 0.2)
cr = veins(31, 2)
rock = mix(rock, VIOLET * 0.35, cr)
save(rock, 'antipode_rock')

vein_base = flesh_base(5) * 0.55
vv = np.clip(veins(41, 5) + veins(42, 3) * 0.6, 0, 1)
vein = mix(vein_base, MAGENTA, vv)
save(vein, 'antipode_vein')

moss = mix(TEAL * 0.45, TEAL * 0.95, noise(3, 6))
moss = mix(moss, CRIMSON, np.clip((noise(2, 7) - 0.7) * 4, 0, 1))
save(moss, 'antipode_moss')

# stalk: ridged violet bark; top: rings
bark = mix(VIOLET, CRIMSON * 0.9, 0.5 + 0.5 * np.sin(xx * 1.4 + noise(4, 8) * 3)[..., None].squeeze())
bark = mix(bark, MAGENTA * 0.8, veins(51, 1) * 0.6)
save(bark, 'antipode_stalk')
cy = np.hypot(xx - 7.5, yy - 7.5)
top = mix(CRIMSON * 0.6, CRIMSON_HI, 0.5 + 0.5 * np.sin(cy * 1.6))
top = mix(top, MAGENTA, (cy < 1.6).astype(float))
save(top, 'antipode_stalk_top')

# canopy: lumpy flesh with holes
can = flesh_base(9) * 1.05
can = mix(can, MAGENTA * 0.9, veins(61, 2) * 0.7)
holes = (noise(2, 62) < 0.22).astype(float)
save(can, 'antipode_canopy', alpha=(1 - holes) * 255)

# tendrils: pale curling strands on nothing
t = np.zeros((N, N))
for k in range(5):
    x = 2 + k * 3 + rng.random()
    for y in range(N - 1, 2 + rng.integers(0, 5), -1):
        x += np.sin(y * 0.8 + k) * 0.5
        t[y, int(np.clip(x, 0, N - 1))] = 1
tc = mix(np.zeros((N, N, 3)) + PALE * 0.8, MAGENTA, (yy < 6).astype(float) * t)
save(tc, 'antipode_tendrils', alpha=t * 255)

# bulb: a short stem and a glowing bulb
b = np.zeros((N, N))
bc = np.zeros((N, N, 3))
stem = (np.abs(xx - 7.5) < 1) & (yy > 8)
bulb = np.hypot(xx - 7.5, (yy - 6) * 1.2) < 4
b[stem | bulb] = 1
bc[stem] = VIOLET * 1.3
bc[bulb] = mix(MAGENTA[None, None, :] * np.ones((N, N, 3)), PALE[None, None, :] * np.ones((N, N, 3)),
               np.clip(1 - np.hypot(xx - 6.5, yy - 5) / 3.0, 0, 1))[bulb]
save(bc, 'antipode_bulb', alpha=b * 255)

# ---------------------------------------------------------------- json


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)


cubes = {'antipode_flesh': 'antipode_flesh', 'antipode_rock': 'antipode_rock', 'antipode_vein': 'antipode_vein', 'antipode_moss': 'antipode_moss'}
for name, tex in cubes.items():
    w(f'{A}/blockstates/{name}.json', {'variants': {'': {'model': f'orbital_arsenal:block/{name}'}}})
    w(f'{A}/models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'orbital_arsenal:block/{tex}'}})
w(f'{A}/blockstates/antipode_sod.json', {'variants': {'': [{'model': 'orbital_arsenal:block/antipode_sod'},
                                                             {'model': 'orbital_arsenal:block/antipode_sod', 'y': 90},
                                                             {'model': 'orbital_arsenal:block/antipode_sod', 'y': 180},
                                                             {'model': 'orbital_arsenal:block/antipode_sod', 'y': 270}]}})
w(f'{A}/models/block/antipode_sod.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
    'top': 'orbital_arsenal:block/antipode_sod_top', 'side': 'orbital_arsenal:block/antipode_sod_side', 'bottom': 'orbital_arsenal:block/antipode_flesh'}})
w(f'{A}/blockstates/antipode_stalk.json', {'variants': {
    'axis=y': {'model': 'orbital_arsenal:block/antipode_stalk'},
    'axis=z': {'model': 'orbital_arsenal:block/antipode_stalk', 'x': 90},
    'axis=x': {'model': 'orbital_arsenal:block/antipode_stalk', 'x': 90, 'y': 90}}})
w(f'{A}/models/block/antipode_stalk.json', {'parent': 'minecraft:block/cube_column', 'textures': {
    'end': 'orbital_arsenal:block/antipode_stalk_top', 'side': 'orbital_arsenal:block/antipode_stalk'}})
w(f'{A}/blockstates/antipode_canopy.json', {'variants': {'': {'model': 'orbital_arsenal:block/antipode_canopy'}}})
w(f'{A}/models/block/antipode_canopy.json', {'parent': 'minecraft:block/leaves', 'render_type': 'minecraft:cutout_mipped',
                                              'textures': {'all': 'orbital_arsenal:block/antipode_canopy'}})
for name in ('antipode_tendrils', 'antipode_bulb'):
    w(f'{A}/blockstates/{name}.json', {'variants': {'': {'model': f'orbital_arsenal:block/{name}'}}})
    w(f'{A}/models/block/{name}.json', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout',
                                         'textures': {'cross': f'orbital_arsenal:block/{name}'}})

blocks = list(cubes) + ['antipode_sod', 'antipode_stalk', 'antipode_canopy', 'antipode_tendrils', 'antipode_bulb']
for name in blocks:
    if name in ('antipode_tendrils', 'antipode_bulb'):
        w(f'{A}/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'orbital_arsenal:block/{name}'}})
    else:
        w(f'{A}/models/item/{name}.json', {'parent': f'orbital_arsenal:block/{name}'})
    w(f'{D}/loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
        'name': f'orbital_arsenal:{name}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
for egg in ('inverted', 'skitter', 'bloater', 'maw'):
    w(f'{A}/models/item/{egg}_spawn_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})
# the rock wants a pickaxe
w(os.path.join(RES, 'data', 'minecraft', 'tags', 'blocks', 'mineable', 'pickaxe.json'), {'replace': False, 'values': ['orbital_arsenal:antipode_rock']})
w(os.path.join(RES, 'data', 'minecraft', 'tags', 'blocks', 'mineable', 'hoe.json'), {'replace': False, 'values': [
    'orbital_arsenal:antipode_sod', 'orbital_arsenal:antipode_flesh', 'orbital_arsenal:antipode_vein', 'orbital_arsenal:antipode_moss',
    'orbital_arsenal:antipode_canopy']})
w(os.path.join(RES, 'data', 'minecraft', 'tags', 'blocks', 'mineable', 'axe.json'), {'replace': False, 'values': ['orbital_arsenal:antipode_stalk']})
print('blight ok')
