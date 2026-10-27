package com.ironlung.feed;

import static com.ironlung.ocean.Sdf.*;

/**
 * The thing that comes through the hatch. An eel-like head, nearly all mouth: a gaping pink maw that
 * funnels into a black throat, ringed with fine needle teeth, flared jaw flaps, small pale eyes,
 * and tendrils trailing off into the dark. Local frame: +z toward the viewer, +y up, units ~metres.
 * mat receives [albedo, flesh]; negative flesh reads as cold blue-grey hide.
 */
final class Finale {
    private Finale() {}

    private static final int TEETH_OUTER = 96, TEETH_INNER = 60;
    private static final double MX = 0.0, MY = -0.9;           // mouth centre
    private static final double RX = 2.55, RY = 3.05;          // mouth rim radii

    // tendrils: each a chain of points
    private static final double[][][] TENDRILS = buildTendrils();
    /** x, y, z, radius, gazeX, gazeY */
    private static final double[][] EYES = {
            {-1.05, 2.75, 3.72, 0.30, 0.10, -0.15}, {0.8, 2.85, 3.68, 0.27, -0.05, -0.2}, {1.75, 2.45, 3.45, 0.22, -0.2, -0.05},
            {-2.0, 2.3, 3.2, 0.18, 0.25, 0.0}, {-0.1, 3.2, 3.35, 0.15, 0.0, -0.3}, {2.3, 2.85, 2.9, 0.13, -0.3, -0.1},
            {-0.45, 2.35, 3.95, 0.11, 0.05, -0.1}};
    private static final double[][] TENDRIL_BOUNDS = buildBounds();

    private static double[][][] buildTendrils() {
        java.util.Random r = new java.util.Random(4242);
        int n = 11;
        double[][][] out = new double[n][][];
        for (int i = 0; i < n; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            double bx = side * (2.2 + r.nextDouble() * 1.6), by = -2.4 + r.nextDouble() * 3.0, bz = -0.5 + r.nextDouble() * 2.5;
            double dx = side * (0.7 + r.nextDouble() * 0.5), dy = -0.4 + r.nextDouble() * 0.8, dz = 0.2 + r.nextDouble() * 0.5;
            int segs = 7;
            double[][] pts = new double[segs + 1][];
            double px = bx, py = by, pz = bz;
            double ph = r.nextDouble() * 6;
            for (int k = 0; k <= segs; k++) {
                pts[k] = new double[]{px, py, pz};
                double w = Math.sin(ph + k * 1.3) * 0.6;
                px += dx * 0.9;
                py += dy * 0.6 + w;
                pz += dz * 0.6 + Math.cos(ph + k) * 0.3;
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

    static double sdf(double x, double y, double z, double[] mat) {
        // hide: head plus a long body falling away behind it
        double head = ellipsoid(x, y - 0.2, z - 0.4, 3.7, 3.4, 3.4);
        double body = capsule(x, y, z, 0, 0.6, -2.0, 1.8, 2.2, -18, 2.9);
        double hide = smin(head, body, 1.6);
        hide += 0.12 * Math.sin(x * 3.1 + z * 1.7) * Math.sin(y * 4.3) + 0.05 * Math.sin(x * 11) * Math.sin(z * 9 + y * 7);
        // flared jaw flaps at the corners of the mouth
        double flapL = ellipsoid(x + 3.3, y + 1.1, z - 2.2, 1.0, 2.0, 0.55);
        double flapR = ellipsoid(x - 3.3, y + 1.1, z - 2.2, 1.0, 2.0, 0.55);
        double flaps = Math.min(flapL, flapR);
        double brow = ellipsoid(x, y - 2.3, z - 2.3, 2.7, 1.05, 1.6) + 0.1 * Math.sin(x * 6) * Math.sin(z * 5);
        double outer = smin(smin(hide, flaps, 0.8), brow, 0.7);
        // the maw, and the throat behind it
        double maw = ellipsoid(x - MX, y - MY, z - 3.1, RX + 0.2, RY + 0.2, 3.0);
        double throat = ellipsoid(x + 0.15 * Math.sin(y * 3), y - MY - 0.1, z - 0.2, 0.8, 1.45, 2.4) + 0.12 * Math.sin(y * 5 + x * 3);
        maw += 0.14 * Math.sin(y * 4.1 + z * 2) * Math.sin(x * 3.7);
        double flesh = Math.max(outer, -Math.min(maw, throat));

        double d = flesh;
        double alb, fl;
        // classify the surface: inside the mouth is raw and pink; outside is cold hide
        double inMaw = Math.abs(maw);
        if (Math.abs(throat) < 0.25) {
            alb = 0.03; fl = 0.9;
        } else if (inMaw < 0.45 || Math.abs(flaps - flesh) < 0.2) {
            alb = 0.62 + 0.15 * Math.sin(y * 6 + x * 2); fl = 1.0;
        } else {
            alb = 0.42 + 0.1 * Math.sin(x * 5 + y * 3); fl = -0.75;
        }

        // needle teeth: only check the few teeth nearest this point's angle around the rim
        double ang = Math.atan2(y - MY, x - MX);
        if (z > 1.2 && z < 4.0) {
            double rim = len((x - MX) / RX, (y - MY) / RY);
            if (rim > 0.55 && rim < 1.25) {
                double teeth = 1e9;
                teeth = Math.min(teeth, ring(x, y, z, ang, TEETH_OUTER, 1.0, 3.05, 0.95));
                teeth = Math.min(teeth, ring(x, y, z, ang, TEETH_INNER, 0.8, 2.3, 0.7));
                if (teeth < d) {
                    d = teeth;
                    alb = 0.95;
                    fl = 0.15;
                }
            }
        }
        // human eyes, too many, in the wrong places on the brow; whites with dark irises looking at you
        for (double[] e : EYES) {
            double ex = x - e[0], ey = y - e[1], ez = z - e[2];
            double ball = sphere(ex, ey, ez, e[3]);
            if (ball < d) {
                d = ball;
                // iris/pupil: the part of the eyeball facing the viewer (+z), offset by its gaze
                double fx = ex / e[3] - e[4], fy = ey / e[3] - e[5];
                double front = ez / e[3];
                double r2 = fx * fx + fy * fy;
                if (front > 0.6 && r2 < 0.16) {
                    alb = r2 < 0.035 ? 0.0 : 0.12;
                    fl = r2 < 0.035 ? 0.0 : 0.3;
                } else {
                    alb = 1.1;
                    fl = 0.25; // bloodshot white
                }
            }
        }
        // a row of flat human teeth in the gums behind the needles
        if (z > 1.0 && z < 2.6) {
            double gum = 1e9;
            int idx = (int) Math.round(ang / (Math.PI * 2) * 34);
            for (int j = idx - 1; j <= idx + 1; j++) {
                double a = j * Math.PI * 2 / 34;
                double ca = Math.cos(a), sa = Math.sin(a);
                if (Math.abs(ca) > 0.95) continue;
                double bx = MX + RX * 0.62 * ca, by = MY + RY * 0.62 * sa;
                // rotate the box so its flat face points at the throat
                double px = x - bx, py = y - by, pz = z - 1.9;
                double ux = -sa, uy = ca;
                double lu = px * ux + py * uy, lv = px * ca + py * sa;
                gum = Math.min(gum, box(lu, lv, pz, 0.13, 0.24, 0.07) - 0.025);
            }
            if (gum < d) {
                d = gum;
                alb = 0.88;
                fl = 0.35;
            }
        }
        // tendrils
        for (int i = 0; i < TENDRILS.length; i++) {
            double[] b = TENDRIL_BOUNDS[i];
            if (len(x - b[0], y - b[1], z - b[2]) - b[3] > d) continue;
            double[][] pts = TENDRILS[i];
            for (int k = 0; k + 1 < pts.length; k++) {
                double rad = 0.14 * (1 - k / (double) pts.length) + 0.03;
                double td = capsule(x, y, z, pts[k][0], pts[k][1], pts[k][2], pts[k + 1][0], pts[k + 1][1], pts[k + 1][2], rad);
                if (td < d) {
                    d = td;
                    alb = 0.6;
                    fl = 0.8;
                }
            }
        }
        mat[0] = alb;
        mat[1] = fl;
        return d;
    }

    /** Distance to a ring of n needles set around the mouth rim at depth zBase, pointing inward. */
    private static double ring(double x, double y, double z, double ang, int n, double scale, double zBase, double length) {
        double best = 1e9;
        int idx = (int) Math.round(ang / (Math.PI * 2) * n);
        for (int j = idx - 2; j <= idx + 2; j++) {
            double a = j * Math.PI * 2 / n;
            double ca = Math.cos(a), sa = Math.sin(a);
            if (sa < -0.25 && Math.floorMod(j, 3) != 0) continue;        // sparse lower jaw
            if (Math.abs(ca) > 0.93 && Math.floorMod(j, 2) == 0) continue; // gaps at the corners
            double jitter = 0.45 + 1.0 * Math.abs(Math.sin(j * 12.9898)) * (sa > 0.2 ? 1.35 : 1.0);
            double bx = MX + RX * scale * ca, by = MY + RY * scale * sa;
            double l = length * jitter;
            double hang = sa > 0.2 ? -0.35 * l : 0; // upper teeth droop
            double tx = bx - ca * l * 0.75, ty = by - sa * l * 0.75 + hang, tz = zBase + 0.3 + 0.12 * Math.sin(j * 7.1);
            double mx = (bx + tx) / 2 - ca * 0.05, my = (by + ty) / 2 - sa * 0.05 + hang * 0.3, mz = (zBase + tz) / 2 + 0.1;
            best = Math.min(best, Math.min(capsule(x, y, z, bx, by, zBase, mx, my, mz, 0.026),
                    capsule(x, y, z, mx, my, mz, tx, ty, tz, 0.015)));
        }
        return best;
    }
}
