package com.railgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Tiny baked mesh builder. Shapes are lathed/superellipse tubes and spheres with smooth per-vertex normals,
 * so nothing reads as a cube. Built once, replayed every frame.
 */
public final class Mesh {
    private static final int STRIDE = 10; // xyz nxyz rgba
    private float[] d = new float[STRIDE * 2048];
    private int n;
    private final Matrix4f m = new Matrix4f();
    private final Matrix3f nm = new Matrix3f();
    private final ArrayDeque<Matrix4f> stack = new ArrayDeque<>();
    private final Vector3f tp = new Vector3f(), tn = new Vector3f();

    public Mesh push() { stack.push(new Matrix4f(m)); return this; }
    public Mesh pop() { m.set(stack.pop()); m.normal(nm); return this; }
    public Mesh translate(float x, float y, float z) { m.translate(x, y, z); m.normal(nm); return this; }
    public Mesh rotX(float deg) { m.rotateX((float) Math.toRadians(deg)); m.normal(nm); return this; }
    public Mesh rotY(float deg) { m.rotateY((float) Math.toRadians(deg)); m.normal(nm); return this; }
    public Mesh rotZ(float deg) { m.rotateZ((float) Math.toRadians(deg)); m.normal(nm); return this; }

    private void vtx(float x, float y, float z, float nx, float ny, float nz, float[] c) {
        if ((n + 1) * STRIDE > d.length) d = Arrays.copyOf(d, d.length * 2);
        m.transformPosition(x, y, z, tp);
        nm.transform(nx, ny, nz, tn);
        tn.normalize();
        int i = n * STRIDE;
        d[i] = tp.x; d[i + 1] = tp.y; d[i + 2] = tp.z;
        d[i + 3] = tn.x; d[i + 4] = tn.y; d[i + 5] = tn.z;
        d[i + 6] = c[0]; d[i + 7] = c[1]; d[i + 8] = c[2]; d[i + 9] = c.length > 3 ? c[3] : 1f;
        n++;
    }

    private static float sgn(float v) { return v < 0 ? -1f : 1f; }

    /**
     * Tube along Z. Cross-section is a superellipse: ex=2 is a circle, ex=4 a heavily rounded box.
     * Radii can taper between z0 and z1.
     */
    public Mesh tube(float z0, float z1, float rx0, float ry0, float rx1, float ry1, float ex, int seg,
                     float[] col, boolean cap0, boolean cap1) {
        float p = 2f / ex;
        float len = z1 - z0;
        float nz = len == 0 ? 0 : -(((rx1 + ry1) - (rx0 + ry0)) * 0.5f) / len;
        float rxa = (rx0 + rx1) * 0.5f, rya = (ry0 + ry1) * 0.5f;
        float[] ux = new float[seg + 1], uy = new float[seg + 1], nx = new float[seg + 1], ny = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            ux[i] = sgn(c) * (float) Math.pow(Math.abs(c), p);
            uy[i] = sgn(s) * (float) Math.pow(Math.abs(s), p);
            float gx = sgn(c) * (float) Math.pow(Math.abs(c), 2f - p) / Math.max(rxa, 1e-4f);
            float gy = sgn(s) * (float) Math.pow(Math.abs(s), 2f - p) / Math.max(rya, 1e-4f);
            float gl = (float) Math.sqrt(gx * gx + gy * gy);
            nx[i] = gx / gl; ny[i] = gy / gl;
        }
        for (int i = 0; i < seg; i++) {
            int j = i + 1;
            vtx(rx0 * ux[i], ry0 * uy[i], z0, nx[i], ny[i], nz, col);
            vtx(rx0 * ux[j], ry0 * uy[j], z0, nx[j], ny[j], nz, col);
            vtx(rx1 * ux[j], ry1 * uy[j], z1, nx[j], ny[j], nz, col);
            vtx(rx1 * ux[i], ry1 * uy[i], z1, nx[i], ny[i], nz, col);
        }
        float out0 = len > 0 ? -1f : 1f;
        if (cap0) cap(z0, rx0, ry0, ux, uy, seg, out0, col);
        if (cap1) cap(z1, rx1, ry1, ux, uy, seg, -out0, col);
        return this;
    }

    private void cap(float z, float rx, float ry, float[] ux, float[] uy, int seg, float nz, float[] col) {
        for (int i = 0; i < seg; i++) {
            int j = i + 1;
            vtx(0, 0, z, 0, 0, nz, col);
            vtx(rx * ux[i], ry * uy[i], z, 0, 0, nz, col);
            vtx(rx * ux[j], ry * uy[j], z, 0, 0, nz, col);
            vtx(rx * ux[j], ry * uy[j], z, 0, 0, nz, col);
        }
    }

    public Mesh sphere(float cx, float cy, float cz, float r, int seg, int rings, float[] col) {
        for (int j = 0; j < rings; j++) {
            double l0 = Math.PI * ((double) j / rings - 0.5), l1 = Math.PI * ((double) (j + 1) / rings - 0.5);
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
                sv(cx, cy, cz, r, l0, a0, col); sv(cx, cy, cz, r, l0, a1, col);
                sv(cx, cy, cz, r, l1, a1, col); sv(cx, cy, cz, r, l1, a0, col);
            }
        }
        return this;
    }

    private void sv(float cx, float cy, float cz, float r, double lat, double lon, float[] col) {
        float x = (float) (Math.cos(lat) * Math.cos(lon)), y = (float) Math.sin(lat), z = (float) (Math.cos(lat) * Math.sin(lon));
        vtx(cx + x * r, cy + y * r, cz + z * r, x, y, z, col);
    }

    private static int b(float f) { return f <= 0 ? 0 : (f >= 1 ? 255 : (int) (f * 255f)); }

    /** Lit, shaded solid draw (use with entityCutoutNoCull(white)). */
    public void drawLit(VertexConsumer vc, PoseStack.Pose pose, int light, int overlay) {
        Matrix4f pm = pose.pose();
        Matrix3f pn = pose.normal();
        for (int k = 0; k < n; k++) {
            int i = k * STRIDE;
            vc.vertex(pm, d[i], d[i + 1], d[i + 2])
                    .color(b(d[i + 6]), b(d[i + 7]), b(d[i + 8]), 255)
                    .uv(0.5f, 0.5f).overlayCoords(overlay).uv2(light)
                    .normal(pn, d[i + 3], d[i + 4], d[i + 5]).endVertex();
        }
    }

    /** Additive glow draw. expand pushes vertices out along their normals for cheap bloom shells. */
    public void drawGlow(VertexConsumer vc, PoseStack.Pose pose, float k, float expand, float tr, float tg, float tb) {
        if (k <= 0.002f) return;
        Matrix4f pm = pose.pose();
        for (int v = 0; v < n; v++) {
            int i = v * STRIDE;
            vc.vertex(pm, d[i] + d[i + 3] * expand, d[i + 1] + d[i + 4] * expand, d[i + 2] + d[i + 5] * expand)
                    .color(b(d[i + 6] * tr), b(d[i + 7] * tg), b(d[i + 8] * tb), b(d[i + 9] * k)).endVertex();
        }
    }
}
