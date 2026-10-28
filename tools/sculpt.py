"""
Sculpts The Abomination as smooth organic meshes bound to the animation skeleton, paints a realistic
gore texture atlas, writes the binary mesh the mod loads at runtime, the Java skeleton code, and a preview.

usage: python3 sculpt.py <resources_assets_dir> <java_snippet_out> <preview_png>
Units are model pixels (1/16 block), +y is DOWN and -z is the creature's front, exactly like ModelPart.
"""
import math, struct, sys, os
import numpy as np
from PIL import Image

ASSETS, JAVA_OUT, PREVIEW = sys.argv[1], sys.argv[2], sys.argv[3]
rng = np.random.default_rng(1666)

# =============================================================================================== skeleton
# (name, parent, pivot, rest rotation). Pivots match the original animation rig so every animation still works.
SK = []
def bone(n, p, piv, rot=(0, 0, 0)): SK.append((n, p, piv, rot))
bone('root', None, (0, 24, 0))
bone('hips', 'root', (0, -26, 0))
bone('torso', 'hips', (0, -1, 0), (0.25, 0, 0))
bone('chest', 'torso', (0, -7, 0), (0.45, 0, 0))
bone('neck', 'chest', (0, -12, -1), (-0.2, 0, 0))
bone('head', 'neck', (0, -9, 0), (0.1, 0, 0))
bone('jaw', 'head', (0, -1, -1), (0.85, 0, 0))
bone('jaw_inner', 'head', (0, -1.5, -2))
EYES = [('eye_1', (-1.5, -4.6, -4.0), 0.5), ('eye_2', (1.7, -4.3, -4.1), 0.42), ('eye_3', (-2.5, -5.6, -2.4), 0.36),
        ('eye_4', (2.4, -5.9, -2.0), 0.3), ('eye_5', (0.3, -6.4, -2.8), 0.34), ('eye_6', (-0.6, -3.7, -5.5), 0.26)]
for n, piv, r in EYES: bone(n, 'head', piv)
prev = 'chest'
for i in range(8):
    bone(f'spine_{i+1}', prev, (0, -11.5, 3.2) if i == 0 else (0, -3, 0), (-0.5 if i == 0 else -0.28, 0, 0)); prev = f'spine_{i+1}'
for tn, piv, rot in [('tendril_a', (-2.5, -4, 3), (0.5, 0, 0.3)), ('tendril_b', (3, -8, 3), (0.9, 0, -0.4))]:
    prev = 'chest'
    for i in range(4):
        bone(f'{tn}{i+1}', prev, piv if i == 0 else (0, 5, 0), rot if i == 0 else (0.3, 0, 0)); prev = f'{tn}{i+1}'
bone('rshoulder', 'chest', (-5.5, -10.5, 0), (-0.6, 0, 0.12))
bone('rfore', 'rshoulder', (0, 14, 0), (-0.3, 0, 0))
bone('rhand', 'rfore', (0, 15, 0))
for i, px in enumerate([-1, 0, 1]):
    bone(f'rf{i+1}', 'rhand', (px, 1.8, -0.6 if px else -0.9), (-0.2, 0, 0))
    bone(f'rc{i+1}', f'rf{i+1}', (0, 6, 0), (0.3, 0, 0))
bone('lshoulder', 'chest', (5.5, -10.5, 0.5), (-0.6, 0, -0.1))
bone('lmid', 'lshoulder', (0, 12, 0), (0.2, 0, 0))
bone('lfore', 'lmid', (0, 9, 0), (-0.4, 0, 0))
bone('lhand', 'lfore', (0, 11, 0))
for i, px in enumerate([-1.2, -0.4, 0.4, 1.2]):
    bone(f'lf{i+1}', 'lhand', (px, 1.8, -0.9 if abs(px) < 1 else -0.6), (-0.15, 0, 0))
    bone(f'lc{i+1}', f'lf{i+1}', (0, 7, 0), (0.35, 0, 0))
bone('lthigh', 'root', (2.6, -26, 0), (-0.25, 0, -0.05))
bone('lshin', 'lthigh', (0, 13, 0), (0.5, 0, 0))
bone('lfoot', 'lshin', (0, 12, 0), (-0.25, 0, 0))
bone('rthigh', 'root', (-2.8, -26, 0.4), (-0.1, 0, 0.08))
bone('rshin', 'rthigh', (0, 13, 0), (0.35, 0, 0))
bone('rfoot', 'rshin', (0, 12, 0), (-0.25, 0, 0))
# new gore bones (animated sway)
bone('gut_1', 'torso', (0.6, -1.5, -2.2), (0.15, 0, 0.1))
bone('gut_2', 'gut_1', (0, 4, 0), (0.2, 0, -0.2))
bone('gut_3', 'gut_2', (0, 4, 0), (0.25, 0, 0.3))
bone('flap_1', 'chest', (-2.6, -1.2, -3.0), (0.1, 0, 0.1))
bone('flap_2', 'chest', (2.4, -1.6, -2.9), (0.15, 0, -0.1))
bone('flap_3', 'torso', (-2.0, -5.5, 2.0), (-0.2, 0, 0.15))
NAMES = [b[0] for b in SK]
HEAD_SCALE = 1.4  # applied at runtime via ModelPart scale so the maw dominates the silhouette

# =============================================================================================== materials / atlas
TILE, ATLAS = 256, 1024
MATS = ['flesh', 'skin', 'bone', 'teeth', 'gum', 'eye', 'claw', 'gut', 'cavity', 'sinew', 'drool', 'tumor', 'soaked', 'vein', 'mold']
def tile_of(m):
    i = MATS.index(m); return (i % 4) * TILE, (i // 4) * TILE

# ----- 3D value noise for displacement
_P = rng.integers(0, 256, 512)
def _h(ix, iy, iz): return (_P[(_P[(_P[ix & 255] + iy) & 255] + iz) & 255]) / 255.0
def vnoise(x, y, z):
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    sx, sy, sz = fx*fx*(3-2*fx), fy*fy*(3-2*fy), fz*fz*(3-2*fz)
    def L(a, b, t): return a + (b - a) * t
    c = [[[_h(ix+i, iy+j, iz+k) for k in (0, 1)] for j in (0, 1)] for i in (0, 1)]
    return L(L(L(c[0][0][0], c[0][0][1], sz), L(c[0][1][0], c[0][1][1], sz), sy),
             L(L(c[1][0][0], c[1][0][1], sz), L(c[1][1][0], c[1][1][1], sz), sy), sx) * 2 - 1
def fbm(p, o=3):
    a, f, s = 0.5, 1.0, 0.0
    for _ in range(o): s += a * vnoise(p[0]*f, p[1]*f, p[2]*f); a *= 0.5; f *= 2.03
    return s

# =============================================================================================== mesh building
MESH = {n: [] for n in NAMES}
PROPS = {}
def emit(target, P, N, UV, C, mat, closed_u=True):
    """P,N: (K,S,3) grids, UV (K,S,2) in 0..1 within tile, C (K,S) brightness. Emits quads."""
    tx, ty = tile_of(mat)
    K, S = P.shape[0], P.shape[1]
    for k in range(K - 1):
        for s in range(S - 1):
            quad = []
            for (a, b) in ((k, s), (k, s+1), (k+1, s+1), (k+1, s)):
                u = (tx + 2 + UV[a, b, 0] * (TILE - 4)) / ATLAS
                v = (ty + 2 + UV[a, b, 1] * (TILE - 4)) / ATLAS
                quad.append((*P[a, b], *N[a, b], u, v, C[a, b]))
            target.append((mat, quad))

def grid_normals(P, closed):
    du = np.roll(P, -1, axis=1) - np.roll(P, 1, axis=1) if closed else np.gradient(P, axis=1)
    dv = np.gradient(P, axis=0)
    N = np.cross(du, dv)
    L = np.linalg.norm(N, axis=2, keepdims=True); L[L < 1e-9] = 1
    return N / L

def finish(target, P, mat, noise_amp, nscale, closed=True, ao=True, dark_bottom=0.0):
    K, S = P.shape[:2]
    N0 = grid_normals(P, closed)
    if noise_amp > 0:
        D = np.array([[fbm(P[k, s] * nscale + 7.3) for s in range(S)] for k in range(K)])
        P = P + N0 * (D[..., None] * noise_amp)
        if closed: P[:, -1] = P[:, 0]
    else:
        D = np.zeros((K, S))
    N = grid_normals(P, closed)
    # orient outward-ish: flip if most normals point toward centroid
    c = P.reshape(-1, 3).mean(0)
    if np.mean(np.sum((P - c) * N, axis=2)) < 0: N = -N
    UV = np.zeros((K, S, 2))
    UV[..., 0] = np.linspace(0, 1, S)[None, :]
    UV[..., 1] = np.linspace(0, 1, K)[:, None]
    C = np.ones((K, S))
    if ao: C = np.clip(0.78 + D * 0.45, 0.45, 1.0)  # crevices darker
    if dark_bottom: C *= 1 - dark_bottom * UV[..., 1]
    emit(target, P, N, UV, C, mat)

def frames(path):
    path = np.asarray(path, float)
    T = np.gradient(path, axis=0); T /= np.linalg.norm(T, axis=1, keepdims=True)
    up = np.array([0, 0, 1.0]) if abs(T[0, 2]) < 0.9 else np.array([1.0, 0, 0])
    Nn = np.cross(T[0], up); Nn /= np.linalg.norm(Nn)
    Ns, Bs = [], []
    for i in range(len(path)):
        if i > 0:
            Nn = Nn - T[i] * np.dot(Nn, T[i]); Nn /= np.linalg.norm(Nn)
        Ns.append(Nn.copy()); Bs.append(np.cross(T[i], Nn))
    return path, np.array(Ns), np.array(Bs)

def tube(target, path, radii, mat, seg=12, noise=0.25, nscale=0.35, flat=1.0, cap=True, dark_bottom=0.0, veins=0):
    """Generalized cylinder along a polyline; radii per path point (scalar or (rx,ry)). Rounded caps by pinching."""
    path = list(map(np.array, path)); radii = list(radii)
    if cap:
        d0 = path[0] - path[1]; d0 = d0 / (np.linalg.norm(d0) + 1e-9)
        d1 = path[-1] - path[-2]; d1 = d1 / (np.linalg.norm(d1) + 1e-9)
        r0 = radii[0] if np.isscalar(radii[0]) else radii[0][0]
        r1 = radii[-1] if np.isscalar(radii[-1]) else radii[-1][0]
        path = [path[0] + d0 * r0 * 0.55, path[0] + d0 * r0 * 0.35] + path + [path[-1] + d1 * r1 * 0.35, path[-1] + d1 * r1 * 0.55]
        def sc(r, f): return r * f if np.isscalar(r) else (r[0] * f, r[1] * f)
        radii = [sc(radii[0], 0.02), sc(radii[0], 0.75)] + radii + [sc(radii[-1], 0.75), sc(radii[-1], 0.02)]
    pth, Ns, Bs = frames(path)
    K = len(pth)
    P = np.zeros((K, seg + 1, 3))
    for k in range(K):
        r = radii[k]
        rx, ry = (r, r * flat) if np.isscalar(r) else r
        for s in range(seg + 1):
            a = 2 * math.pi * s / seg
            P[k, s] = pth[k] + Ns[k] * math.cos(a) * rx + Bs[k] * math.sin(a) * ry
    finish(target, P, mat, noise, nscale, dark_bottom=dark_bottom)
    for v in range(veins):   # bulging veins snaking over the surface
        a0, ph, amp = rng.random() * 2 * math.pi, rng.random() * 6, 0.5 + rng.random() * 0.8
        k0, k1 = (2, K - 3) if cap else (0, K - 1)
        pts, rads = [], []
        for k in range(k0, k1 + 1):
            r = radii[k]; rx, ry = (r, r * flat) if np.isscalar(r) else r
            a = a0 + amp * math.sin(k * 0.8 + ph)
            pts.append(pth[k] + Ns[k] * math.cos(a) * rx * 1.02 + Bs[k] * math.sin(a) * ry * 1.02)
            rads.append(0.11 + 0.1 * rng.random())
        if len(pts) > 2:
            tube(target, pts, rads, 'vein', seg=5, noise=0.02, cap=True)
            if rng.random() < 0.6:   # branch
                j = len(pts) // 2
                br = [pts[j] + (pts[min(j + 2, len(pts) - 1)] - pts[j]) * t + (Ns[k0 + j] * 0.4 * t) for t in np.linspace(0, 1, 4)]
                tube(target, br, [0.1, 0.09, 0.07, 0.05], 'vein', seg=4, noise=0.0, cap=True)

def surface_bits(target, c, r, count, mat, size=(0.25, 0.6), flatten=1.0, lat_range=(-1.2, 1.2), lon_range=(0, 2 * math.pi), lift=0.95):
    """Boils, mould colonies and sores stuck onto an ellipsoid's surface."""
    for _ in range(count):
        la = lat_range[0] + rng.random() * (lat_range[1] - lat_range[0]); lo = lon_range[0] + rng.random() * (lon_range[1] - lon_range[0])
        n = np.array([math.cos(la) * math.cos(lo), math.sin(la), math.cos(la) * math.sin(lo)])
        pos = np.array(c) + n * np.array(r) * lift
        sz = size[0] + rng.random() * (size[1] - size[0])
        ellipsoid(target, tuple(pos), (sz, sz * flatten, sz), mat, seg=8, rings=6, noise=0.06 if mat != 'mold' else 0.12, nscale=1.5)

def surface_veins(target, c, r, count):
    for _ in range(count):
        la0, lo0 = (rng.random() - 0.5) * 2.4, rng.random() * 2 * math.pi
        dla, dlo = (rng.random() - 0.5) * 1.2, (rng.random() - 0.5) * 1.6
        pts = []
        for t in np.linspace(0, 1, 7):
            la = la0 + dla * t + 0.12 * math.sin(t * 9); lo = lo0 + dlo * t
            n = np.array([math.cos(la) * math.cos(lo), math.sin(la), math.cos(la) * math.sin(lo)])
            pts.append(np.array(c) + n * np.array(r) * 1.01)
        tube(target, pts, [0.1, 0.15, 0.17, 0.16, 0.14, 0.11, 0.07], 'vein', seg=5, noise=0.02)

def ellipsoid(target, c, r, mat, seg=16, rings=12, noise=0.3, nscale=0.4, lon=(0, 2*math.pi), lat=(-math.pi/2, math.pi/2)):
    P = np.zeros((rings + 1, seg + 1, 3))
    for j in range(rings + 1):
        la = lat[0] + (lat[1] - lat[0]) * j / rings
        for i in range(seg + 1):
            lo = lon[0] + (lon[1] - lon[0]) * i / seg
            P[j, i] = (c[0] + r[0] * math.cos(la) * math.cos(lo), c[1] + r[1] * math.sin(la), c[2] + r[2] * math.cos(la) * math.sin(lo))
    finish(target, P, mat, noise, nscale, closed=(lon[1] - lon[0]) > 6.2)

def lerp_path(a, b, n, bend=(0, 0, 0)):
    a, b, bend = np.array(a, float), np.array(b, float), np.array(bend, float)
    return [a + (b - a) * t + bend * math.sin(math.pi * t) for t in np.linspace(0, 1, n)]

def fang(target, base, direction, length, r, mat='teeth', curve=(0, 0, 0), seg=6):
    base, d = np.array(base, float), np.array(direction, float); d /= np.linalg.norm(d)
    curve = np.array(curve, float)
    pts = [base + d * length * t + curve * t * t for t in np.linspace(0, 1, 6)]
    rad = [r * (1 - t) ** 0.8 + 0.03 for t in np.linspace(0, 1, 6)]
    tube(target, pts, rad, mat, seg=seg, noise=0.0, cap=False)

def ribbon(target, path, widths, mat, normal_hint=(0, 0, -1), noise=0.15):
    """Hanging strip of torn skin. Two-sided (render type is no-cull)."""
    path = np.array(path, float); n = np.array(normal_hint, float)
    K = len(path); P = np.zeros((K, 3, 3))
    for k in range(K):
        t = path[min(k + 1, K - 1)] - path[max(k - 1, 0)]; t /= np.linalg.norm(t)
        side = np.cross(t, n); side /= np.linalg.norm(side) + 1e-9
        w = widths[k]
        jag = (rng.random() - 0.5) * 0.4 * (k / K)
        for s, f in enumerate((-1, 0, 1)):
            P[k, s] = path[k] + side * w * f * 0.5 + n * (0.15 * (1 - abs(f))) + side * jag
    finish(target, P, mat, noise, 0.6, closed=False, ao=True)

def arc(cx, cz, rx, rz, y, a0, a1, n):
    return [(cx + rx * math.sin(a), y, cz - rz * math.cos(a)) for a in np.linspace(a0, a1, n)]

# ------------------------------------------------------------------ body
M = MESH
# pelvis + iliac bone wings
ellipsoid(M['hips'], (0, 1.0, 0.3), (3.6, 2.3, 2.7), 'skin', noise=0.35)
surface_veins(M['hips'], (0, 1.0, 0.3), (3.6, 2.3, 2.7), 5)
surface_bits(M['hips'], (0, 1.0, 0.3), (3.6, 2.3, 2.7), 6, 'mold', (0.4, 0.9), flatten=0.4)
surface_bits(M['hips'], (0, 1.0, 0.3), (3.6, 2.3, 2.7), 6, 'tumor', (0.2, 0.45))
for sx in (-1, 1):
    tube(M['hips'], lerp_path((sx*1.5, -0.5, 1.8), (sx*3.6, 1.2, 0.2), 6, (0, -0.6, 0.4)), [0.6, 0.7, 0.6, 0.5, 0.45, 0.4], 'bone', seg=8, noise=0.08)
# waist: emaciated sinew column, exposed vertebrae on the back, torn open abdomen
tube(M['torso'], lerp_path((0, 0.5, 0), (0, -7.5, 0), 8, (0, 0, -0.3)), [(2.6, 2.0), (2.2, 1.8), (2.0, 1.6), (2.1, 1.7), (2.4, 1.9), (2.7, 2.1), (3.0, 2.3), (3.2, 2.4)], 'sinew', seg=14, noise=0.3, veins=4)
ellipsoid(M['torso'], (0, -3.2, -1.2), (1.9, 2.6, 1.1), 'cavity', noise=0.25)
for i in range(5):
    ellipsoid(M['torso'], (0, -0.5 - i * 1.5, 2.0), (0.8, 0.6, 0.7), 'bone', seg=8, rings=6, noise=0.05)
# chest: exposed ribcage around a wet visceral cavity, muscle mass over the shoulders, flayed back
ellipsoid(M['chest'], (0, -6.5, 0.4), (3.9, 5.4, 2.5), 'cavity', noise=0.35)
for k in range(4):  # coiled viscera inside the cage
    y = -3.0 - k * 2.1
    tube(M['chest'], arc(0, 0.2, 2.6, 1.6, y, -1.2, 1.2, 9), [0.55] * 9, 'gut', seg=8, noise=0.12)
ellipsoid(M['chest'], (0, -10.6, 1.0), (6.0, 2.8, 3.0), 'flesh', noise=0.4)
ellipsoid(M['chest'], (0, -6.5, 1.9), (5.2, 6.2, 2.0), 'skin', noise=0.45, lon=(0.15, math.pi - 0.15))
for i in range(7):
    ellipsoid(M['chest'], (0, -0.5 - i * 1.7, 2.9), (0.9, 0.7, 0.8), 'bone', seg=8, rings=6, noise=0.05)
for lvl, y in enumerate([-2.0, -3.8, -5.6, -7.4, -9.2, -10.8]):
    for sx in (-1, 1):
        w = 4.6 + 0.6 * math.sin(lvl / 5 * math.pi)
        torn = 0.35 + 0.25 * rng.random()   # broken short of the sternum
        pts = [(sx * w * math.sin(a), y + 0.4 * math.sin(a), 0.4 - 3.0 * math.cos(a)) for a in np.linspace(math.pi * 0.95, torn, 9)]
        tube(M['chest'], pts, [0.42] * 9, 'bone', seg=7, noise=0.06)
ellipsoid(M['chest'], (-5.2, -11.2, 0.0), (1.8, 1.4, 1.5), 'tumor', noise=0.5)
surface_veins(M['chest'], (0, -10.6, 1.0), (6.0, 2.8, 3.0), 10)
surface_veins(M['chest'], (0, -6.5, 1.9), (5.2, 6.2, 2.0), 8)
surface_bits(M['chest'], (0, -10.6, 1.0), (6.0, 2.8, 3.0), 14, 'tumor', (0.25, 0.55))
surface_bits(M['chest'], (0, -6.5, 1.9), (5.2, 6.2, 2.0), 10, 'mold', (0.5, 1.3), flatten=0.4, lon_range=(0.3, math.pi - 0.3))
surface_bits(M['chest'], (0, -10.6, 1.0), (6.0, 2.8, 3.0), 6, 'mold', (0.6, 1.2), flatten=0.4)
ellipsoid(M['chest'], (-4.4, -9.7, -1.2), (1.0, 0.9, 0.9), 'tumor', noise=0.4)
ellipsoid(M['chest'], (3.4, -4.2, -2.6), (1.2, 1.0, 0.8), 'tumor', noise=0.4)
ellipsoid(M['chest'], (5.0, -11.0, 0.8), (1.6, 1.5, 1.7), 'flesh', noise=0.35)   # shoulder caps
ellipsoid(M['chest'], (-5.0, -11.0, 0.2), (1.6, 1.5, 1.7), 'flesh', noise=0.35)
# long neck with tendons
tube(M['neck'], lerp_path((0, 0.5, 0), (0, -9.2, 0), 9, (0, 0, 0.4)), [1.9, 1.5, 1.3, 1.25, 1.2, 1.2, 1.25, 1.35, 1.5], 'sinew', seg=12, noise=0.2, veins=4)
for sx in (-0.9, 0.9):
    tube(M['neck'], lerp_path((sx, 0, -0.8), (sx * 0.8, -8.8, -0.9), 7, (sx * 0.4, 0, -0.3)), [0.3] * 7, 'flesh', seg=6, noise=0.05)
for i in range(6):
    ellipsoid(M['neck'], (0, -0.6 - i * 1.5, 1.2), (0.55, 0.5, 0.6), 'bone', seg=8, rings=6, noise=0.04)

# ------------------------------------------------------------------ head: a gaping maw of fangs
H = M['head']
ellipsoid(H, (0, -4.0, 0.4), (3.3, 3.0, 4.4), 'skin', noise=0.4)                     # elongated cranium
surface_veins(H, (0, -4.0, 0.4), (3.3, 3.0, 4.4), 12)
surface_bits(H, (0, -4.0, 0.4), (3.3, 3.0, 4.4), 9, 'tumor', (0.15, 0.4), lon_range=(0.2, 3.0))
surface_bits(H, (0, -4.0, 0.4), (3.3, 3.0, 4.4), 6, 'mold', (0.4, 0.9), flatten=0.4, lon_range=(0.4, 2.7), lat_range=(-1.3, -0.1))
ellipsoid(H, (0, -2.6, -3.4), (2.4, 1.6, 3.6), 'skin', noise=0.45)                   # long skull snout
ellipsoid(H, (0, -1.9, -3.0), (2.3, 0.6, 2.4), 'gum', noise=0.15)                    # palate
ellipsoid(H, (0, -0.6, -1.4), (2.3, 2.4, 2.4), 'cavity', noise=0.2)                  # black wet throat
ellipsoid(H, (-1.2, -6.6, 1.2), (1.6, 0.8, 2.6), 'bone', noise=0.35)   # cracked skull plate breaking through
fang(H, (-2.6, -6.4, 2.6), (-0.6, -1, 0.9), 2.2, 0.42, 'bone', curve=(0, 0, 0.6))  # broken horn stumps
fang(H, (2.4, -6.8, 3.0), (0.4, -1, 1.0), 4.2, 0.4, 'bone', curve=(0.4, 0.4, 1.6))
gum = arc(0, -0.8, 2.7, 5.9, -0.9, -1.45, 1.45, 15)
tube(H, gum, [0.38] * 15, 'cavity', seg=8, noise=0.15, cap=True)
for i, (x, y, z) in enumerate(arc(0, -0.8, 2.75, 6.0, -0.9, -1.4, 1.4, 11)):   # outer row: uneven, some snapped off
    L = (1.6 + 4.2 * rng.random() ** 0.7) * (0.45 if rng.random() < 0.2 else 1.0)
    tilt = (rng.random() - 0.5) * 0.5
    fang(H, (x, y, z), (-x * 0.06 + tilt, 1, -0.25 + tilt * 0.3), L, 0.33 + rng.random() * 0.2, curve=(-x * 0.08 + tilt, 0, 0.5 * rng.random()))
for i, (x, y, z) in enumerate(arc(0, -0.6, 2.0, 4.6, -1.1, -1.3, 1.3, 11)):   # inner row
    fang(H, (x, y, z), (0, 1, 0.1), 1.2 + rng.random() * 0.8, 0.26, curve=(0, 0, 0.3))
for (x, y, z) in arc(0, -0.8, 2.6, 5.7, -0.7, -1.2, 1.2, 7):                  # drool / blood strands
    L = 3.2 + rng.random() * 2.5
    tube(H, lerp_path((x, y + 0.2, z), (x * 0.95, y + L, z + 0.1), 6, (0, 0, 0.25 * (rng.random() - 0.5))), [0.16, 0.12, 0.1, 0.1, 0.13, 0.2], 'drool', seg=5, noise=0.0)
for n, piv, r in EYES:   # independently animated eyes, sunk into wet raw sockets
    ellipsoid(M[n], (0, 0, 0), (r, r, r), 'eye', seg=14, rings=10, noise=0.0)
    tube(M[n], [(r * 1.15 * math.cos(a), r * 1.15 * math.sin(a), -r * 0.15) for a in np.linspace(0, 2 * math.pi, 13)], [r * 0.35] * 13, 'cavity', seg=6, noise=0.05, cap=False)
for k in range(9):   # extra pinprick eyes crowding the brow, asymmetric
    x, y, z = (rng.random() - 0.5) * 4.6, -4.3 - rng.random() * 2.4, -4.6 + rng.random() * 2.2
    ellipsoid(H, (x, y, z), (0.18 + rng.random() * 0.15,) * 3, 'eye', seg=8, rings=6, noise=0.0)
# lower jaw (pivot already at mouth hinge)
J = M['jaw']
tube(J, arc(0, -0.2, 2.7, 6.2, 1.1, -1.5, 1.5, 15), [(0.5, 0.75)] * 15, 'soaked', seg=10, noise=0.25)
ellipsoid(J, (0, 0.9, -3.2), (2.2, 0.7, 2.8), 'gum', noise=0.12)
for i, (x, y, z) in enumerate(arc(0, -0.2, 2.6, 6.0, 0.2, -1.4, 1.4, 15)):
    L = 2.6 + 1.8 * (1 - abs(i - 7) / 7) + rng.random() * 1.0
    fang(J, (x, y, z), (-x * 0.05, -1, -0.1), L, 0.33 + rng.random() * 0.08, curve=(-x * 0.05, 0, 0.3))
tube(J, lerp_path((0, 0.4, -1.0), (0, 2.4, -6.8), 8, (0, -0.8, 0)), [0.8, 0.85, 0.8, 0.75, 0.7, 0.6, 0.5, 0.35], 'gum', seg=10, noise=0.12, flat=0.45)   # tongue lolling out
I = M['jaw_inner']
tube(I, arc(0, 0.2, 1.4, 3.6, 0, -1.2, 1.2, 9), [0.4] * 9, 'sinew', seg=7, noise=0.05)
for (x, y, z) in arc(0, 0.2, 1.4, 3.7, -0.3, -1.1, 1.1, 7):
    fang(I, (x, y, z), (0, -0.3, -1), 1.1, 0.22, curve=(0, -0.2, 0))

# ------------------------------------------------------------------ external spine
for i in range(8):
    w = 1.15 * (1 - 0.07 * i)
    S = M[f'spine_{i+1}']
    ellipsoid(S, (0, -1.5, 0), (w, 1.3, w), 'bone', seg=10, rings=8, noise=0.08)
    tube(S, lerp_path((0, 0, 0), (0, -3, 0), 4), [w * 0.6] * 4, 'sinew', seg=8, noise=0.1)
    fang(S, (0, -1.6, w * 0.7), (0, -0.4, 1), 3.2 - 0.25 * i, 0.5 * (1 - 0.05 * i), 'bone', curve=(0, -0.8, 0.2))
    for sx in (-1, 1):
        fang(S, (sx * w * 0.8, -1.5, 0), (sx, 0.1, 0.3), 1.2, 0.25, 'bone')
# tendrils: raw muscle tentacles with hooked tips
for tn in ('tendril_a', 'tendril_b'):
    for i in range(4):
        r0, r1 = 0.75 - 0.15 * i, 0.6 - 0.15 * i
        tube(M[f'{tn}{i+1}'], lerp_path((0, -0.3, 0), (0, 5.3, 0), 6, (0.2, 0, 0.2)), np.linspace(r0, max(r1, 0.15), 6), 'flesh' if i < 2 else 'sinew', seg=9, noise=0.15, veins=2 if i < 2 else 1)
        if i == 3: fang(M[f'{tn}{i+1}'], (0, 5, 0), (0, 1, -0.3), 2.2, 0.28, 'claw', curve=(0, 0, -1.2))

# ------------------------------------------------------------------ arms
def upper_arm(B, length, rprof):
    tube(B, lerp_path((0, -1.2, 0), (0, length, 0), len(rprof), (0, 0, -0.4)), rprof, 'flesh', seg=12, noise=0.35, dark_bottom=0.2, veins=5)
    tube(B, lerp_path((0.6, 0.5, -1.2), (0.4, length - 1, -1.1), 6, (0.4, 0, -0.5)), [0.35] * 6, 'sinew', seg=6, noise=0.05)
upper_arm(M['rshoulder'], 14.5, [1.9, 2.3, 2.1, 1.7, 1.3, 1.25, 1.4])
fang(M['rshoulder'], (-1.2, -0.8, 0.6), (-0.6, -1, 0.5), 3.0, 0.5, 'bone', curve=(0, 0, 0.6))
tube(M['rfore'], lerp_path((0, -0.3, 0), (0, 15.3, 0), 8, (0, 0, 0.5)), [1.2, 1.1, 0.95, 0.8, 0.75, 0.75, 0.8, 0.9], 'sinew', seg=10, noise=0.2, dark_bottom=0.35, veins=3)
tube(M['rfore'], lerp_path((0, 0.5, 0.9), (0, 13.5, 0.7), 6, (0, 0, 0.4)), [0.3] * 6, 'bone', seg=6, noise=0.03)
fang(M['rfore'], (0, 0.4, 1.0), (0, -0.2, 1), 2.4, 0.42, 'bone', curve=(0, -0.6, 0))  # elbow spike
ellipsoid(M['rhand'], (0, 1.0, -0.3), (1.6, 1.3, 1.0), 'soaked', noise=0.2)
tube(M['lshoulder'], lerp_path((0, -1.2, 0), (0, 12.3, 0), 7, (0, 0, -0.3)), [1.9, 2.2, 1.9, 1.6, 1.3, 1.25, 1.35], 'flesh', seg=12, noise=0.35, dark_bottom=0.2, veins=5)
ellipsoid(M['lshoulder'], (1.4, -1.0, -0.2), (1.7, 1.6, 1.7), 'tumor', noise=0.5)
tube(M['lmid'], lerp_path((0, -0.2, 0), (0, 9.2, 0), 6, (0, 0, 0.3)), [1.25, 1.05, 0.95, 0.95, 1.0, 1.1], 'sinew', seg=10, noise=0.2, veins=3)
ellipsoid(M['lmid'], (0, 0, 0.3), (1.2, 1.2, 1.3), 'bone', seg=10, rings=8, noise=0.1)
fang(M['lmid'], (0, 0.2, 1.0), (0, -0.3, 1), 2.0, 0.38, 'bone', curve=(0, -0.5, 0))
tube(M['lfore'], lerp_path((0, -0.2, 0), (0, 11.3, 0), 7, (0, 0, 0.4)), [1.05, 0.95, 0.8, 0.72, 0.72, 0.8, 0.9], 'sinew', seg=10, noise=0.2, dark_bottom=0.4, veins=3)
ellipsoid(M['lhand'], (0, 1.0, -0.3), (1.7, 1.3, 1.0), 'soaked', noise=0.2)
for f in ('rf1', 'rf2', 'rf3', 'lf1', 'lf2', 'lf3', 'lf4'):
    L = 6.2 if f.startswith('r') else 7.2
    tube(M[f], lerp_path((0, -0.2, 0), (0, L, 0), 7), [0.45, 0.42, 0.36, 0.4, 0.34, 0.32, 0.36], 'skin', seg=8, noise=0.05, dark_bottom=0.5)
    for k in (0.33, 0.66):
        ellipsoid(M[f], (0, L * k, 0), (0.48, 0.42, 0.48), 'bone', seg=8, rings=6, noise=0.0)
for c in ('rc1', 'rc2', 'rc3', 'lc1', 'lc2', 'lc3', 'lc4'):
    L = 4.2 if c.startswith('r') else 5.2
    fang(M[c], (0, -0.2, 0), (0, 1, -0.15), L, 0.42, 'claw', curve=(0, 0, -1.4), seg=7)
# ------------------------------------------------------------------ legs
for side in ('l', 'r'):
    tube(M[f'{side}thigh'], lerp_path((0, -0.6, 0), (0, 13.6, 0), 7, (0, 0, -0.5)), [2.0, 2.3, 2.1, 1.8, 1.4, 1.2, 1.25], 'flesh', seg=12, noise=0.35, veins=5)
    fang(M[f'{side}thigh'], (0, 12.5, -1.0), (0, 0.2, -1), 2.0, 0.45, 'bone', curve=(0, 0.6, 0))
    tube(M[f'{side}shin'], lerp_path((0, -0.4, 0), (0, 12.2, 0), 7, (0, 0, 0.6)), [1.2, 1.0, 0.85, 0.75, 0.7, 0.75, 0.85], 'sinew', seg=10, noise=0.2, dark_bottom=0.45, veins=3)
    tube(M[f'{side}shin'], lerp_path((0, 0.5, 0.9), (0, 11.0, 0.8), 6, (0, 0, 0.4)), [0.32] * 6, 'bone', seg=6, noise=0.03)
    tube(M[f'{side}foot'], lerp_path((0, 0.3, 0.6), (0, 0.8, -3.6), 6), [(0.9, 0.6), (1.1, 0.7), (1.0, 0.6), (0.9, 0.55), (0.8, 0.5), (0.7, 0.45)], 'soaked', seg=10, noise=0.15)
    for tx in (-0.8, 0, 0.8):
        fang(M[f'{side}foot'], (tx, 0.9, -3.5), (tx * 0.3, 0.3, -1), 2.2, 0.3, 'claw', curve=(0, 0.8, 0))
# ------------------------------------------------------------------ hanging gore
for i in range(3):
    pts = [(math.sin(t * 2.2 + i) * 0.7, t * 4.3, -0.4 + math.cos(t * 3.0 + i) * 0.5) for t in np.linspace(0, 1, 7)]
    tube(M[f'gut_{i+1}'], pts, [0.62, 0.65, 0.6, 0.66, 0.6, 0.62, 0.58 if i < 2 else 0.3], 'gut', seg=10, noise=0.1)
ribbon(M['flap_1'], [(0, t * 5.0, -0.1 * t) for t in np.linspace(0, 1, 7)], [2.4, 2.2, 2.0, 1.7, 1.3, 0.9, 0.3], 'skin')
ribbon(M['flap_2'], [(0, t * 4.2, -0.2 * t) for t in np.linspace(0, 1, 6)], [2.0, 1.9, 1.6, 1.2, 0.8, 0.2], 'skin')
ribbon(M['flap_3'], [(0, t * 4.6, 0.3 * t) for t in np.linspace(0, 1, 6)], [2.6, 2.3, 1.9, 1.4, 0.9, 0.3], 'skin', normal_hint=(0, 0, 1))

# ------------------------------------------------------------------ props for the finisher
PROPS['prop_vertebra'] = []
V = PROPS['prop_vertebra']
ellipsoid(V, (0, 0, 0), (1.5, 1.0, 1.4), 'bone', seg=10, rings=8, noise=0.05)
fang(V, (0, 0, 1.0), (0, 0.2, 1), 2.4, 0.4, 'bone')
for sx in (-1, 1): fang(V, (sx * 1.2, 0, 0), (sx, 0, 0.2), 1.5, 0.3, 'bone')
ellipsoid(V, (0, 1.15, 0), (1.2, 0.4, 1.2), 'soaked', seg=10, rings=6, noise=0.1)
PROPS['prop_neckgore'] = []
G = PROPS['prop_neckgore']
ellipsoid(G, (0, 1.2, 0), (2.6, 1.5, 2.6), 'soaked', noise=0.45)
for i in range(5):
    a = i / 5 * 2 * math.pi
    tube(G, lerp_path((math.cos(a) * 1.6, 1.8, math.sin(a) * 1.6), (math.cos(a) * 2.0, 4.2 + rng.random() * 1.5, math.sin(a) * 2.0), 5), [0.35, 0.3, 0.25, 0.2, 0.15], 'flesh', seg=6, noise=0.05)
PROPS['prop_stump'] = []
ST = PROPS['prop_stump']
ellipsoid(ST, (0, -0.2, 0), (3.0, 1.0, 2.0), 'soaked', noise=0.4)
ellipsoid(ST, (0, -0.8, 0), (1.0, 0.6, 1.0), 'cavity', noise=0.1)
fang(ST, (0, -0.6, 0.6), (0, -1, 0.1), 1.3, 0.45, 'bone')

# =============================================================================================== texture painting
def vnoise2(h, w, cells, seed):
    r = np.random.default_rng(seed).random((cells + 1, cells + 1))
    ys, xs = np.linspace(0, cells, h, endpoint=False), np.linspace(0, cells, w, endpoint=False)
    yi, xi = ys.astype(int), xs.astype(int); fy, fx = (ys - yi)[:, None], (xs - xi)[None, :]
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a, b = r[yi][:, xi], r[yi][:, xi + 1]; c, d = r[yi + 1][:, xi], r[yi + 1][:, xi + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
def fbm2(h, w, base, seed, oct=5):
    s, a, tot = np.zeros((h, w)), 1.0, 0
    for o in range(oct): s += a * vnoise2(h, w, base * 2 ** o, seed + o); tot += a; a *= 0.5
    return s / tot
T = TILE
yy, xx = np.mgrid[0:T, 0:T]
def drips(img, count, seed, col=(70, 0, 4), maxlen=0.9):
    r = np.random.default_rng(seed)
    for _ in range(count):
        x0, y0 = r.integers(0, T), r.integers(0, T // 2)
        L = int(r.integers(20, int(T * maxlen))); wdt = r.uniform(1.0, 3.2)
        for y in range(y0, min(T, y0 + L)):
            f = (y - y0) / L; xc = x0 + math.sin(y * 0.07 + x0) * 2
            mask = np.abs(xx[y] - xc) < wdt * (1 - 0.5 * f)
            a = 0.85 * (1 - f * 0.4)
            img[y][mask] = img[y][mask] * (1 - a) + np.array(col) * a
        yb = min(T - 1, y0 + L); m = (xx - x0) ** 2 + (yy - yb) ** 2 < (wdt * 1.6) ** 2
        img[m] = img[m] * 0.15 + np.array(col) * 0.85
def gloss(img, seed, amount=0.06, col=(255, 235, 230)):
    r = np.random.default_rng(seed); n = fbm2(T, T, 8, seed, 3)
    spots = (r.random((T, T)) > 1 - amount) & (n > 0.55)
    img[spots] = img[spots] * 0.3 + np.array(col) * 0.7
def wounds(img, count, seed):
    r = np.random.default_rng(seed)
    for _ in range(count):
        cx, cy, rad = r.integers(0, T), r.integers(0, T), r.uniform(5, 16)
        d = np.sqrt((xx - cx) ** 2 + ((yy - cy) * 1.4) ** 2) / rad
        img[d < 1.25] = img[d < 1.25] * 0.4 + np.array([200, 170, 60]) * 0.6   # pus rim
        img[d < 1.0] = np.array([90, 4, 8]) * (0.6 + 0.4 * d[d < 1.0, None])    # open sore
        img[d < 0.4] = np.array([25, 0, 2])
def fibers(h, w, seed, freq=0.9):
    n = fbm2(h, w, 6, seed, 4)
    return 0.5 + 0.5 * np.sin(xx * freq + n * 9)

tiles = {}
n1, n2 = fbm2(T, T, 4, 1), fbm2(T, T, 16, 2)
f = fibers(T, T, 3)
img = np.stack([62 + 70 * f * n1, 10 + 16 * f * n1, 12 + 12 * n2], -1)          # raw muscle with fibre striation
bruise = fbm2(T, T, 5, 4) > 0.62; img[bruise] = img[bruise] * 0.45 + np.array([50, 10, 45]) * 0.55
drips(img, 30, 5, (35, 0, 2)); gloss(img, 6, 0.08, (230, 170, 160)); wounds(img, 6, 7)
tiles['flesh'] = img
n = fbm2(T, T, 5, 10)
img = np.stack([62 + 40 * n, 56 + 32 * n, 46 + 24 * n], -1)               # necrotic grey-green flayed skin
veins = np.abs(fbm2(T, T, 10, 11) - 0.5) < 0.022; img[veins] = [40, 18, 40]
rot = fbm2(T, T, 6, 12) > 0.55; img[rot] = img[rot] * 0.4 + np.array([45, 40, 18]) * 0.6
tear = fbm2(T, T, 3, 13) > 0.52; img[tear] = np.array([85, 6, 8]) * (0.6 + 0.4 * fbm2(T, T, 20, 14)[tear, None])
drips(img, 70, 15, (45, 0, 3)); drips(img, 30, 18, (90, 4, 6), 0.5); wounds(img, 12, 16); gloss(img, 17, 0.04, (200, 190, 170))
tiles['skin'] = img
n = fbm2(T, T, 8, 20)
img = np.stack([150 + 40 * n, 135 + 35 * n, 100 + 30 * n], -1)              # dirty yellowed bone
cracks = np.abs(fbm2(T, T, 12, 21) - 0.5) < 0.012; img[cracks] = [60, 45, 30]
stain = fbm2(T, T, 4, 22) > 0.5; img[stain] = img[stain] * 0.35 + np.array([70, 5, 6]) * 0.65
drips(img, 22, 23, (40, 0, 2)); tiles['bone'] = img
v = yy / T                                                                   # teeth: blood soaked at the root
img = np.stack([185 + 30 * n1, 170 + 30 * n1, 120 + 30 * n1], -1)
img = img * (1 - np.clip(1 - v * 1.6, 0, 1)[..., None] * 0.85) + np.array([75, 3, 5]) * np.clip(1 - v * 1.6, 0, 1)[..., None] * 0.85
img[(v > 0.85)] = img[(v > 0.85)] * 0.6 + np.array([80, 60, 40]) * 0.4
drips(img, 10, 24, maxlen=0.6); tiles['teeth'] = img
n = fbm2(T, T, 6, 30)
img = np.stack([105 + 55 * n, 15 + 20 * n, 25 + 20 * n], -1); gloss(img, 31, 0.14, (240, 200, 200)); tiles['gum'] = img
img = np.zeros((T, T, 3)); lat = yy / T                                      # eye: v=lat (0=front pole)
img[:] = np.stack([175 + 30 * n2, 170 + 30 * n2, 150 + 20 * n2], -1)
eu = np.minimum(xx / T, 1 - xx / T)
vein = ((np.abs(fbm2(T, T, 14, 32) - 0.5) < 0.012) | (np.abs(fbm2(T, T, 22, 33) - 0.5) < 0.008)) & (eu > 0.06); img[vein] = [150, 10, 10]
iris = (np.abs(lat - 0.5) < 0.09) & (eu < 0.05); img[iris] = np.stack([150 + 40 * n1, 140 + 30 * n1, 110 + 20 * n1], -1)[iris]
pupil = (np.abs(lat - 0.5) < 0.03) & (eu < 0.012); img[pupil] = [5, 0, 0]
tiles['eye'] = img
n = fbm2(T, T, 10, 40)
img = np.stack([22 + 25 * n, 12 + 10 * n, 14 + 10 * n], -1); gloss(img, 41, 0.05, (200, 200, 210))
tip = yy > T * 0.6; img[tip] = img[tip] * 0.4 + np.array([100, 0, 5]) * 0.6; tiles['claw'] = img
n = fbm2(T, T, 6, 50)
img = np.stack([140 + 45 * n, 80 + 35 * n, 85 + 35 * n], -1); img = img * (0.8 + 0.2 * np.sin(xx * 0.4)[..., None])
drips(img, 14, 51, (110, 10, 15)); gloss(img, 52, 0.1); tiles['gut'] = img
n = fbm2(T, T, 7, 60)
img = np.stack([55 + 60 * n, 3 + 8 * n, 6 + 8 * n], -1); gloss(img, 61, 0.05, (200, 80, 80)); tiles['cavity'] = img
fb = fibers(T, T, 70, 1.6)
img = np.stack([45 + 50 * fb, 6 + 10 * fb, 9 + 8 * fb], -1); drips(img, 16, 71, (30, 0, 2)); gloss(img, 72, 0.05); tiles['sinew'] = img
img = np.stack([170 + 0 * n, 90 + 20 * n, 90 + 20 * n], -1); tiles['drool'] = img
n = fbm2(T, T, 9, 80)
img = np.stack([110 + 60 * n, 18 + 25 * n, 25 + 15 * n], -1)
pus = fbm2(T, T, 14, 81) > 0.64; img[pus] = [190, 170, 70]; gloss(img, 82, 0.1); tiles['tumor'] = img
n = fbm2(T, T, 5, 90)
img = np.stack([60 + 45 * n, 2 + 6 * n, 4 + 6 * n], -1); gloss(img, 91, 0.1, (255, 150, 150)); drips(img, 20, 92, (40, 0, 2)); tiles['soaked'] = img

n = fbm2(T, T, 6, 100)
img = np.stack([40 + 30 * n, 22 + 18 * n, 55 + 35 * n], -1); gloss(img, 101, 0.12, (200, 170, 220)); tiles['vein'] = img
n = fbm2(T, T, 12, 110); n2m = fbm2(T, T, 40, 111)
img = np.stack([95 + 70 * n * n2m, 105 + 70 * n * n2m, 70 + 40 * n], -1)          # fuzzy mould colony
spore = np.random.default_rng(112).random((T, T)) > 0.93; img[spore] = [210, 215, 190]
dark = np.random.default_rng(113).random((T, T)) > 0.95; img[dark] = [25, 35, 20]; tiles['mold'] = img

def rot_pass(img, seed, mold=0.5, veins=1.0, grime=0.5, blood=1.0):
    r = np.random.default_rng(seed)
    g = fbm2(T, T, 5, seed + 1)                                        # tarnish / grime
    img *= (1 - grime * 0.7 * np.clip((g - 0.35) * 1.6, 0, 1))[..., None]
    m = fbm2(T, T, 7, seed + 2) * fbm2(T, T, 30, seed + 3)                   # mould blooms
    mm = np.clip((m - (0.42 - 0.12 * mold)) * 8, 0, 1)[..., None] * mold
    mcol = np.stack([110 + 60 * fbm2(T, T, 50, seed + 4), 120 + 60 * fbm2(T, T, 50, seed + 5), 80 + 30 * fbm2(T, T, 50, seed + 6)], -1)
    img[:] = img * (1 - mm) + mcol * mm
    sp = (r.random((T, T)) > 0.985) & (mm[..., 0] > 0.2); img[sp] = [220, 225, 200]
    for k, (cells, thr, col) in enumerate([(4, 0.012, (45, 10, 60)), (9, 0.008, (60, 12, 50)), (18, 0.006, (90, 15, 40))]):
        vn = np.abs(fbm2(T, T, cells, seed + 10 + k) - 0.5) < thr * veins * 1.6
        img[vn] = img[vn] * 0.25 + np.array(col) * 0.75
    if blood:
        drips(img, int(25 * blood), seed + 20, (55, 0, 3))
        sp2 = fbm2(T, T, 10, seed + 21) > 0.7; img[sp2] = img[sp2] * 0.3 + np.array([75, 2, 5]) * 0.7
        gloss(img, seed + 22, 0.04, (230, 160, 150))
for i, (mname, mold, veins, grime, blood) in enumerate([('flesh', 0.35, 1.2, 0.5, 1.0), ('skin', 0.8, 1.4, 0.7, 1.2), ('sinew', 0.3, 1.0, 0.5, 0.8),
                                                       ('soaked', 0.2, 0.8, 0.4, 1.0), ('bone', 0.6, 0.0, 0.7, 0.8), ('teeth', 0.3, 0.0, 0.6, 0.6),
                                                       ('gut', 0.3, 1.0, 0.4, 0.8), ('tumor', 0.2, 1.4, 0.3, 0.5), ('claw', 0.2, 0.0, 0.3, 0.6)]):
    tiles[mname] = np.clip(tiles[mname].astype(float), 0, 255)
    rot_pass(tiles[mname], 500 + i * 31, mold, veins, grime, blood)

atlas = np.zeros((ATLAS, ATLAS, 4), np.uint8); glow = np.zeros((ATLAS, ATLAS, 4), np.uint8)
for m in MATS:
    tx, ty = tile_of(m)
    atlas[ty:ty + T, tx:tx + T, :3] = np.clip(tiles[m], 0, 255)
    atlas[ty:ty + T, tx:tx + T, 3] = 170 if m == 'drool' else 255
    if m == 'eye':
        g = np.zeros((T, T, 3)); g[iris] = [150, 20, 10]; g[pupil] = [255, 30, 10]
        glow[ty:ty + T, tx:tx + T, :3] = g; glow[ty:ty + T, tx:tx + T, 3] = 255
    elif m == 'tumor':
        glow[ty:ty + T, tx:tx + T, :3] = (np.clip(tiles[m], 0, 255) * np.array([0.35, 0.05, 0.05])).astype(np.uint8)
        glow[ty:ty + T, tx:tx + T, 3] = 255
    else:
        glow[ty:ty + T, tx:tx + T, 3] = 255
os.makedirs(f'{ASSETS}/textures/entity', exist_ok=True)
Image.fromarray(atlas, 'RGBA').save(f'{ASSETS}/textures/entity/abomination.png')
Image.fromarray(glow, 'RGBA').save(f'{ASSETS}/textures/entity/abomination_glow.png')

# =============================================================================================== binary mesh
os.makedirs(f'{ASSETS}/meshes', exist_ok=True)
total = 0
with open(f'{ASSETS}/meshes/abomination.bin', 'wb') as fbin:
    entries = [(n, [q for _, q in MESH[n]]) for n in NAMES] + [(k, [q for _, q in v]) for k, v in PROPS.items()]
    entries += [(n + '#glow', [q for m, q in MESH[n] if m in ('eye', 'tumor')]) for n in NAMES]
    fbin.write(struct.pack('>i', len(entries)))
    for name, quads in entries:
        nb = name.encode(); fbin.write(struct.pack('>H', len(nb))); fbin.write(nb)
        fbin.write(struct.pack('>i', len(quads)))
        for q in quads:
            for (x, y, z, nx, ny, nz, u, v, c) in q:
                cb = int(np.clip(c, 0, 1) * 255)
                fbin.write(struct.pack('>8fI', x / 16, y / 16, z / 16, nx, ny, nz, u, v, (255 << 24) | (cb << 16) | (cb << 8) | cb))
        total += len(quads)
print('quads', total, 'bytes', os.path.getsize(f'{ASSETS}/meshes/abomination.bin'))

# =============================================================================================== java skeleton
def jf(v):
    s = f'{v:.4f}'.rstrip('0').rstrip('.'); return (s if s not in ('-0', '') else '0') + 'F'
fields = '\n'.join(f'    public final ModelPart {n};' for n in NAMES)
ctor = '\n'.join(f'        this.{n} = ' + ('modelRoot.getChild("root");' if p is None else f'this.{p}.getChild("{n}");') for n, p, _, _ in SK)
layer = '\n'.join(
    f'        PartDefinition p_{n} = {"mesh.getRoot()" if p is None else "p_" + p}.addOrReplaceChild("{n}", CubeListBuilder.create(), '
    f'PartPose.offsetAndRotation({jf(piv[0])}, {jf(piv[1])}, {jf(piv[2])}, {jf(r[0])}, {jf(r[1])}, {jf(r[2])}));'
    for n, p, piv, r in SK)
tree = '        this.bones = new ModelPart[]{' + ', '.join(NAMES) + '};\n'
tree += '        this.boneNames = new String[]{' + ', '.join(f'"{n}"' for n in NAMES) + '};\n'
tree += '        this.boneParent = new int[]{' + ', '.join(str(NAMES.index(p)) if p else '-1' for _, p, _, _ in SK) + '};'
open(JAVA_OUT, 'w').write('// GENERATED_FIELDS\n' + fields + '\n// GENERATED_CTOR\n' + ctor + '\n' + tree + '\n// GENERATED_LAYER\n' + layer + '\n')

# =============================================================================================== preview render
def rotZYX(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx
W_ = {}
for n, p, piv, r in SK:
    Mloc = np.eye(4); Mloc[:3, :3] = rotZYX(*r) * (HEAD_SCALE if n == 'head' else 1.0); Mloc[:3, 3] = np.array(piv)
    W_[n] = (W_[p] if p else np.eye(4)) @ Mloc
def render_preview(path, yaw_deg, W=760, Hh=980, scale=13.0, oy_off=40, focus_y=0.0):
    tex = atlas.astype(float) / 255
    zb = np.full((Hh, W), -1e9); out = np.zeros((Hh, W, 3)); out[:] = (0.035, 0.02, 0.02)
    yaw = math.radians(yaw_deg)
    Ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    flip = np.diag([-1, -1, 1])   # model space -> world (y up), facing +z toward the camera
    light = np.array([0.4, 0.8, 0.6]); light /= np.linalg.norm(light)
    ox, oy = W / 2, (Hh - oy_off) if focus_y == 0 else (Hh / 2 + focus_y * scale)
    tris = []
    for n in NAMES:
        Mw = W_[n]
        for _, q in MESH[n]:
            v = np.array([qq[:3] for qq in q]); nn = np.array([qq[3:6] for qq in q]); uv = np.array([qq[6:8] for qq in q]); c = np.array([qq[8] for qq in q])
            v = (Mw[:3, :3] @ v.T).T + Mw[:3, 3]; nn = (Mw[:3, :3] @ nn.T).T
            v = (Ry @ flip @ v.T).T + np.array([0, 24.0, 0]) @ np.eye(3); nn = (Ry @ flip @ nn.T).T
            for tri in ((0, 1, 2), (0, 2, 3)):
                tris.append((v[list(tri)], nn[list(tri)], uv[list(tri)], c[list(tri)]))
    for V3, N3, UV3, C3 in tris:
        sx = ox + V3[:, 0] * scale; sy = oy - V3[:, 1] * scale; sz = V3[:, 2]
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(W - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(Hh - 1, math.ceil(sy.max())))
        if x1 < x0 or y1 < y0: continue
        den = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(den) < 1e-9: continue
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        a = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / den
        b = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / den
        g = 1 - a - b
        m = (a >= 0) & (b >= 0) & (g >= 0)
        if not m.any(): continue
        z = a * sz[0] + b * sz[1] + g * sz[2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= z > sub
        if not m.any(): continue
        u = a * UV3[0, 0] + b * UV3[1, 0] + g * UV3[2, 0]; v = a * UV3[0, 1] + b * UV3[1, 1] + g * UV3[2, 1]
        tc = tex[np.clip((v * ATLAS).astype(int), 0, ATLAS - 1), np.clip((u * ATLAS).astype(int), 0, ATLAS - 1)]
        nrm = a[..., None] * N3[0] + b[..., None] * N3[1] + g[..., None] * N3[2]
        nrm /= np.linalg.norm(nrm, axis=2, keepdims=True) + 1e-9
        lam = np.abs(nrm @ light) * 0.75 + 0.25
        spec = np.clip(nrm @ np.array([0.2, 0.3, 0.93]), 0, 1) ** 24 * 0.35
        cc = a * C3[0] + b * C3[1] + g * C3[2]
        col = tc[..., :3] * (lam * cc)[..., None] + spec[..., None]
        sub[m] = z[m]
        out[y0:y1 + 1, x0:x1 + 1][m] = col[m]
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
    return img
a = render_preview(PREVIEW, 200); b = render_preview(PREVIEW, 135)
c = render_preview(PREVIEW, 192, W=760, Hh=980, scale=40.0, focus_y=52.0)
both = Image.new('RGB', (a.width * 3, a.height)); both.paste(a, (0, 0)); both.paste(b, (a.width, 0)); both.paste(c, (a.width * 2, 0)); both.save(PREVIEW)
print('preview', PREVIEW)
