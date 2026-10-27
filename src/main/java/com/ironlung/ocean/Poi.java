package com.ironlung.ocean;

import static com.ironlung.ocean.Sdf.*;

/** A point of interest resting on the sea floor. Coordinates: x/z on the survey map, y up (negative = depth). */
public final class Poi {
    public final int index;
    public final PoiType type;
    public final double x, z;
    /** World y of the floor under the POI centre (negative). */
    public double baseY;
    public final double yaw;
    public final double scale;
    public final long seed;
    public final boolean catalogued;
    /** Bounding radius in metres, used for culling. */
    public final double radius;
    /** Height of the visual centre above the floor, used for photo checks. */
    public final double centerHeight;

    private final double[] blobs; // for GROWTH: x,y,z,r quads

    public Poi(int index, PoiType type, double x, double z, double yaw, double scale, long seed, boolean catalogued) {
        this.index = index;
        this.type = type;
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        this.scale = scale;
        this.seed = seed;
        this.catalogued = catalogued;
        double r;
        double ch;
        switch (type) {
            case WRECK: r = 12; ch = 2; break;
            case RIBCAGE: r = 11; ch = 2; break;
            case GROWTH: r = 9; ch = 3; break;
            case SPIRE: r = 18; ch = 7; break;
            case MONOLITH: r = 16; ch = 7; break;
            case CARCASS: r = 17; ch = 2; break;
            case EYE_ROCK: r = 8; ch = 2; break;
            case SKULL: r = 7; ch = 2; break;
            default: r = 11; ch = 5; break;
        }
        this.radius = r * scale;
        this.centerHeight = ch * scale;
        if (type == PoiType.GROWTH) {
            java.util.Random rnd = new java.util.Random(seed);
            int n = 7;
            blobs = new double[n * 4];
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = rnd.nextDouble() * 3.5;
                blobs[i * 4] = Math.cos(a) * d;
                blobs[i * 4 + 1] = rnd.nextDouble() * 4.5;
                blobs[i * 4 + 2] = Math.sin(a) * d;
                blobs[i * 4 + 3] = 1.1 + rnd.nextDouble() * 2.0;
            }
        } else {
            blobs = null;
        }
    }

    /**
     * Signed distance from a world point to this object.
     * @param mat out: [0]=albedo, [1]=flesh tint; written only when this object is the closest.
     */
    public double sdf(double wx, double wy, double wz, double[] mat) {
        double lx = (wx - x) / scale, lz = (wz - z) / scale, ly = (wy - baseY) / scale;
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double rx = lx * c + lz * s;
        double rz = -lx * s + lz * c;
        double d;
        double albedo = type.albedo;
        double flesh = type.flesh;
        switch (type) {
            case WRECK: {
                // tilted hull lying on its side, a conning tower, a torn-open stern
                double roll = 0.38;
                double cr = Math.cos(roll), sr = Math.sin(roll);
                double hx = rx * cr - (ly - 1.4) * sr;
                double hy = rx * sr + (ly - 1.4) * cr;
                double hull = box(hx, hy, rz, 2.0, 1.5, 9.0) - 0.4;
                double tower = box(hx, hy - 2.2, rz + 2.0, 0.8, 1.2, 1.5) - 0.15;
                d = Math.min(hull, tower);
                double tear = sphere(rx - 0.5, ly - 1.5, rz - 7.5, 2.8);
                d = Math.max(d, -tear);
                // bent mast
                d = Math.min(d, capsule(hx, hy, rz, 0, 3.2, -2, 1.5, 6.0, -4.5, 0.12));
                break;
            }
            case RIBCAGE: {
                double spine = capsule(rx, ly, rz, 0, 0.7, -8.5, 0, 0.9, 8.5, 0.55);
                double qz = rz - 2.3 * Math.rint(rz / 2.3);
                double ring = len(rx, ly - 0.7) - (3.4 - Math.abs(rz) * 0.12);
                double rib = len(ring, qz) - 0.28;
                if (Math.abs(rz) > 8.0) rib = Math.max(rib, Math.abs(rz) - 8.0);
                d = Math.min(spine, rib);
                d = Math.min(d, sphere(rx, ly - 1.0, rz - 10.0, 1.8)); // skull-ish end
                break;
            }
            case GROWTH: {
                d = 1e9;
                for (int i = 0; i < blobs.length; i += 4) {
                    double b = sphere(rx - blobs[i], ly - blobs[i + 1], rz - blobs[i + 2], blobs[i + 3]);
                    d = (d > 1e8) ? b : smin(d, b, 1.6);
                }
                d += 0.18 * Math.sin(rx * 3.1) * Math.sin(ly * 2.7) * Math.sin(rz * 3.3);
                // tendrils rising out of the mass
                d = Math.min(d, capsule(rx, ly, rz, 0.5, 3, 0.2, 1.6, 9.5, -0.8, 0.22));
                d = Math.min(d, capsule(rx, ly, rz, -1.2, 2.5, 1.0, -2.6, 8.0, 2.1, 0.18));
                break;
            }
            case SPIRE: {
                double lean = 0.08;
                double sx = rx - ly * lean;
                d = capsuleY(sx, ly, rz, 17, 0.75 - ly * 0.02);
                for (int i = 0; i < 3; i++) {
                    double hy = 5 + i * 4.2;
                    d = smin(d, sphere(rx - hy * lean, ly - hy, rz, 1.4 - i * 0.2), 0.9);
                }
                break;
            }
            case MONOLITH: {
                d = box(rx, ly - 7.0, rz, 1.6, 7.0, 0.45);
                break;
            }
            case CARCASS: {
                double body = ellipsoid(rx, ly - 1.0, rz, 3.6, 2.6, 13.0);
                body += 0.22 * Math.sin(rz * 2.2) * Sdf.smoothstep(-1, 2, ly);
                double tail = capsule(rx, ly, rz, 0, 1.0, 12, 1.5, 0.6, 19, 0.7);
                double fin = ellipsoid(rx - 3.5, ly - 0.6, rz + 3, 2.8, 0.35, 1.6);
                d = smin(Math.min(body, fin), tail, 1.2);
                break;
            }
            case EYE_ROCK: {
                double rock = sphere(rx, ly - 0.5, rz, 5.0) + 0.3 * Math.sin(rx * 1.7 + rz * 1.3) * Math.sin(ly * 2.1);
                double socket = sphere(rx, ly - 2.4, rz + 4.6, 1.9);
                rock = Math.max(rock, -socket);
                double eye = sphere(rx, ly - 2.4, rz + 4.3, 1.45);
                double pupil = ellipsoid(rx, ly - 2.4, rz + 5.62, 0.22, 0.75, 0.25);
                if (pupil < eye && pupil < rock) {
                    d = pupil;
                    albedo = 0.0;
                    flesh = 0.0;
                } else if (eye < rock) {
                    d = eye;
                    albedo = 1.35;
                    flesh = 0.15;
                } else {
                    d = rock;
                }
                break;
            }
            case SKULL: {
                double head = ellipsoid(rx, ly - 2.2, rz, 3.4, 3.0, 4.0);
                double jaw = box(rx, ly - 0.6, rz + 2.4, 2.2, 0.8, 1.6) - 0.3;
                d = smin(head, jaw, 1.0);
                double eyeL = sphere(rx - 1.4, ly - 2.6, rz + 3.5, 1.05);
                double eyeR = sphere(rx + 1.4, ly - 2.6, rz + 3.5, 1.05);
                d = Math.max(d, -Math.min(eyeL, eyeR));
                break;
            }
            default: { // FIGURE: a tall, pale, too-thin standing shape
                double body = capsuleY(rx, ly, rz, 8.0, 0.42);
                double head = sphere(rx, ly - 9.0, rz, 0.75);
                double armL = capsule(rx, ly, rz, -0.6, 7.6, 0, -1.0, 2.0, 0.3, 0.16);
                double armR = capsule(rx, ly, rz, 0.6, 7.6, 0, 1.0, 2.0, -0.2, 0.16);
                d = Math.min(Math.min(body, head), Math.min(armL, armR));
                break;
            }
        }
        if (mat != null) {
            mat[0] = albedo;
            mat[1] = flesh;
        }
        return d * scale;
    }

    public double centerY() {
        return baseY + centerHeight;
    }
}
