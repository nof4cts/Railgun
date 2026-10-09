package com.railgun.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Additive-glow primitives. All coordinates are already camera-relative. */
public final class Draw {
    private Draw() {}

    private static float c(float x) { return x < 0f ? 0f : Math.min(x, 1f); }

    static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(c(r), c(g), c(b), c(a)).endVertex();
    }

    /** returns {ux,uy,uz, vx,vy,vz}, two unit vectors perpendicular to n and each other */
    public static float[] basis(float nx, float ny, float nz) {
        float hx = 0, hy = 1, hz = 0;
        if (Math.abs(ny) > 0.9f) { hx = 1; hy = 0; }
        float ux = ny * hz - nz * hy, uy = nz * hx - nx * hz, uz = nx * hy - ny * hx;
        float l = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= l; uy /= l; uz /= l;
        float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        return new float[]{ux, uy, uz, vx, vy, vz};
    }

    /** open cylinder from o along d for len, with a radius/alpha gradient */
    public static void cylinder(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz,
                                float dx, float dy, float dz, float len, float[] bs,
                                float r0, float r1, int seg, float cr, float cg, float cb, float a0, float a1) {
        float ex = ox + dx * len, ey = oy + dy * len, ez = oz + dz * len;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float x0 = bs[0] * c0 + bs[3] * s0, y0 = bs[1] * c0 + bs[4] * s0, z0 = bs[2] * c0 + bs[5] * s0;
            float x1 = bs[0] * c1 + bs[3] * s1, y1 = bs[1] * c1 + bs[4] * s1, z1 = bs[2] * c1 + bs[5] * s1;
            v(vc, m, ox + x0 * r0, oy + y0 * r0, oz + z0 * r0, cr, cg, cb, a0);
            v(vc, m, ox + x1 * r0, oy + y1 * r0, oz + z1 * r0, cr, cg, cb, a0);
            v(vc, m, ex + x1 * r1, ey + y1 * r1, ez + z1 * r1, cr, cg, cb, a1);
            v(vc, m, ex + x0 * r1, ey + y0 * r1, ez + z0 * r1, cr, cg, cb, a1);
        }
    }

    /** soft-edged ring in the plane spanned by bs (u,v) */
    public static void softRing(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float[] bs,
                                float rad, float hw, int seg, float r, float g, float b, float a) {
        float ri = Math.max(0f, rad - hw), ro = rad + hw;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float x0 = bs[0] * c0 + bs[3] * s0, y0 = bs[1] * c0 + bs[4] * s0, z0 = bs[2] * c0 + bs[5] * s0;
            float x1 = bs[0] * c1 + bs[3] * s1, y1 = bs[1] * c1 + bs[4] * s1, z1 = bs[2] * c1 + bs[5] * s1;
            v(vc, m, cx + x0 * ri, cy + y0 * ri, cz + z0 * ri, r, g, b, 0);
            v(vc, m, cx + x0 * rad, cy + y0 * rad, cz + z0 * rad, r, g, b, a);
            v(vc, m, cx + x1 * rad, cy + y1 * rad, cz + z1 * rad, r, g, b, a);
            v(vc, m, cx + x1 * ri, cy + y1 * ri, cz + z1 * ri, r, g, b, 0);

            v(vc, m, cx + x0 * rad, cy + y0 * rad, cz + z0 * rad, r, g, b, a);
            v(vc, m, cx + x0 * ro, cy + y0 * ro, cz + z0 * ro, r, g, b, 0);
            v(vc, m, cx + x1 * ro, cy + y1 * ro, cz + z1 * ro, r, g, b, 0);
            v(vc, m, cx + x1 * rad, cy + y1 * rad, cz + z1 * rad, r, g, b, a);
        }
    }

    /** radial-gradient billboard disc (bright centre, transparent rim) */
    public static void glowDisc(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                float rx, float ry, float rz, float ux, float uy, float uz,
                                float rad, int seg, float r, float g, float b, float a) {
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(t0) * rad, s0 = (float) Math.sin(t0) * rad, c1 = (float) Math.cos(t1) * rad, s1 = (float) Math.sin(t1) * rad;
            v(vc, m, cx, cy, cz, r, g, b, a);
            v(vc, m, cx + rx * c0 + ux * s0, cy + ry * c0 + uy * s0, cz + rz * c0 + uz * s0, r, g, b, 0);
            v(vc, m, cx + rx * c1 + ux * s1, cy + ry * c1 + uy * s1, cz + rz * c1 + uz * s1, r, g, b, 0);
            v(vc, m, cx + rx * c1 + ux * s1, cy + ry * c1 + uy * s1, cz + rz * c1 + uz * s1, r, g, b, 0);
        }
    }

    /** a diamond spike through c along d (length L each way, half-width W) */
    public static void spike(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                             float dx, float dy, float dz, float px, float py, float pz,
                             float L, float W, float r, float g, float b, float a) {
        for (int s = -1; s <= 1; s += 2) {
            v(vc, m, cx + px * W, cy + py * W, cz + pz * W, r, g, b, a);
            v(vc, m, cx + dx * L * s, cy + dy * L * s, cz + dz * L * s, r, g, b, 0);
            v(vc, m, cx + dx * L * s, cy + dy * L * s, cz + dz * L * s, r, g, b, 0);
            v(vc, m, cx - px * W, cy - py * W, cz - pz * W, r, g, b, a);
        }
    }

    /** camera-facing lens-flare star */
    public static void star(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                            float rx, float ry, float rz, float ux, float uy, float uz,
                            int spikes, float rot, float L, float W, float r, float g, float b, float a) {
        for (int k = 0; k < spikes; k++) {
            float ang = rot + (float) Math.PI * k / spikes;
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            float dx = rx * ca + ux * sa, dy = ry * ca + uy * sa, dz = rz * ca + uz * sa;
            float px = -rx * sa + ux * ca, py = -ry * sa + uy * ca, pz = -rz * sa + uz * ca;
            float len = L * (k % 2 == 0 ? 1f : 0.62f);
            spike(vc, m, cx, cy, cz, dx, dy, dz, px, py, pz, len, W, r, g, b, a);
        }
    }

    private static void side(float[] p, int i, int n, int mode, float hw, float[] o) {
        int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
        float tx = p[i1 * 3] - p[i0 * 3], ty = p[i1 * 3 + 1] - p[i0 * 3 + 1], tz = p[i1 * 3 + 2] - p[i0 * 3 + 2];
        float rx, ry, rz;
        if (mode == 0) { rx = p[i * 3]; ry = p[i * 3 + 1]; rz = p[i * 3 + 2]; }
        else {
            rx = 0; ry = 1; rz = 0;
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz + 1e-9f);
            if (Math.abs(ty) / tl > 0.95f) { rx = 1; ry = 0; }
        }
        float sx = ty * rz - tz * ry, sy = tz * rx - tx * rz, sz = tx * ry - ty * rx;
        if (mode == 2) {
            float ax = sx, ay = sy, az = sz;
            sx = ty * az - tz * ay; sy = tz * ax - tx * az; sz = tx * ay - ty * ax;
        }
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6f) { o[0] = o[1] = o[2] = 0; return; }
        float k = hw / l;
        o[0] = sx * k; o[1] = sy * k; o[2] = sz * k;
    }

    /**
     * Soft-edged polyline ribbon. cross=false: faces the camera (points are camera-relative).
     * cross=true: two perpendicular ribbons, view-independent (used for in-hand arcs).
     */
    public static void ribbon(VertexConsumer vc, Matrix4f m, float[] p, int n, float w0, float w1,
                              float r, float g, float b, float a, boolean cross) {
        if (n < 2) return;
        float ea = a * 0.12f;
        float[] sa = new float[3], sb = new float[3];
        for (int pass = 0; pass < (cross ? 2 : 1); pass++) {
            int mode = cross ? pass + 1 : 0;
            for (int i = 0; i < n - 1; i++) {
                float hwA = Mth_lerp(w0, w1, i / (float) (n - 1)) * 0.5f;
                float hwB = Mth_lerp(w0, w1, (i + 1) / (float) (n - 1)) * 0.5f;
                side(p, i, n, mode, hwA, sa);
                side(p, i + 1, n, mode, hwB, sb);
                float ax = p[i * 3], ay = p[i * 3 + 1], az = p[i * 3 + 2];
                float bx = p[i * 3 + 3], by = p[i * 3 + 4], bz = p[i * 3 + 5];
                v(vc, m, ax - sa[0], ay - sa[1], az - sa[2], r, g, b, ea);
                v(vc, m, ax, ay, az, r, g, b, a);
                v(vc, m, bx, by, bz, r, g, b, a);
                v(vc, m, bx - sb[0], by - sb[1], bz - sb[2], r, g, b, ea);

                v(vc, m, ax, ay, az, r, g, b, a);
                v(vc, m, ax + sa[0], ay + sa[1], az + sa[2], r, g, b, ea);
                v(vc, m, bx + sb[0], by + sb[1], bz + sb[2], r, g, b, ea);
                v(vc, m, bx, by, bz, r, g, b, a);
            }
        }
    }

    private static float Mth_lerp(float a, float b, float t) { return a + (b - a) * t; }
}
