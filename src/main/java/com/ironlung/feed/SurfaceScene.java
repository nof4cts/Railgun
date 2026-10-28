package com.ironlung.feed;

import static com.ironlung.ocean.Sdf.*;

import java.util.stream.IntStream;

/**
 * The opening: a rig on a sea of blood under a blood moon, the submarine hanging from a crane,
 * the pilot crossing the gangway and climbing in, and the boat being lowered. Pure Java ray marcher
 * producing full-colour frames (0xAARRGGBB). Sea level is y = 0.
 */
public final class SurfaceScene {
    private SurfaceScene() {}

    public static final int SHOTS = 5;

    // we are ON the blood moon: what hangs over the horizon is the planet it orbits.
    // MOON here is the direction of that planet (the scene's backlight).
    private static final double[] MOON = norm(0.22, 0.13, 1.0);
    private static final double PLANET_R = 0.31;
    // a distant sun behind and above the planet: lights only a thin crescent
    private static final double[] SUN = norm(0.87, 0.55, 0.18);
    // rig floodlight
    private static final double LX = 2.6, LY = 9.0, LZ = 3.2;

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    /** Per-shot scene state. */
    private static final class Shot {
        double cx, cy, cz, tx, ty, tz, fov;
        double subY;          // height of the sub's keel line
        boolean hatchOpen;
        int figure;           // 0 none, 1 walking on gangway, 2 climbing into hatch
        double figX, figY, figZ, walkPhase;
        boolean chains = true;
    }

    private static Shot shot(int i) {
        Shot s = new Shot();
        s.subY = 2.6;
        s.hatchOpen = true;
        switch (i) {
            case 0 -> { // establishing: the rig, the moon, the sea
                s.cx = -9; s.cy = 3.2; s.cz = -26; s.tx = 1.5; s.ty = 6.5; s.tz = 8; s.fov = 50;
                s.figure = 0;
            }
            case 1 -> { // the boat on its chains
                s.cx = -7.5; s.cy = 4.2; s.cz = 2.0; s.tx = 0; s.ty = 3.6; s.tz = 8; s.fov = 46;
                s.figure = 0;
            }
            case 2 -> { // the pilot on the gangway, against the moon
                s.cx = -3.6; s.cy = 4.0; s.cz = 0.2; s.tx = 0.6; s.ty = 6.0; s.tz = 8.0; s.fov = 52;
                s.figure = 1; s.figX = 0; s.figZ = 4.6; s.walkPhase = 0.7;
            }
            case 3 -> { // climbing in
                s.cx = -3.0; s.cy = 7.0; s.cz = 4.6; s.tx = 0; s.ty = 6.4; s.tz = 7.8; s.fov = 46;
                s.figure = 2; s.figX = 0; s.figZ = 7.6;
            }
            default -> { // sealed, and lowered into the sea
                s.cx = -10; s.cy = 2.2; s.cz = 16; s.tx = 0; s.ty = 1.6; s.tz = 7.5; s.fov = 44;
                s.subY = -0.9; s.hatchOpen = false; s.figure = 0;
            }
        }
        if (s.figure == 1) s.figY = gangwayY(s.figZ, s.subY);
        return s;
    }

    /** Gangway runs from the deck edge (z=2, y=3.2) to the tower top. */
    private static double gangwayY(double z, double subY) {
        double topY = subY + 2.55, t = Math.max(0, Math.min(1, (z - 2.0) / (7.0 - 2.0)));
        return 3.2 + (topY - 3.2) * t;
    }

    // ------------------------------------------------------------------ noise

    private static double hash(int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double vnoise(double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double a = lerp(lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy);
        double b = lerp(lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx), lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy);
        return lerp(a, b, fz);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ scene

    /** mat: [r, g, b, kind] kind 0 metal, 1 figure, 2 lamp */
    private static double scene(Shot s, double x, double y, double z, double[] mat) {
        double best = 1e9;
        double r = 0.3, g = 0.2, b = 0.17, kind = 0;

        // rig: deck on four legs, railings
        double deck = box(x - 0, y - 3.0, z + 1.5, 5.0, 0.2, 3.5);
        double legs = Math.min(Math.min(capsule(x, y, z, -4.4, -6, -4.5, -4.4, 3, -4.5, 0.35), capsule(x, y, z, 4.4, -6, -4.5, 4.4, 3, -4.5, 0.35)),
                Math.min(capsule(x, y, z, -4.4, -6, 1.6, -4.4, 3, 1.6, 0.35), capsule(x, y, z, 4.4, -6, 1.6, 4.4, 3, 1.6, 0.35)));
        double rail = Math.min(box(x + 5.0, y - 3.7, z + 1.5, 0.04, 0.5, 3.5), box(x - 0, y - 3.7, z + 5.0, 5.0, 0.5, 0.04));
        rail = Math.max(rail, -box(x, y - 3.6, z + 1.5, 4.9, 0.4, 3.4));
        double rig = Math.min(Math.min(deck, legs), Math.min(rail, box(x + 5.0, y - 4.2, z + 1.5, 0.06, 0.06, 3.5)));
        // crane: mast and boom reaching out over the water
        double mast = box(x - 3.2, y - 8.5, z + 0.5, 0.35, 5.5, 0.35);
        double boom = capsule(x, y, z, 3.2, 13.6, -0.5, 0.0, 12.6, 7.6, 0.28);
        double brace = capsule(x, y, z, 3.2, 10.0, -0.5, 1.6, 13.0, 3.6, 0.12);
        rig = Math.min(rig, Math.min(mast, Math.min(boom, brace)));
        // a small hut on the deck with a lit window
        double hut = box(x + 2.5, y - 4.3, z + 3.0, 1.4, 1.1, 1.2);
        rig = Math.min(rig, hut);
        if (rig < best) {
            best = rig;
            double n = vnoise(x * 1.7, y * 1.7, z * 1.7);
            r = 0.16 + 0.1 * n; g = 0.11 + 0.05 * n; b = 0.1 + 0.04 * n;
            kind = 0;
        }
        // floodlight housing
        double lamp = sphere(x - LX, y - LY, z - LZ, 0.32);
        if (lamp < best) { best = lamp; r = 3.5; g = 2.6; b = 1.6; kind = 2; }
        double window = box(x + 1.1, y - 4.5, z + 3.0, 0.02, 0.35, 0.5);
        if (window < best) { best = window; r = 1.6; g = 0.9; b = 0.4; kind = 2; }

        // the submarine: a short, fat riveted cylinder with a tower and hatch
        double sy = s.subY + 1.4;
        double hull = capsule(x, y, z, 0, sy, 6.0, 0, sy, 9.2, 1.4);
        hull = Math.min(hull, box(x, y - sy, z - 7.6, 1.42, 0.04, 2.0) - 0.03); // weld band
        double tower = Math.max(len(x, z - 7.6) - 0.62, Math.abs(y - (sy + 1.75)) - 0.45);
        hull = Math.min(hull, tower);
        double rimTop = sy + 2.2;
        hull = Math.max(hull, -Math.max(len(x, z - 7.6) - 0.45, -(y - (rimTop - 0.6)))); // the hatch opening
        // hatch lid: open (standing up behind the opening) or closed (flat on top)
        double lid = s.hatchOpen
                ? Math.max(Math.abs(z - 7.0) - 0.05, len(x, y - (rimTop + 0.48)) - 0.5)
                : Math.max(Math.abs(y - rimTop - 0.04) - 0.05, len(x, z - 7.6) - 0.52);
        hull = Math.min(hull, lid);
        // the external camera on its arm
        hull = Math.min(hull, capsule(x, y, z, 0, sy + 0.2, 9.4, 0, sy + 0.1, 10.1, 0.14));
        hull = Math.min(hull, sphere(x, y - (sy + 0.1), z - 10.25, 0.26));
        if (hull < best) {
            best = hull;
            double rust = vnoise(x * 3.1, y * 3.1, z * 3.1);
            double streak = vnoise(x * 6, y * 0.6, z * 6);
            r = 0.2 + 0.25 * rust; g = 0.1 + 0.08 * rust; b = 0.07 + 0.03 * rust;
            if (streak > 0.7) { r *= 0.6; g *= 0.6; b *= 0.6; }
            double riv = Math.abs(Math.sin(z * 9)) * Math.abs(Math.sin(Math.atan2(y - sy, x) * 10));
            if (riv > 0.92) { r += 0.1; g += 0.07; b += 0.05; }
            kind = 0;
        }
        // chains from the boom tip to the hull
        if (s.chains) {
            double tipY = 12.6;
            double ch = Math.min(capsule(x, y, z, 0, tipY, 7.6, -0.9, sy + 1.1, 6.5, 0.06), capsule(x, y, z, 0, tipY, 7.6, 0.9, sy + 1.1, 8.7, 0.06));
            ch += 0.03 * Math.sin(y * 18); // links
            if (ch < best) { best = ch; r = 0.12; g = 0.08; b = 0.07; kind = 0; }
        }
        // gangway from the deck to the tower (only while boarding)
        if (s.subY > 0) {
            double topY = s.subY + 2.55;
            double dz = 7.0 - 2.0, dy = topY - 3.2;
            double ang = Math.atan2(dy, dz);
            double lz = z - 2.0, ly = y - 3.2;
            double u = lz * Math.cos(ang) + ly * Math.sin(ang), v = -lz * Math.sin(ang) + ly * Math.cos(ang);
            double plank = box(x, v, u - Math.hypot(dz, dy) / 2, 0.55, 0.05, Math.hypot(dz, dy) / 2);
            double hand = Math.min(box(x - 0.55, v - 0.55, u - Math.hypot(dz, dy) / 2, 0.03, 0.03, Math.hypot(dz, dy) / 2),
                    box(x + 0.55, v - 0.55, u - Math.hypot(dz, dy) / 2, 0.03, 0.03, Math.hypot(dz, dy) / 2));
            double gw = Math.min(plank, hand);
            if (gw < best) { best = gw; r = 0.14; g = 0.1; b = 0.09; kind = 0; }
        }
        // the pilot: a blocky figure
        if (s.figure != 0) {
            double fig = figure(s, x, y, z);
            if (fig < best) {
                best = fig;
                r = 0.18; g = 0.15; b = 0.14; kind = 1;
            }
        }
        if (mat != null) { mat[0] = r; mat[1] = g; mat[2] = b; mat[3] = kind; }
        return best;
    }

    private static double figure(Shot s, double x, double y, double z) {
        double fx = x - s.figX, fy = y - s.figY, fz = z - s.figZ;
        if (s.figure == 1) {
            // walking up the gangway toward +z
            double sw = Math.sin(s.walkPhase) * 0.45;
            double legL = rotBox(fx + 0.125, fy, fz, 0.75, sw, 0.125, 0.375, 0.125);
            double legR = rotBox(fx - 0.125, fy, fz, 0.75, -sw, 0.125, 0.375, 0.125);
            double body = box(fx, fy - 1.125, fz, 0.25, 0.375, 0.125);
            double armL = rotBox(fx + 0.375, fy - 0.75, fz, 0.75, -sw, 0.125, 0.375, 0.125);
            double armR = rotBox(fx - 0.375, fy - 0.75, fz, 0.75, sw, 0.125, 0.375, 0.125);
            double head = box(fx, fy - 1.75, fz - 0.03, 0.25, 0.25, 0.25);
            return Math.min(Math.min(Math.min(legL, legR), Math.min(body, head)), Math.min(armL, armR));
        }
        // climbing down into the hatch: chest and head above the rim, arms gripping it
        double rimTop = s.subY + 1.4 + 2.2;
        double baseY = rimTop - 0.5;
        fy = y - baseY;
        double body = box(fx, fy - 0.2, fz, 0.25, 0.375, 0.125);
        double head = box(fx, fy - 0.83, fz, 0.25, 0.25, 0.25);
        double armL = box(fx + 0.52, fy - 0.62, fz - 0.05, 0.125, 0.06, 0.3);
        double armR = box(fx - 0.52, fy - 0.62, fz - 0.05, 0.125, 0.06, 0.3);
        return Math.min(Math.min(body, head), Math.min(armL, armR));
    }

    /** A limb hanging from a hip/shoulder pivot at height py, swung by angle a about the x axis. */
    private static double rotBox(double x, double y, double z, double py, double a, double hx, double hy, double hz) {
        double ly = y - py, lz = z;
        double c = Math.cos(a), sn = Math.sin(a);
        double ry = ly * c - lz * sn, rz = ly * sn + lz * c;
        return box(x, ry + hy, rz, hx, hy, hz);
    }

    // ------------------------------------------------------------------ sky and sea

    private static double[] sky(double dx, double dy, double dz) {
        double h = Math.max(0, dy);
        double r = 0.012 + 0.32 * Math.exp(-h * 9);
        double g = 0.004 + 0.035 * Math.exp(-h * 10);
        double b = 0.006 + 0.02 * Math.exp(-h * 12);
        double cosM = dx * MOON[0] + dy * MOON[1] + dz * MOON[2];
        double angle = Math.acos(Math.max(-1, Math.min(1, cosM)));
        // glow
        double edge = Math.max(0, angle - PLANET_R);
        double glow = Math.exp(-edge * 22) * 0.35 + Math.exp(-edge * 4) * 0.1;
        r += glow * 0.9;
        g += glow * 0.12;
        b += glow * 0.06;
        if (angle < PLANET_R) {
            // the parent planet: a banded gas giant, almost all night side, a thin lit crescent
            double u = angle / PLANET_R;
            double tx = dx - MOON[0] * cosM, ty = dy - MOON[1] * cosM, tz = dz - MOON[2] * cosM;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-9;
            tx /= tl; ty /= tl; tz /= tl;
            double sq = Math.sqrt(Math.max(0, 1 - u * u));
            double nx = -MOON[0] * sq + tx * u, ny = -MOON[1] * sq + ty * u, nz = -MOON[2] * sq + tz * u;
            double lit = Math.max(0, nx * SUN[0] + ny * SUN[1] + nz * SUN[2]);
            lit = Math.pow(lit, 1.3);
            // bands follow the planet's latitude (tilted a little)
            double lat = ny * 0.95 + nx * 0.3;
            double band = 0.5 + 0.5 * Math.sin(lat * 26 + vnoise(nx * 6, ny * 6, nz * 6) * 3.5);
            double storm = vnoise(nx * 14 + 3, ny * 14, nz * 14);
            double br = 0.55 + 0.45 * band, bg = 0.3 + 0.25 * band, bb = 0.22 + 0.15 * band;
            if (storm > 0.78) { br *= 0.6; bg *= 0.5; bb *= 0.5; }
            double night = 0.012 + 0.03 * Math.pow(u, 6); // faint red airglow at the limb
            r = br * lit * 2.2 + night * 1.4 + 0.004;
            g = bg * lit * 1.4 + night * 0.15;
            b = bb * lit * 1.1 + night * 0.1;
            // the planet blots out the haze behind it, except a thin atmosphere at the edge
            double limb = Math.pow(u, 14) * 0.25;
            r += limb * 0.8; g += limb * 0.1; b += limb * 0.06;
            return new double[]{r, g, b};
        }
        // faint stars, smothered by the haze
        if (dy > 0.15) {
            double st = hash((int) (dx * 900), (int) (dy * 900), (int) (dz * 900));
            if (st > 0.9993) { r += 0.25; g += 0.2; b += 0.2; }
        }
        return new double[]{r, g, b};
    }

    private static double waveH(double x, double z) {
        return 0.12 * Math.sin(x * 0.7 + z * 0.4) + 0.08 * Math.sin(x * 1.9 - z * 1.3 + 1.7) + 0.04 * Math.sin(x * 4.1 + z * 3.3)
                + 0.12 * (vnoise(x * 0.5, 0, z * 0.5) - 0.5);
    }

    // ------------------------------------------------------------------ render

    public static void render(int[] out, int w, int h, int shotIndex, long seed) {
        final Shot s = shot(shotIndex);
        double fx = s.tx - s.cx, fy = s.ty - s.cy, fz = s.tz - s.cz;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        final double Fx = fx / fl, Fy = fy / fl, Fz = fz / fl;
        double rx = Fz, rz = -Fx; // right = forward x up(0,1,0)... (z, 0, -x)
        double rl = Math.sqrt(rx * rx + rz * rz);
        final double Rx = -rx / rl, Rz = -rz / rl;
        final double Ux = -(Rz * Fy), Uy = Rz * Fx - Rx * Fz, Uz = Rx * Fy;
        final double tanHalf = Math.tan(Math.toRadians(s.fov) / 2), aspect = w / (double) h;

        IntStream.range(0, h).parallel().forEach(j -> {
            double[] mat = new double[4];
            java.util.Random grain = new java.util.Random(seed * 31 + j * 7919L);
            for (int i = 0; i < w; i++) {
                double sx = (2 * (i + 0.5) / w - 1) * tanHalf * aspect, sy = (1 - 2 * (j + 0.5) / h) * tanHalf;
                double dx = Fx + Rx * sx + Ux * sy, dy = Fy + Uy * sy, dz = Fz + Rz * sx + Uz * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                double[] col = trace(s, s.cx, s.cy, s.cz, dx, dy, dz, mat, 0);
                // tone map, grade, grain, vignette
                double u = (i + 0.5) / w - 0.5, v = (j + 0.5) / h - 0.5;
                double vig = Math.max(0, 1 - (u * u + v * v) * 1.6);
                double gr = (grain.nextDouble() - 0.5) * 0.05;
                double R = tone(col[0] * vig) + gr, G = tone(col[1] * vig) + gr * 0.8, B = tone(col[2] * vig) + gr * 0.8;
                int ri = clamp255(R), gi = clamp255(G), bi = clamp255(B);
                out[j * w + i] = 0xFF000000 | (ri << 16) | (gi << 8) | bi;
            }
        });
    }

    private static double tone(double c) {
        c = Math.max(0, c);
        c = c / (1 + c * 0.9);
        return Math.pow(c, 0.85);
    }

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, v * 255));
    }

    private static double[] trace(Shot s, double ox, double oy, double oz, double dx, double dy, double dz, double[] mat, int depth) {
        double t = 0.05;
        double tSea = dy < -1e-4 ? -oy / dy : 1e9;
        for (int step = 0; step < 220 && t < 160; step++) {
            if (t > tSea) break;
            double px = ox + dx * t, py = oy + dy * t, pz = oz + dz * t;
            double d = scene(s, px, py, pz, mat);
            if (d < 0.004 * Math.max(1, t * 0.2)) {
                return shade(s, px, py, pz, dx, dy, dz, mat, t);
            }
            t += Math.max(0.01, d * 0.9);
        }
        if (tSea < 1e8 && depth == 0) {
            // the blood sea: dark, glossy, reflecting the moon and the haze
            double px = ox + dx * tSea, pz = oz + dz * tSea;
            double e = 0.05;
            double nx = -(waveH(px + e, pz) - waveH(px - e, pz)) / (2 * e), nz = -(waveH(px, pz + e) - waveH(px, pz - e)) / (2 * e);
            double nl = Math.sqrt(nx * nx + 1 + nz * nz);
            nx /= nl; double ny = 1 / nl; nz /= nl;
            double dn = dx * nx + dy * ny + dz * nz;
            double rx = dx - 2 * dn * nx, ry = Math.abs(dy - 2 * dn * ny), rz = dz - 2 * dn * nz;
            double fres = 0.04 + 0.96 * Math.pow(1 - Math.max(0, -dn), 5);
            double[] refl = trace(s, px, 0.02, pz, rx, ry, rz, mat, 1);
            double base = 0.025;
            double fog = 1 - Math.exp(-tSea * 0.012);
            double[] hz = sky(dx, 0.02, dz);
            double r = base * 0.9 + refl[0] * (0.25 + fres * 0.75), g = base * 0.08 + refl[1] * (0.2 + fres * 0.6), b = base * 0.06 + refl[2] * (0.2 + fres * 0.6);
            return new double[]{lerp(r, hz[0], fog), lerp(g, hz[1], fog), lerp(b, hz[2], fog)};
        }
        return sky(dx, dy, dz);
    }

    private static double[] shade(Shot s, double px, double py, double pz, double dx, double dy, double dz, double[] mat, double t) {
        double r = mat[0], g = mat[1], b = mat[2];
        int kind = (int) mat[3];
        if (kind == 2) return new double[]{r, g, b};
        double e = 0.004;
        double d0 = scene(s, px, py, pz, null);
        double nx = scene(s, px + e, py, pz, null) - d0, ny = scene(s, px, py + e, pz, null) - d0, nz = scene(s, px, py, pz + e, null) - d0;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
        nx /= nl; ny /= nl; nz /= nl;
        // moonlight (red, from behind most subjects) + rig floodlight + a little haze
        double moon = Math.max(0, nx * MOON[0] + ny * MOON[1] + nz * MOON[2]);
        double lx = LX - px, ly = LY - py, lz = LZ - pz;
        double ld = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double flood = Math.max(0, (nx * lx + ny * ly + nz * lz) / ld) * 45 / (ld * ld + 4);
        // rim light where the moon grazes the silhouette
        double rim = Math.pow(1 - Math.max(0, -(nx * dx + ny * dy + nz * dz)), 3) * Math.max(0, -(dx * MOON[0] + dy * MOON[1] + dz * MOON[2]) * -1);
        double occ = 1;
        for (int k = 1; k <= 3; k++) {
            double hs = 0.1 * k;
            occ -= (hs - scene(s, px + nx * hs, py + ny * hs, pz + nz * hs, null)) * (1.2 / k);
        }
        occ = Math.max(0.25, Math.min(1, occ));
        double R = r * (moon * 0.9 + flood * 1.0 + 0.06) * occ + rim * 0.5;
        double G = g * (moon * 0.18 + flood * 0.85 + 0.03) * occ + rim * 0.06;
        double B = b * (moon * 0.12 + flood * 0.6 + 0.03) * occ + rim * 0.04;
        double fog = 1 - Math.exp(-t * 0.012);
        double[] hz = sky(dx, Math.max(0.02, dy * 0.3), dz);
        return new double[]{lerp(R, hz[0], fog), lerp(G, hz[1], fog), lerp(B, hz[2], fog)};
    }
}
