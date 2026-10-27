package com.ironlung.feed;

import com.ironlung.ocean.OceanMap;
import com.ironlung.ocean.Poi;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Software renderer for the external camera. Ray-marches the seabed heightfield and
 * the signed-distance objects resting on it, lit only by the camera's own floodlight,
 * through dense, light-eating red water. Pure Java: no Minecraft classes, so it can
 * run on a worker thread and be tested outside the game.
 */
public final class FeedRenderer {

    /** A thing that is only there for a moment. */
    public static final class Transient {
        public static final int SILHOUETTE = 0, LEVIATHAN = 1, FIGURE = 2, EYES = 3, FACE = 4;
        public final int kind;
        public final double x, y, z, vx, vy, vz, size, yaw;
        public final double start, duration;

        public Transient(int kind, double x, double y, double z, double vx, double vy, double vz,
                         double size, double yaw, double start, double duration) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.yaw = yaw;
            this.start = start;
            this.duration = duration;
        }

        boolean alive(double t) {
            return t >= start && t <= start + duration;
        }
    }

    public static final class Params {
        public double fovDeg = 52;
        /** Distance (m) at which the floodlight has mostly given up. */
        public double lightRange = 24;
        /** 0..1 sensor noise. */
        public double grain = 0.25;
        /** 0..1 signal interference (tearing, rolling bars). */
        public double glitch = 0;
        public boolean photo = false;
        public long frame = 0;
        /** Seconds; used for transient objects. */
        public double time = 0;
        public final List<Transient> transients = new ArrayList<>();
        /** Light multiplier: 0 = camera light dead. */
        public double light = 1.0;
    }

    private final OceanMap map;
    // Height cache: floor y sampled on a grid around the camera.
    private static final double RES = 0.5;
    private static final int N = 360; // 180 m window
    private final float[] cacheY = new float[N * N];
    private final float[] cacheSed = new float[N * N];
    private double cacheOx = Double.NaN, cacheOz = Double.NaN;

    public FeedRenderer(OceanMap map) {
        this.map = map;
    }

    private void ensureCache(double cx, double cz) {
        double half = N * RES / 2;
        if (!Double.isNaN(cacheOx)) {
            double mx = cacheOx + half, mz = cacheOz + half;
            if (Math.abs(cx - mx) < 35 && Math.abs(cz - mz) < 35) return;
        }
        final double ox = Math.floor((cx - half) / RES) * RES;
        final double oz = Math.floor((cz - half) / RES) * RES;
        IntStream.range(0, N).parallel().forEach(j -> {
            for (int i = 0; i < N; i++) {
                double x = ox + i * RES, z = oz + j * RES;
                cacheY[j * N + i] = (float) map.floorY(x, z);
                cacheSed[j * N + i] = (float) map.sediment(x, z);
            }
        });
        cacheOx = ox;
        cacheOz = oz;
    }

    private double floorY(double x, double z) {
        double fx = (x - cacheOx) / RES, fz = (z - cacheOz) / RES;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        if (ix < 0 || iz < 0 || ix >= N - 1 || iz >= N - 1) return map.floorY(x, z);
        double tx = fx - ix, tz = fz - iz;
        int k = iz * N + ix;
        double a = cacheY[k], b = cacheY[k + 1], c = cacheY[k + N], d = cacheY[k + N + 1];
        double ab = a + (b - a) * tx, cd = c + (d - c) * tx;
        return ab + (cd - ab) * tz;
    }

    private double sediment(double x, double z) {
        int ix = (int) Math.floor((x - cacheOx) / RES), iz = (int) Math.floor((z - cacheOz) / RES);
        if (ix < 0 || iz < 0 || ix >= N || iz >= N) return 0.5;
        return cacheSed[iz * N + ix];
    }

    /** Scene objects for one frame, pre-culled. */
    private static final class Scene {
        final List<Poi> pois;
        final List<Transient> trans;
        final double time;

        Scene(List<Poi> pois, List<Transient> trans, double time) {
            this.pois = pois;
            this.trans = trans;
            this.time = time;
        }
    }

    /** Distance to the nearest object; mat receives [albedo, flesh]. */
    private static double sceneSdf(Scene s, double x, double y, double z, double[] mat, double[] tmp) {
        double best = 1e9;
        for (int i = 0, n = s.pois.size(); i < n; i++) {
            Poi p = s.pois.get(i);
            double dx = x - p.x, dz = z - p.z;
            double bound = Math.sqrt(dx * dx + dz * dz) - p.radius;
            if (bound > best || bound > 6) {
                if (bound < best) best = bound;
                if (mat != null && bound <= best) { mat[0] = p.type.albedo; mat[1] = p.type.flesh; mat[2] = 0.15; }
                continue;
            }
            double d = p.sdf(x, y, z, tmp);
            if (d < best) {
                best = d;
                if (mat != null) { mat[0] = tmp[0]; mat[1] = tmp[1]; mat[2] = 0.15; }
            }
        }
        for (int i = 0, n = s.trans.size(); i < n; i++) {
            Transient t = s.trans.get(i);
            double age = s.time - t.start;
            double tx = t.x + t.vx * age, ty = t.y + t.vy * age, tz = t.z + t.vz * age;
            double lx = x - tx, ly = y - ty, lz = z - tz;
            double d;
            double alb, flesh, wet = -1;
            switch (t.kind) {
                case Transient.SILHOUETTE: {
                    double c = Math.cos(t.yaw), sn = Math.sin(t.yaw);
                    double rx = lx * c + lz * sn, rz = -lx * sn + lz * c;
                    double und = Math.sin(rz * 0.35 + s.time * 2.0) * 0.8;
                    d = com.ironlung.ocean.Sdf.ellipsoid(rx + und, ly, rz, 1.6 * t.size, 1.1 * t.size, 7.5 * t.size);
                    d = Math.min(d, com.ironlung.ocean.Sdf.ellipsoid(rx + und * 1.6, ly, rz - 8.5 * t.size, 0.4 * t.size, 2.2 * t.size, 1.6 * t.size));
                    alb = 0.32;
                    flesh = 0.35;
                    break;
                }
                case Transient.LEVIATHAN: {
                    double body = com.ironlung.ocean.Sdf.sphere(lx, ly, lz, t.size);
                    body += 0.9 * Math.sin(lx * 0.4 + ly * 0.3) * Math.sin(lz * 0.35 - ly * 0.2);
                    double c = Math.cos(t.yaw), sn = Math.sin(t.yaw);
                    // the eye sits on the side facing the camera (yaw points at the viewer)
                    double ex = tx + sn * t.size * 0.93, ez = tz - c * t.size * 0.93;
                    double eye = com.ironlung.ocean.Sdf.sphere(x - ex, y - (ty + t.size * 0.18), z - ez, t.size * 0.075);
                    double slit = com.ironlung.ocean.Sdf.ellipsoid(x - (ex - sn * t.size * 0.06), y - (ty + t.size * 0.18), z - (ez + c * t.size * 0.06),
                            t.size * 0.018, t.size * 0.065, t.size * 0.018);
                    if (slit < eye && slit < body) {
                        d = slit;
                        alb = 0.0;
                        flesh = 0.0;
                    } else if (eye < body) {
                        d = eye;
                        alb = 1.6;
                        flesh = 0.05;
                    } else {
                        d = body;
                        alb = 0.1;
                        flesh = 0.4;
                    }
                    break;
                }
                case Transient.EYES: {
                    double c = Math.cos(t.yaw), sn = Math.sin(t.yaw);
                    double ox = c * 0.7 * t.size, oz = sn * 0.7 * t.size;
                    double e1 = com.ironlung.ocean.Sdf.sphere(lx - ox, ly, lz - oz, 0.22 * t.size);
                    double e2 = com.ironlung.ocean.Sdf.sphere(lx + ox, ly, lz + oz, 0.22 * t.size);
                    d = Math.min(e1, e2);
                    alb = 2.2;
                    flesh = 0.0;
                    break;
                }
                case Transient.FACE: {
                    double c = Math.cos(t.yaw), sn = Math.sin(t.yaw);
                    double fs = t.size;
                    double qz = (lx * sn - lz * c) / fs;   // toward viewer
                    double qx = (lx * c + lz * sn) / fs;   // right
                    double qy = ly / fs;
                    double[] fm = new double[3];
                    d = Finale.sdf(qx, qy, qz, fm) * fs;
                    alb = fm[0];
                    flesh = fm[1];
                    wet = fm[2];
                    break;
                }
                default: { // FIGURE
                    double body = com.ironlung.ocean.Sdf.capsuleY(lx, ly, lz, 8.0 * t.size, 0.42 * t.size);
                    double head = com.ironlung.ocean.Sdf.sphere(lx, ly - 9.0 * t.size, lz, 0.75 * t.size);
                    d = Math.min(body, head);
                    alb = 0.95;
                    flesh = 0.0;
                    break;
                }
            }
            if (d < best) {
                best = d;
                if (mat != null) { mat[0] = alb; mat[1] = flesh; mat[2] = wet >= 0 ? wet : (alb > 1.2 ? 1.0 : 0.25); }
            }
        }
        return best;
    }

    /**
     * Render one frame into argb (0xAARRGGBB).
     * Camera pose: position (x, y up, z), yaw (0 = north/-z, clockwise), pitch (up positive), radians.
     */
    public void render(int[] argb, int w, int h, double cx, double cy, double cz, double yaw, double pitch, Params p) {
        ensureCache(cx, cz);
        final double maxDist = Math.min(90, p.lightRange * 2.0);
        List<Poi> near = map.poisNear(cx, cz, maxDist + 5);
        List<Transient> trans = new ArrayList<>();
        for (Transient t : p.transients) if (t.alive(p.time)) trans.add(t);
        final Scene scene = new Scene(near, trans, p.time);

        final double cp = Math.cos(pitch), sp = Math.sin(pitch);
        final double fx = Math.sin(yaw) * cp, fy = sp, fz = -Math.cos(yaw) * cp;
        final double rx = Math.cos(yaw), rz = Math.sin(yaw); // right vector (y = 0)
        // up = right x forward
        final double ux = -(rz * fy), uy = rz * fx - rx * fz, uz = rx * fy;
        final double tanHalf = Math.tan(Math.toRadians(p.fovDeg) / 2);
        final double aspect = w / (double) h;
        final double absorb = 1.15 / p.lightRange;
        final double cosOuter = Math.cos(Math.toRadians(p.fovDeg) * 0.62);
        final double lightMul = p.light * (p.photo ? 1.9 : 1.7);
        final long frame = p.frame;
        final boolean photo = p.photo;
        final double grain = p.grain;
        final double[] lum = new double[w * h];
        final double[] red = new double[w * h];

        IntStream.range(0, h).parallel().forEach(j -> {
            double[] mat = new double[3];
            double[] tmp = new double[3];
            for (int i = 0; i < w; i++) {
                double sx = (2 * (i + 0.5) / w - 1) * tanHalf * aspect;
                double sy = (1 - 2 * (j + 0.5) / h) * tanHalf;
                double dx = fx + rx * sx + ux * sy;
                double dy = fy + uy * sy;
                double dz = fz + rz * sx + uz * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl;
                dy /= dl;
                dz /= dl;
                double cosA = dx * fx + dy * fy + dz * fz;
                double spot = Math.max(0, (cosA - cosOuter) / (1 - cosOuter));
                spot = spot * spot * (3 - 2 * spot);
                spot = 0.025 + 0.975 * spot;

                double t = 0.35;
                double haze = 0;
                int hit = 0; // 0 none, 1 floor, 2 object
                double hx = 0, hy = 0, hz = 0;
                for (int step = 0; step < 140 && t < maxDist; step++) {
                    double px = cx + dx * t, py = cy + dy * t, pz = cz + dz * t;
                    double fyv = floorY(px, pz);
                    double dh = py - fyv;
                    if (dh < 0.03) {
                        hit = 1;
                        hx = px; hy = py; hz = pz;
                        break;
                    }
                    double ds = scene.pois.isEmpty() && scene.trans.isEmpty() ? 1e9 : sceneSdf(scene, px, py, pz, null, tmp);
                    if (ds < 0.03) {
                        hit = 2;
                        hx = px; hy = py; hz = pz;
                        break;
                    }
                    double stepLen = Math.min(Math.max(dh * 0.42, 0.12), ds * 0.9);
                    stepLen = Math.max(0.04, Math.min(stepLen, 2.2));
                    double att = Math.exp(-2 * t * absorb);
                    haze += spot * spot * att * stepLen * 0.0085;
                    t += stepLen;
                }
                double L = haze * lightMul;
                double flesh = 0.25;
                if (hit != 0) {
                    double nx, ny, nz, albedo, wet = 0.05, occ = 1.0;
                    if (hit == 1) {
                        double e = 0.25;
                        nx = floorY(hx - e, hz) - floorY(hx + e, hz);
                        nz = floorY(hx, hz - e) - floorY(hx, hz + e);
                        ny = 2 * e;
                        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        nx /= nl; ny /= nl; nz /= nl;
                        double sed = sediment(hx, hz);
                        albedo = 0.22 + 0.28 * sed;
                        // steep rock faces are darker and rougher
                        albedo *= 0.7 + 0.3 * ny;
                        flesh = 0.30 + 0.25 * (1 - sed);
                    } else {
                        double e = 0.05;
                        double d0 = sceneSdf(scene, hx, hy, hz, mat, tmp);
                        albedo = mat[0];
                        flesh = mat[1];
                        nx = sceneSdf(scene, hx + e, hy, hz, null, tmp) - d0;
                        ny = sceneSdf(scene, hx, hy + e, hz, null, tmp) - d0;
                        nz = sceneSdf(scene, hx, hy, hz + e, null, tmp) - d0;
                        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
                        nx /= nl; ny /= nl; nz /= nl;
                        wet = mat[2];
                        // crevice shadowing: how buried is this point?
                        for (int k = 1; k <= 4; k++) {
                            double hstep = 0.09 * k;
                            double dd = sceneSdf(scene, hx + nx * hstep, hy + ny * hstep, hz + nz * hstep, null, tmp);
                            occ -= (hstep - dd) * (1.6 / k);
                        }
                        occ = Math.max(0.12, Math.min(1, occ));
                    }
                    double lambert = Math.max(0, -(nx * dx + ny * dy + nz * dz));
                    double att = Math.exp(-2 * t * absorb) / (1 + 0.0007 * t * t);
                    double spec = (albedo > 1.2 ? Math.pow(lambert, 24) * 1.5 : 0) + Math.pow(lambert, 48) * wet * 0.85;
                    L += (albedo * (0.12 + 0.88 * lambert) * occ + spec * Math.sqrt(occ)) * spot * att * lightMul * 1.25;
                }
                // drifting particulate caught in the beam
                long hsh = mix(frame * 0x9E3779B97F4A7C15L + (long) i * 73856093L + (long) j * 19349663L);
                double r01 = (hsh >>> 11) * 0x1.0p-53;
                if (r01 < (photo ? 0.0035 : 0.004) * spot * spot) {
                    L += (photo ? 0.55 : 0.35) * spot * lightMul;
                }
                // sensor grain
                double g = ((mix(hsh) >>> 11) * 0x1.0p-53 - 0.5);
                L += g * grain * 0.16;
                lum[j * w + i] = L;
                red[j * w + i] = flesh;
            }
        });

        postProcess(argb, lum, red, w, h, p);
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static void soften(double[] a, int w, int h) {
        double[] src = a.clone();
        for (int j = 1; j < h - 1; j++) {
            for (int i = 1; i < w - 1; i++) {
                int k = j * w + i;
                a[k] = (src[k] * 4 + (src[k - 1] + src[k + 1] + src[k - w] + src[k + w]) * 2
                        + src[k - w - 1] + src[k - w + 1] + src[k + w - 1] + src[k + w + 1]) / 16.0;
            }
        }
    }

    private static void postProcess(int[] argb, double[] lum, double[] red, int w, int h, Params p) {
        if (p.photo) {
            soften(lum, w, h);
            soften(red, w, h);
        }
        java.util.Random rnd = new java.util.Random(p.frame * 31 + 7);
        int[] shift = new int[h];
        if (p.glitch > 0) {
            int bands = 1 + (int) (p.glitch * 6);
            for (int b = 0; b < bands; b++) {
                if (rnd.nextDouble() > p.glitch) continue;
                int y0 = rnd.nextInt(h);
                int bh = 1 + rnd.nextInt(Math.max(2, h / 8));
                int off = (int) ((rnd.nextDouble() - 0.5) * w * 0.25 * p.glitch);
                for (int y = y0; y < Math.min(h, y0 + bh); y++) shift[y] = off;
            }
        }
        double roll = p.glitch > 0.3 ? (p.frame * 3 % h) : -1;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int si = Math.floorMod(i + shift[j], w);
                double L = lum[j * w + si];
                double fl = red[j * w + si];
                double u = (i + 0.5) / w - 0.5, v = (j + 0.5) / h - 0.5;
                double vig = 1.0 - (u * u + v * v) * 1.5;
                L *= Math.max(0.15, vig);
                if (!p.photo && (j & 1) == 1) L *= 0.86; // scanlines on the live feed
                if (roll >= 0 && Math.abs(j - roll) < 3) L += 0.12 * p.glitch;
                if (p.glitch > 0 && rnd.nextDouble() < p.glitch * 0.02) L = rnd.nextDouble() * 0.6;
                L = Math.max(0, L);
                L = L / (1 + L * 0.35); // soft shoulder
                L = Math.pow(L, 0.8);
                double R = Math.min(1, 0.012 + L * (1.02 + 0.10 * fl + Math.min(0, fl) * 0.25));
                double G = Math.min(1, L * (0.80 - 0.22 * fl) * Math.pow(Math.max(L, 1e-4), 0.12));
                double B = Math.min(1, L * (0.70 - 0.30 * fl) * Math.pow(Math.max(L, 1e-4), 0.22));
                int r = (int) (R * 255), g = (int) (G * 255), b = (int) (B * 255);
                argb[j * w + i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }
}
