package com.ironlung.feed;

import static com.ironlung.ocean.Sdf.*;

/**
 * The thing that comes through the hatch. Eel-like and nearly all mouth: a torn, blood-soaked maw that
 * funnels into a black throat; broken, yellowed needle teeth; a row of flat human teeth in raw gums;
 * strands of blood and mucus stretched between the jaws; a cluster of bloodshot human eyes; a mottled,
 * pitted, veined hide; and nerve-like tendrils trailing off into the dark.
 * Local frame: +z toward the viewer, +y up, units ~metres.
 * mat receives [albedo, flesh, wetness]; negative flesh reads as cold, desaturated hide.
 */
final class Finale {
    private Finale() {}

    private static final int TEETH_OUTER = 96, TEETH_INNER = 60;
    private static final double MX = 0.0, MY = -0.9;
    private static final double RX = 2.55, RY = 3.05;

    private static final double[][][] TENDRILS = buildTendrils();
    private static final double[][] TENDRIL_BOUNDS = buildBounds();
    /** x, y, z, radius, gazeX, gazeY */
    private static final double[][] EYES = {
            {-1.05, 2.75, 3.72, 0.30, 0.10, -0.15}, {0.8, 2.85, 3.68, 0.27, -0.05, -0.2}, {1.75, 2.45, 3.45, 0.22, -0.2, -0.05},
            {-2.0, 2.3, 3.2, 0.18, 0.25, 0.0}, {-0.1, 3.2, 3.35, 0.15, 0.0, -0.3}, {2.3, 2.85, 2.9, 0.13, -0.3, -0.1},
            {-0.45, 2.35, 3.95, 0.11, 0.05, -0.1}};
    /** strands between the jaws: x, top y, bottom y, sag, radius */
    private static final double[][] STRANDS = {
            {-1.6, 0.9, -3.0, 0.5, 0.05}, {-0.9, 1.3, -3.4, 0.8, 0.035}, {-0.2, 1.4, -3.5, 1.0, 0.06}, {0.55, 1.35, -3.45, 0.7, 0.03},
            {1.25, 1.1, -3.2, 0.9, 0.045}, {1.9, 0.6, -2.7, 0.4, 0.03}, {-2.2, 0.3, -2.4, 0.3, 0.028}, {0.15, 1.45, -1.2, 0.2, 0.04}};

    // ------------------------------------------------------------------ noise

    private static double hash(int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L;
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

    private static double fbm(double x, double y, double z) {
        return vnoise(x, y, z) * 0.6 + vnoise(x * 2.3 + 7, y * 2.3, z * 2.3) * 0.3 + vnoise(x * 5.1, y * 5.1 + 3, z * 5.1) * 0.1;
    }

    /** Thin dark lines where the noise crosses 0.5: veins. 0..1 */
    private static double veins(double x, double y, double z) {
        double v = Math.abs(vnoise(x * 1.7, y * 1.7, z * 1.7) - 0.5);
        return Math.max(0, 1 - v * 22);
    }

    // ------------------------------------------------------------------ shape

    private static double[][][] buildTendrils() {
        java.util.Random r = new java.util.Random(4242);
        int n = 13;
        double[][][] out = new double[n][][];
        for (int i = 0; i < n; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            double bx = side * (2.2 + r.nextDouble() * 1.6), by = -2.8 + r.nextDouble() * 3.2, bz = -0.5 + r.nextDouble() * 2.5;
            double dx = side * (0.6 + r.nextDouble() * 0.6), dy = -0.6 + r.nextDouble() * 0.9, dz = 0.1 + r.nextDouble() * 0.6;
            int segs = 9;
            double[][] pts = new double[segs + 1][];
            double px = bx, py = by, pz = bz;
            double ph = r.nextDouble() * 6;
            for (int k = 0; k <= segs; k++) {
                pts[k] = new double[]{px, py, pz};
                px += dx * 0.75 + (r.nextDouble() - 0.5) * 0.3;
                py += dy * 0.5 + Math.sin(ph + k * 1.7) * 0.45 + (r.nextDouble() - 0.5) * 0.3;
                pz += dz * 0.5 + Math.cos(ph + k * 1.1) * 0.3;
            }
            out[i] = pts;
        }
        return out;
    }

    private static double[][] buildBounds() {
        double[][] b = new double[TENDRILS.length][4];
        for (int i = 0; i < TENDRILS.length; i++) {
            double[][] pts = TENDRILS[i];
            double cx = 0, cy = 0, cz = 0;
            for (double[] p : pts) { cx += p[0]; cy += p[1]; cz += p[2]; }
            cx /= pts.length; cy /= pts.length; cz /= pts.length;
            double rr = 0;
            for (double[] p : pts) rr = Math.max(rr, len(p[0] - cx, p[1] - cy, p[2] - cz));
            b[i] = new double[]{cx, cy, cz, rr + 0.5};
        }
        return b;
    }

    /** Tapered cone from a (radius ra) to b (radius rb). */
    private static double cone(double px, double py, double pz, double ax, double ay, double az,
                               double bx, double by, double bz, double ra, double rb) {
        double pax = px - ax, pay = py - ay, paz = pz - az;
        double bax = bx - ax, bay = by - ay, baz = bz - az;
        double dd = bax * bax + bay * bay + baz * baz;
        double h = dd < 1e-9 ? 0 : Math.max(0, Math.min(1, (pax * bax + pay * bay + paz * baz) / dd));
        return len(pax - bax * h, pay - bay * h, paz - baz * h) - (ra + (rb - ra) * h);
    }

    static double sdf(double x, double y, double z, double[] mat) {
        // hide: head plus a long body falling away behind it
        double head = ellipsoid(x, y - 0.2, z - 0.4, 3.7, 3.4, 3.4);
        double body = capsule(x, y, z, 0, 0.6, -2.0, 1.8, 2.2, -18, 2.9);
        double hide = smin(head, body, 1.6);
        double flapL = ellipsoid(x + 3.3, y + 1.1, z - 2.2, 1.0, 2.0, 0.55);
        double flapR = ellipsoid(x - 3.3, y + 1.1, z - 2.2, 1.0, 2.0, 0.55);
        double flaps = Math.min(flapL, flapR);
        double brow = ellipsoid(x, y - 2.3, z - 2.3, 2.7, 1.05, 1.6);
        double outer = smin(smin(hide, flaps, 0.8), brow, 0.7);
        // skin: folds, wrinkles and pits, only computed near the surface
        if (Math.abs(outer) < 0.7) {
            double folds = 0.16 * Math.sin(x * 3.1 + z * 1.7 + vnoise(x, y, z) * 3) * Math.sin(y * 4.3 + x);
            double wrinkle = 0.05 * (fbm(x * 3, y * 3, z * 3) - 0.5);
            double pits = 0.025 * Math.max(0, vnoise(x * 9, y * 9, z * 9) - 0.6) * 4;
            outer += folds + wrinkle + pits;
        }
        // the maw, with torn, ragged edges, and the throat behind it
        double maw = ellipsoid(x - MX, y - MY, z - 3.1, RX + 0.2, RY + 0.2, 3.0);
        maw += 0.14 * Math.sin(y * 4.1 + z * 2) * Math.sin(x * 3.7);
        if (Math.abs(maw) < 0.8) maw -= 0.22 * (fbm(x * 2.2, y * 2.2, z * 2.2) - 0.45) + 0.08 * (vnoise(x * 7, y * 7, z * 7) - 0.5);
        double throat = ellipsoid(x + 0.15 * Math.sin(y * 3), y - MY - 0.1, z - 0.2, 0.8, 1.45, 2.4) + 0.12 * Math.sin(y * 5 + x * 3);
        double flesh = Math.max(outer, -Math.min(maw, throat));

        double d = flesh;
        double alb, fl, wet;
        double inMaw = Math.abs(maw);
        if (Math.abs(throat) < 0.3) {
            alb = 0.02; fl = 1.0; wet = 0.6;
        } else if (inMaw < 0.5 || Math.abs(flaps - flesh) < 0.2) {
            // raw mouth tissue: deep red, ridged, slick, darkening toward the throat
            double ridges = 0.5 + 0.5 * Math.sin((y - MY) * 9 + fbm(x * 2, y * 2, z * 2) * 4);
            double depthDark = Math.max(0.25, Math.min(1, (z - 0.5) / 2.5));
            alb = (0.22 + 0.3 * ridges * fbm(x * 4, y * 4, z * 4)) * depthDark;
            fl = 1.0;
            wet = 0.9;
            if (fbm(x * 3 + 11, y * 3, z * 3) > 0.62) alb *= 0.35; // pooled blood, almost black
        } else {
            // hide: mottled grey-brown, darker blotches, veins, a little slime
            double mottle = fbm(x * 1.3, y * 1.3, z * 1.3);
            alb = 0.16 + 0.26 * mottle;
            fl = -0.08 + 0.9 * Math.max(0, fbm(x * 0.8 + 4, y * 0.8, z * 0.8) - 0.45); // dead grey-brown, bruised patches
            alb *= 1 - 0.55 * veins(x, y, z);
            wet = 0.35 + 0.3 * vnoise(x * 2, y * 2, z * 2);
        }

        double ang = Math.atan2(y - MY, x - MX);
        if (z > 1.2 && z < 4.0) {
            double rim = len((x - MX) / RX, (y - MY) / RY);
            if (rim > 0.55 && rim < 1.25) {
                double[] tm = new double[1];
                double teeth = Math.min(ring(x, y, z, ang, TEETH_OUTER, 1.0, 3.05, 0.95, tm), ring(x, y, z, ang, TEETH_INNER, 0.8, 2.3, 0.7, tm));
                if (teeth < d) {
                    d = teeth;
                    // tm[0] = 0 at the gum, 1 at the tip: blood-stained base, yellowed, filthy enamel
                    double t = tm[0];
                    double grime = fbm(x * 12, y * 12, z * 12);
                    alb = t < 0.3 ? 0.25 + t : 0.45 + 0.25 * grime;
                    fl = t < 0.3 ? 1.0 : 0.4 - 0.2 * t;
                    wet = 0.8;
                }
            }
        }
        // a row of flat human teeth in raw gums behind the needles
        if (z > 1.0 && z < 2.6) {
            double gum = 1e9;
            int idx = (int) Math.round(ang / (Math.PI * 2) * 34);
            for (int j = idx - 1; j <= idx + 1; j++) {
                double a = j * Math.PI * 2 / 34;
                double ca = Math.cos(a), sa = Math.sin(a);
                if (Math.abs(ca) > 0.95) continue;
                if (Math.abs(Math.sin(j * 5.3)) > 0.85) continue; // missing teeth
                double bx = MX + RX * 0.62 * ca, by = MY + RY * 0.62 * sa;
                double px = x - bx, py = y - by, pz = z - 1.9;
                double lu = px * -sa + py * ca, lv = px * ca + py * sa;
                double tilt = 0.08 * Math.sin(j * 2.1);
                gum = Math.min(gum, box(lu + lv * tilt, lv, pz, 0.12, 0.23, 0.07) - 0.025);
            }
            if (gum < d) {
                d = gum;
                double grime = fbm(x * 10, y * 10, z * 10);
                alb = 0.4 + 0.25 * grime;
                fl = 0.45;
                wet = 0.7;
            }
        }
        // strands of blood and mucus stretched between the jaws
        if (z > 1.6 && z < 4.2 && Math.abs(x) < 2.6) {
            for (double[] s : STRANDS) {
                double midY = (s[1] + s[2]) / 2;
                double ax = s[0], ay = s[1], bx = s[0] + 0.1, by = s[2];
                double mxs = (ax + bx) / 2 + s[3] * 0.15, mz = 3.15 - s[3] * 0.25;
                double sd = Math.min(capsule(x, y, z, ax, ay, 3.2, mxs, midY, mz, s[4]),
                        capsule(x, y, z, mxs, midY, mz, bx, by, 3.15, s[4] * 0.8));
                // a bead of blood partway down
                sd = smin(sd, sphere(x - mxs, y - (midY + 0.4), z - mz, s[4] * 2.2), 0.08);
                if (sd < d) {
                    d = sd;
                    alb = 0.3;
                    fl = 1.0;
                    wet = 1.0;
                }
            }
        }
        // bloodshot human eyes, too many, looking at you
        for (double[] e : EYES) {
            double ex = x - e[0], ey = y - e[1], ez = z - e[2];
            double ball = sphere(ex, ey, ez, e[3]);
            if (ball < d) {
                d = ball;
                double fx = ex / e[3] - e[4], fy = ey / e[3] - e[5];
                double front = ez / e[3];
                double r2 = fx * fx + fy * fy;
                if (front > 0.6 && r2 < 0.035) {
                    alb = 0.0; fl = 0.0;
                } else if (front > 0.6 && r2 < 0.16) {
                    alb = 0.16 + 0.12 * Math.sin(Math.atan2(fy, fx) * 23); fl = 0.2; // iris fibres
                } else {
                    double vein = veins(ex * 9 / e[3], ey * 9 / e[3], ez * 9 / e[3]);
                    alb = 0.78 - 0.35 * vein;
                    fl = 0.3 + 0.6 * vein; // bloodshot
                }
                wet = 1.0;
            }
        }
        // nerve-like tendrils
        for (int i = 0; i < TENDRILS.length; i++) {
            double[] b = TENDRIL_BOUNDS[i];
            if (len(x - b[0], y - b[1], z - b[2]) - b[3] > d) continue;
            double[][] pts = TENDRILS[i];
            for (int k = 0; k + 1 < pts.length; k++) {
                double ra = 0.11 * (1 - k / (double) pts.length) + 0.02;
                double rb = 0.11 * (1 - (k + 1) / (double) pts.length) + 0.02;
                double td = cone(x, y, z, pts[k][0], pts[k][1], pts[k][2], pts[k + 1][0], pts[k + 1][1], pts[k + 1][2], ra, rb);
                if (td < d) {
                    d = td;
                    alb = 0.28 + 0.12 * vnoise(x * 6, y * 6, z * 6);
                    fl = 0.75;
                    wet = 0.7;
                }
            }
        }
        mat[0] = alb;
        mat[1] = fl;
        if (mat.length > 2) mat[2] = wet;
        return d;
    }

    /** Distance to a ring of n broken, curved needles set around the mouth rim; tm[0] = position along the tooth. */
    private static double ring(double x, double y, double z, double ang, int n, double scale, double zBase, double length, double[] tm) {
        double best = 1e9;
        int idx = (int) Math.round(ang / (Math.PI * 2) * n);
        for (int j = idx - 2; j <= idx + 2; j++) {
            double a = j * Math.PI * 2 / n;
            double ca = Math.cos(a), sa = Math.sin(a);
            if (sa < -0.25 && Math.floorMod(j, 3) != 0) continue;
            if (Math.abs(ca) > 0.93 && Math.floorMod(j, 2) == 0) continue;
            double jitter = 0.45 + 1.0 * Math.abs(Math.sin(j * 12.9898)) * (sa > 0.2 ? 1.35 : 1.0);
            boolean broken = Math.abs(Math.sin(j * 4.7)) > 0.88;
            double bx = MX + RX * scale * ca + 0.06 * Math.sin(j * 3.3), by = MY + RY * scale * sa;
            double l = length * jitter * (broken ? 0.4 : 1.0);
            double hang = sa > 0.2 ? -0.35 * l : 0;
            double tx = bx - ca * l * 0.75, ty = by - sa * l * 0.75 + hang, tz = zBase + 0.3 + 0.12 * Math.sin(j * 7.1);
            double mx = (bx + tx) / 2 - ca * 0.05, my = (by + ty) / 2 - sa * 0.05 + hang * 0.3, mz = (zBase + tz) / 2 + 0.1;
            double base = 0.045 + 0.02 * Math.abs(Math.sin(j * 1.7));
            double d1 = cone(x, y, z, bx, by, zBase, mx, my, mz, base, base * 0.55);
            double d2 = cone(x, y, z, mx, my, mz, tx, ty, tz, base * 0.55, broken ? base * 0.5 : 0.004);
            double dd = Math.min(d1, d2);
            if (dd < best) {
                best = dd;
                double vx = x - bx, vy = y - by, vz = z - zBase;
                double tl = len(tx - bx, ty - by, tz - zBase);
                tm[0] = Math.max(0, Math.min(1, len(vx, vy, vz) / Math.max(1e-6, tl)));
            }
        }
        return best;
    }
}
