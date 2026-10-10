package com.sixpaths.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Smooth organic geometry with anime cel shading and inked silhouettes.
 *
 * Every model is drawn twice: an OUTLINE pass (the mesh pushed out along its normals, drawn
 * black with front faces culled — the classic inverted-hull ink line) and a FILL pass
 * (three-band cel lighting + a rim highlight). All quads are wound counter-clockwise when
 * seen from outside, which the outline trick depends on.
 */
public final class Mesh {
    public static final int OUTLINE = 0, FILL = 1;
    /** Colours returned with this alpha are emissive (unlit, glowing). */
    public static final int EMISSIVE = 0xFE000000;
    private static final int INK = 0xFF06040A;
    private static final Vec3 LIGHT = new Vec3(0.35, 0.85, 0.4).normalize();

    private static int mode = FILL;
    private static VertexConsumer vc;
    private static Matrix4f m;
    private static Vec3 cam = Vec3.ZERO;
    private static float bright = 1f;
    private static float hurt = 0f;
    public static double ink = 0.035;

    public static void begin(int passMode, VertexConsumer consumer, Matrix4f matrix, Vec3 camRel, float brightness, float hurtFlash) {
        mode = passMode; vc = consumer; m = matrix; cam = camRel; bright = brightness; hurt = hurtFlash;
    }

    public static void matrix(Matrix4f matrix) {
        m = matrix;
    }

    public static boolean outlinePass() {
        return mode == OUTLINE;
    }

    public static int emissive(int rgb) {
        return (rgb & 0xFFFFFF) | EMISSIVE;
    }

    private static int shade(int base, Vec3 n, Vec3 p) {
        double d = n.dot(LIGHT);
        float band = d > 0.32 ? 1f : d > -0.22 ? 0.74f : 0.52f;
        int c = Draw.scaleRgb(base | 0xFF000000, band * bright);
        Vec3 v = cam.subtract(p);
        double vl = v.length();
        if (vl > 1.0e-4) {
            double rim = 1 - Math.abs(n.dot(v.scale(1 / vl)));
            if (rim > 0.8 && d > -0.3) c = Draw.lerp(c, 0xFFFFFFFF, 0.2f);
        }
        if (hurt > 0) c = Draw.lerp(c, 0xFFFF2020, hurt * 0.55f);
        return c;
    }

    private static void vert(Vec3 p, Vec3 n, int col) {
        if (mode == OUTLINE) {
            Draw.v(vc, m, p.add(n.scale(ink)), INK);
            return;
        }
        boolean glow = (col & 0xFF000000) == EMISSIVE;
        Draw.v(vc, m, p, glow ? (col | 0xFF000000) : shade(col, n, p));
    }

    // ───────────────────────────── primitives ─────────────────────────────

    public interface Paint {
        int at(double u, Vec3 n);
    }

    /** A swept tube through the spine points with per-point radius. Parallel-transport frames, no twisting. */
    public static void tube(Vec3[] pts, double[] radii, int sides, Paint paint) {
        int n = pts.length;
        if (n < 2) return;
        Vec3[] tan = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)];
            Vec3 t = b.subtract(a);
            tan[i] = t.lengthSqr() < 1.0e-10 ? (i > 0 ? tan[i - 1] : Draw.Y) : t.normalize();
        }
        Vec3[] nor = new Vec3[n], bin = new Vec3[n];
        nor[0] = Draw.perp(tan[0]);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                Vec3 prev = nor[i - 1];
                Vec3 proj = prev.subtract(tan[i].scale(prev.dot(tan[i])));
                nor[i] = proj.lengthSqr() < 1.0e-8 ? Draw.perp(tan[i]) : proj.normalize();
            }
            bin[i] = tan[i].cross(nor[i]);
        }
        Vec3[][] ring = new Vec3[n][sides + 1];
        Vec3[][] rn = new Vec3[n][sides + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= sides; j++) {
                double th = 2 * Math.PI * j / sides;
                Vec3 dir = nor[i].scale(Math.cos(th)).add(bin[i].scale(Math.sin(th)));
                rn[i][j] = dir;
                ring[i][j] = pts[i].add(dir.scale(radii[i]));
            }
        }
        for (int i = 0; i < n - 1; i++) {
            double u0 = i / (double) (n - 1), u1 = (i + 1) / (double) (n - 1);
            for (int j = 0; j < sides; j++) {
                vert(ring[i][j], rn[i][j], paint.at(u0, rn[i][j]));
                vert(ring[i][j + 1], rn[i][j + 1], paint.at(u0, rn[i][j + 1]));
                vert(ring[i + 1][j + 1], rn[i + 1][j + 1], paint.at(u1, rn[i + 1][j + 1]));
                vert(ring[i + 1][j], rn[i + 1][j], paint.at(u1, rn[i + 1][j]));
            }
        }
    }

    public static void tube(Vec3[] pts, double[] radii, int sides, int col) {
        tube(pts, radii, sides, (u, nn) -> col);
    }

    /** Tapered tube with a linear radius profile. */
    public static void limb(Vec3[] pts, double r0, double r1, int sides, int col) {
        double[] r = new double[pts.length];
        for (int i = 0; i < r.length; i++) r[i] = r0 + (r1 - r0) * i / (double) Math.max(1, r.length - 1);
        tube(pts, r, sides, col);
    }

    public static void limb(Vec3[] pts, double r0, double r1, int sides, Paint paint) {
        double[] r = new double[pts.length];
        for (int i = 0; i < r.length; i++) r[i] = r0 + (r1 - r0) * i / (double) Math.max(1, r.length - 1);
        tube(pts, r, sides, paint);
    }

    public static void spike(Vec3 base, Vec3 tip, double r, int col) {
        tube(new Vec3[]{base, base.lerp(tip, 0.5), tip}, new double[]{r, r * 0.55, 0.003}, 7, col);
    }

    /** Smooth curve through a quadratic Bézier, tapering r0 → r1. */
    public static void curve(Vec3 a, Vec3 ctrl, Vec3 b, int segs, double r0, double r1, int sides, Paint paint) {
        Vec3[] pts = bezier(a, ctrl, b, segs);
        limb(pts, r0, r1, sides, paint);
    }

    public static void curve(Vec3 a, Vec3 ctrl, Vec3 b, int segs, double r0, double r1, int sides, int col) {
        curve(a, ctrl, b, segs, r0, r1, sides, (u, n) -> col);
    }

    public static Vec3[] bezier(Vec3 a, Vec3 ctrl, Vec3 b, int segs) {
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs, it = 1 - t;
            pts[i] = a.scale(it * it).add(ctrl.scale(2 * it * t)).add(b.scale(t * t));
        }
        return pts;
    }

    public static Vec3[] bezier3(Vec3 a, Vec3 c1, Vec3 c2, Vec3 b, int segs) {
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs, it = 1 - t;
            pts[i] = a.scale(it * it * it).add(c1.scale(3 * it * it * t)).add(c2.scale(3 * it * t * t)).add(b.scale(t * t * t));
        }
        return pts;
    }

    /** Ellipsoid with semi-axes ax, ay, az (vectors). Handedness is fixed up automatically. */
    public static void ellipsoid(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, int lat, int lon, Paint paint) {
        if (ax.dot(ay.cross(az)) < 0) az = az.scale(-1);
        double lx = Math.max(1.0e-6, ax.lengthSqr()), ly = Math.max(1.0e-6, ay.lengthSqr()), lz = Math.max(1.0e-6, az.lengthSqr());
        Vec3[][] p = new Vec3[lat + 1][lon + 1];
        Vec3[][] nn = new Vec3[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            double y = Math.cos(th), s = Math.sin(th);
            for (int j = 0; j <= lon; j++) {
                double ph = 2 * Math.PI * j / lon;
                double x = s * Math.cos(ph), z = s * Math.sin(ph);
                p[i][j] = c.add(ax.scale(x)).add(ay.scale(y)).add(az.scale(z));
                Vec3 nv = ax.scale(x / lx).add(ay.scale(y / ly)).add(az.scale(z / lz));
                nn[i][j] = nv.lengthSqr() < 1.0e-12 ? Draw.Y : nv.normalize();
            }
        }
        for (int i = 0; i < lat; i++) {
            double u0 = i / (double) lat, u1 = (i + 1) / (double) lat;
            for (int j = 0; j < lon; j++) {
                vert(p[i][j], nn[i][j], paint.at(u0, nn[i][j]));
                vert(p[i][j + 1], nn[i][j + 1], paint.at(u0, nn[i][j + 1]));
                vert(p[i + 1][j + 1], nn[i + 1][j + 1], paint.at(u1, nn[i + 1][j + 1]));
                vert(p[i + 1][j], nn[i + 1][j], paint.at(u1, nn[i + 1][j]));
            }
        }
    }

    public static void ellipsoid(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, int col) {
        ellipsoid(c, ax, ay, az, 10, 14, (u, n) -> col);
    }

    /** Ellipsoid oriented by a frame: side/up/fwd radii along the frame's axes. */
    public static void blob(Vec3 c, Frame f, double rs, double ru, double rf, int col) {
        ellipsoid(c, f.side.scale(rs), f.up.scale(ru), f.fwd.scale(rf), 10, 14, (u, n) -> col);
    }

    public static void blob(Vec3 c, Frame f, double rs, double ru, double rf, Paint paint) {
        ellipsoid(c, f.side.scale(rs), f.up.scale(ru), f.fwd.scale(rf), 10, 14, paint);
    }

    public static void ball(Vec3 c, double r, int col) {
        ellipsoid(c, Draw.X.scale(r), Draw.Y.scale(r), Draw.Z.scale(r), 7, 10, (u, n) -> col);
    }

    /** Closed ring (torus) of tube radius tr around centre c in the plane of a, b. */
    public static void torus(Vec3 c, Vec3 a, Vec3 b, double R, double tr, int segs, int col) {
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double th = 2 * Math.PI * i / segs;
            pts[i] = c.add(a.scale(Math.cos(th) * R)).add(b.scale(Math.sin(th) * R));
        }
        double[] r = new double[segs + 1];
        java.util.Arrays.fill(r, tr);
        tube(pts, r, 8, col);
    }

    // ───────────────────────────── frames ─────────────────────────────

    /** Orthonormal right-handed frame: side = up × fwd. */
    public static final class Frame {
        public final Vec3 side, up, fwd;

        public Frame(Vec3 side, Vec3 up, Vec3 fwd) {
            this.side = side; this.up = up; this.fwd = fwd;
        }

        public static Frame of(Vec3 fwd, Vec3 upHint) {
            Vec3 f = fwd.lengthSqr() < 1.0e-10 ? Draw.Z : fwd.normalize();
            Vec3 s = upHint.cross(f);
            if (s.lengthSqr() < 1.0e-8) s = Draw.perp(f);
            s = s.normalize();
            Vec3 u = f.cross(s).normalize();
            return new Frame(s, u, f);
        }

        public static Frame yaw(float yawDeg) {
            double y = Math.toRadians(yawDeg);
            Vec3 f = new Vec3(-Math.sin(y), 0, Math.cos(y));
            return of(f, Draw.Y);
        }

        /** Local (side, up, fwd) → world offset. */
        public Vec3 l(double s, double u, double f) {
            return side.scale(s).add(up.scale(u)).add(fwd.scale(f));
        }

        /** Rotate this frame's forward toward up by an angle (pitch) about the side axis. */
        public Frame pitch(double rad) {
            Vec3 f2 = fwd.scale(Math.cos(rad)).add(up.scale(Math.sin(rad)));
            return of(f2, up.scale(Math.cos(rad)).subtract(fwd.scale(Math.sin(rad))));
        }

        public Frame yawBy(double rad) {
            Vec3 f2 = fwd.scale(Math.cos(rad)).add(side.scale(Math.sin(rad)));
            return of(f2, up);
        }
    }

    private Mesh() {}
}
