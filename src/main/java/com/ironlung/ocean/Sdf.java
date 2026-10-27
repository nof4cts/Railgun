package com.ironlung.ocean;

/** Signed distance helpers used to describe things on the sea floor. */
public final class Sdf {
    private Sdf() {}

    public static double len(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public static double len(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    public static double sphere(double x, double y, double z, double r) {
        return len(x, y, z) - r;
    }

    public static double box(double x, double y, double z, double bx, double by, double bz) {
        double qx = Math.abs(x) - bx, qy = Math.abs(y) - by, qz = Math.abs(z) - bz;
        double out = len(Math.max(qx, 0), Math.max(qy, 0), Math.max(qz, 0));
        double in = Math.min(Math.max(qx, Math.max(qy, qz)), 0);
        return out + in;
    }

    /** Vertical capsule from y=0 to y=h. */
    public static double capsuleY(double x, double y, double z, double h, double r) {
        double cy = Math.max(0, Math.min(h, y));
        return len(x, y - cy, z) - r;
    }

    /** Capsule between two points. */
    public static double capsule(double px, double py, double pz,
                                 double ax, double ay, double az,
                                 double bx, double by, double bz, double r) {
        double pax = px - ax, pay = py - ay, paz = pz - az;
        double bax = bx - ax, bay = by - ay, baz = bz - az;
        double dd = bax * bax + bay * bay + baz * baz;
        double h = dd < 1e-9 ? 0 : Math.max(0, Math.min(1, (pax * bax + pay * bay + paz * baz) / dd));
        return len(pax - bax * h, pay - bay * h, paz - baz * h) - r;
    }

    public static double ellipsoid(double x, double y, double z, double rx, double ry, double rz) {
        double k0 = len(x / rx, y / ry, z / rz);
        double k1 = len(x / (rx * rx), y / (ry * ry), z / (rz * rz));
        if (k1 < 1e-9) return -Math.min(rx, Math.min(ry, rz));
        return k0 * (k0 - 1.0) / k1;
    }

    public static double smin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    public static double smoothstep(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
