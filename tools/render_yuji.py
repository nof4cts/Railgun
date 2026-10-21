"""
Offline test of yuji.fsh (+ ycomposite.fsh for impact frames) over the synthetic landscape.

    python3 tools/render_yuji.py out.png --fx <scenario> [--impact MODE]
"""
import argparse
import math
import os
import sys

import moderngl
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import render_world as rw  # noqa: E402

ROWS = 320
B = dict(SLASH=0, FLASH=48, RING=56, DUST=72, BOLT=80, ORB=208, SHELL=224, CHARGE=228, AURA=232, MARK=240, TRENCH=256, SPHERE=272)


def norm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--fx", default="bf")
    ap.add_argument("--impact", type=float, default=0)
    ap.add_argument("--w", type=int, default=640)
    ap.add_argument("--h", type=int, default=360)
    a = ap.parse_args()
    g = lambda x, z: float(rw.height(np.array(x), np.array(z)))
    data = np.zeros((ROWS, 4, 4), np.float32)
    counts = {k: 0 for k in B}
    U = {}

    def put(kind, *cols):
        i = counts[kind]
        for c, v in enumerate(cols):
            data[B[kind] + i, c, :len(v)] = v
        counts[kind] += 1

    cam = np.array([-10.0, g(-10, -6) + 4, -6.0])
    tgt = np.array([0.0, g(0, 12) + 1.0, 12.0])
    look = tgt
    fov = 70
    fx = a.fx
    W = []  # world-space points to make camera-relative: (kind,row,col)

    def wpt(p):
        return list(np.asarray(p, float))

    if fx == "bf":
        put("FLASH", wpt(tgt) + [0.07], [2.0, 3.0, 2.0, 0])
        put("RING", wpt(tgt - [0, 1, 0]) + [0.25], [40, 1.2, 30, 1.0])
    if fx == "bf_big":
        cam = np.array([-40.0, g(-40, -30) + 12, -30.0])
        put("FLASH", wpt(tgt) + [0.12], [5.0, 7.0, 4.0, 0])
        put("RING", wpt(tgt - [0, 1, 0]) + [0.6], [60, 2.0, 60, 1.0])
        put("RING", wpt(tgt - [0, 1, 0]) + [0.3], [90, 1.5, 90, 0.8])
        put("SHELL", wpt(tgt) + [14.0], [3.0, 1.0, 0, 0])
    if fx == "crater":
        cam = np.array([-35.0, g(-35, -20) + 14, -20.0])
        put("DUST", wpt(tgt - [0, 1, 0]) + [2.0], [18, 22, 1.0, 0])
        put("RING", wpt(tgt - [0, 1, 0]) + [1.0], [50, 2.0, 50, 1.0])
        put("TRENCH", wpt(tgt) + [0.6], wpt(norm([0.3, 0, 1])) + [40], [3.0, 120, 0, 0])
    if fx == "blood":
        rng = np.random.default_rng(2)
        hand = cam + norm(tgt - cam) * 1.5
        for i in range(10):
            p = tgt + np.array([rng.uniform(-8, 8), 18 - i * 1.6, rng.uniform(-8, 8)])
            put("BOLT", wpt(p) + [0.22], wpt(p + [0, 3.5, 0]) + [1.0], [0, 1, 0, 0])
        put("BOLT", wpt(tgt + [0, 0.5, 0]) + [0.12], wpt(hand) + [1.0], [0, 1, 0, 0])
        put("BOLT", wpt(tgt + [3, 4, 0]) + [0.4], wpt(tgt + [3, 14, 6]) + [1.0], [1, 1, 0, 0])
    if fx == "orbs":
        for i in range(5):
            ang = i / 5 * 6.283
            put("ORB", wpt(tgt + [math.cos(ang) * 3, 2.5 + math.sin(ang * 2) * 0.5, math.sin(ang) * 3]) + [0.6], [1.0, 0, 0, 0])
    if fx == "charge":
        cam = tgt + np.array([-1.2, 0.5, -2.2])
        put("CHARGE", wpt(tgt + [0, 0.2, 0]) + [0.8])
        U["Dark"] = (0.6, 0.6, 0.7, 1.0)
    if fx == "aura":
        cam = tgt + np.array([-3.0, 1.2, -4.0])
        look = tgt + [0, 0.5, 0]
        put("AURA", wpt(tgt - [0, 1, 0]) + [1.9], [1.0, 1, 0, 0])
    if fx == "domain_in":
        c = np.array([0.0, g(0, 0), 0.0])
        cam = np.array([-10.0, g(-10, -10) + 3, -10.0])
        look = np.array([20.0, g(20, 30) + 4, 30.0])
        U["DomC"] = tuple(c) + (50.0,)
        U["DomP"] = (4.0, 1.0, 3.0, 1.0)
        put("MARK", wpt([8, g(8, 14), 14]) + [1.0])
    if fx == "domain_out":
        c = np.array([0.0, g(0, 0), 0.0])
        cam = np.array([-90.0, g(-90, -90) + 25, -90.0])
        look = c + [0, 15, 0]
        U["DomC"] = tuple(c) + (50.0,)
        U["DomP"] = (4.0, 1.0, 3.0, 1.0)
    if fx == "sphere":
        cam = np.array([-60.0, g(-60, -60) + 6, -60.0])
        put("SPHERE", wpt(tgt + [0, 45, 0]) + [12.0], [3.0, 1.0, 0, 0])
        look = tgt + [0, 30, 0]
        rng = np.random.default_rng(5)
        for i in range(30):
            p = tgt + np.array([rng.uniform(-20, 20), rng.uniform(5, 40), rng.uniform(-20, 20)])
            put("BOLT", wpt(p) + [0.35], wpt(p + (tgt + [0, 45, 0] - p) * 0.15) + [1.0], [0, 1, 0, 0])
        U["Dark"] = (0.5, 0.8, 0.6, 0.6)
    if fx == "slash":
        o = np.array([0.0, g(0, 0) + 1.6, 0.0])
        cam = np.array([-6.0, g(-6, 0) + 3.5, -4.0])
        look = np.array([0.0, g(0, 20), 20.0])
        d = norm([0.05, -0.05, 1.0])
        right = norm(np.cross(d, [0, 1, 0])); up = np.cross(right, d)
        r = math.radians(30); side = right * math.cos(r) + up * math.sin(r)
        put("SLASH", wpt(o) + [0.02], wpt(d) + [60], wpt(side) + [16], [0, 380, 1, 0.3])
        put("SLASH", wpt(o) + [0.6], wpt(norm([0.3, -0.06, 1])) + [60], wpt(right) + [16], [0, 380, 2, 1.0])

    fwd, right, up = rw.look_basis(look - cam)
    aspect = a.w / a.h
    P = rw.perspective(fov, aspect)
    view = np.stack([right, up, -fwd])
    ys, xs = np.mgrid[0:a.h, 0:a.w]
    px = ((xs + 0.5) / a.w * 2 - 1) * math.tan(math.radians(fov) / 2) * aspect
    py = (1 - (ys + 0.5) / a.h * 2) * math.tan(math.radians(fov) / 2)
    rd = rw.norm(fwd * 1 + right * px[..., None] + up * py[..., None])
    col, dist = rw.landscape(cam, rd)
    vz = -(dist * (rd @ fwd))
    with np.errstate(all="ignore"):
        ndc = np.where(np.isfinite(dist), (P[2, 2] * vz + P[2, 3]) / -vz, 1.0)
    depth = np.clip(ndc * 0.5 + 0.5, 0, 1).astype(np.float32)
    depth[~np.isfinite(dist)] = 1.0

    # make positions camera-relative: column 0 of every row except a few kinds whose column 0 is not a point
    for kind, base in B.items():
        for i in range(counts[kind]):
            data[base + i, 0, :3] -= cam
            if kind == "BOLT":
                data[base + i, 1, :3] -= cam
    if "DomC" in U:
        d = U["DomC"]
        U["DomC"] = (d[0] - cam[0], d[1] - cam[1], d[2] - cam[2], d[3])

    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    prog = ctx.program(vertex_shader=rw.load("fullscreen.vsh"), fragment_shader=rw.load("yuji.fsh"))
    vao = ctx.vertex_array(prog, [])
    scene = ctx.texture((a.w, a.h), 3, col[::-1].copy().tobytes(), dtype="f4")
    dtex = ctx.texture((a.w, a.h), 1, depth[::-1].copy().tobytes(), dtype="f4")
    ftex = ctx.texture((4, ROWS), 4, data.tobytes(), dtype="f4")
    ftex.filter = (moderngl.NEAREST, moderngl.NEAREST)
    scene.use(0)
    dtex.use(1)
    ftex.use(2)
    vals = {"SceneTex": 0, "DepthTex": 1, "FxData": 2, "Proj": tuple(P.T.flatten()), "InvProj": tuple(np.linalg.inv(P).T.flatten()),
            "ViewRot": tuple(view.T.flatten()), "InvViewRot": tuple(view.flatten()), "Time": 12.0, "Steps": 48,
            "Dark": (0, 1, 1, 1), "DomC": (0, 0, 0, 0), "DomP": (0, 0, 0, 0), "Overdrive": 0.0}
    names = dict(SLASH="NSlash", FLASH="NFlash", RING="NRing", DUST="NDust", BOLT="NBolt", ORB="NOrb", SHELL="NShell",
                 CHARGE="NCharge", AURA="NAura", MARK="NMark", TRENCH="NTrench", SPHERE="NSphere")
    for k, n in names.items():
        vals[n] = counts[k]
    vals.update(U)
    for k, v in vals.items():
        if k in prog:
            prog[k].value = v
    out = ctx.texture((a.w, a.h), 4, dtype="f4")
    fbo = ctx.framebuffer([out])
    fbo.use()
    vao.render(moderngl.TRIANGLES, vertices=3)
    img = np.frombuffer(out.read(), dtype=np.float32).reshape(a.h, a.w, 4)[::-1, :, :3]

    if a.impact:
        comp = ctx.program(vertex_shader=rw.load("fullscreen.vsh"), fragment_shader=rw.load("ycomposite.fsh"))
        cvao = ctx.vertex_array(comp, [])
        out.use(0)
        black = ctx.texture((a.w, a.h), 4, dtype="f4")
        black.use(1)
        dtex.use(2)
        sc = np.append(tgt - cam, 1.0)
        clip = P @ np.append(view @ (tgt - cam), 1.0)
        c = clip[:2] / clip[3] * 0.5 + 0.5
        cv = {"Hdr": 0, "Bloom": 1, "Depth": 2, "InvProj": tuple(np.linalg.inv(P).T.flatten()), "ScreenSize": (a.w, a.h),
              "Time": 3.0, "BloomStrength": 0.0, "Exposure": 1.0, "Aberration": 0.3, "Shockwave": (0.5, 0.5, 0, 0),
              "Flash": (1, 1, 1, 0), "Afterimage": 0.0, "Desaturate": 0.0, "Vignette": 0.25, "Grain": 0.2,
              "Impact": (a.impact, 1.0, c[0], c[1]), "ImpactDir": (0.8, 0.3, 1.0 if a.impact == 1 else 0.0, 1.0 if a.impact == 6 else 0.0),
              "ImpactSeed": 7.0, "Accent": (1.0, 0.1, 0.15), "Punch": 0.5, "Smear": (0, 0, 0, 0), "Blackout": 0.0}
        for k, v in cv.items():
            if k in comp:
                comp[k].value = v
        out2 = ctx.texture((a.w, a.h), 4, dtype="f4")
        ctx.framebuffer([out2]).use()
        cvao.render(moderngl.TRIANGLES, vertices=3)
        img = np.frombuffer(out2.read(), dtype=np.float32).reshape(a.h, a.w, 4)[::-1, :, :3]
    m = img.max(axis=-1, keepdims=True)
    mapped = np.where(m > 0.8, 0.8 + 0.2 * (1 - np.exp(-(m - 0.8) / 0.2)), m)
    img = img * (mapped / np.maximum(m, 1e-6))
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(a.out)
    print("saved", a.out)


if __name__ == "__main__":
    main()
