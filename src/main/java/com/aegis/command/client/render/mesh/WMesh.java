package com.aegis.command.client.render.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Hard-surface mesh builder for hand-authored, code-defined models (weapons, aircraft).
 * Primitives: boxes, arbitrary hexahedra (tapers, wedges, swept wings), cylinders/cones and rings,
 * all under a transform stack with arbitrary rotations. Quads are grouped by material and part
 * group so the renderer can tint, light, animate and route each to the right render type.
 * Coordinates are in model "pixels" (16 = one block) unless a different unit is chosen by the caller.
 */
public final class WMesh {
    // materials
    public static final int PAINT = 0, ACCENT = 1, DARK = 2, BLACK = 3, CHROME = 4, GRIP = 5, TRIM = 6,
            GLOW = 7, LENS = 8, HOT = 9;
    public static final int MATS = 10;
    // part groups
    public static final int MAIN = 0, MAG = 1, SPIN = 2, AUX = 3;
    public static final int GROUPS = 4;

    /** One quad: 4 xyz corners, a normal and a UV inset (0 = panel edges visible, 0.3 = smooth). */
    public record Quad(float[] p, float nx, float ny, float nz, float inset) {}

    @SuppressWarnings("unchecked")
    private final List<Quad>[][] quads = new List[GROUPS][MATS];
    private final Deque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f cur = new Matrix4f();
    private int group = MAIN;
    private float inset = 0f;

    public WMesh() {
        for (int g = 0; g < GROUPS; g++) for (int m = 0; m < MATS; m++) quads[g][m] = new ArrayList<>();
    }

    public List<Quad> quads(int group, int mat) { return quads[group][mat]; }

    public int quadCount() {
        int n = 0;
        for (List<Quad>[] g : quads) for (List<Quad> l : g) n += l.size();
        return n;
    }

    // ------------------------------------------------------------------ state

    public WMesh group(int g) { group = g; return this; }
    public WMesh smooth() { inset = 0.3f; return this; }
    public WMesh panel() { inset = 0f; return this; }
    public WMesh push() { stack.push(new Matrix4f(cur)); return this; }
    public WMesh pop() { cur = stack.pop(); return this; }
    public WMesh translate(float x, float y, float z) { cur.translate(x, y, z); return this; }
    public WMesh scale(float x, float y, float z) { cur.scale(x, y, z); return this; }
    public WMesh rotX(float deg) { cur.rotateX(deg * Mth.DEG_TO_RAD); return this; }
    public WMesh rotY(float deg) { cur.rotateY(deg * Mth.DEG_TO_RAD); return this; }
    public WMesh rotZ(float deg) { cur.rotateZ(deg * Mth.DEG_TO_RAD); return this; }

    /** Rotate about a pivot point. axis: 0=x, 1=y, 2=z */
    public WMesh rotAt(int axis, float deg, float px, float py, float pz) {
        cur.translate(px, py, pz);
        if (axis == 0) rotX(deg); else if (axis == 1) rotY(deg); else rotZ(deg);
        cur.translate(-px, -py, -pz);
        return this;
    }

    private Vector3f tf(float x, float y, float z) {
        return cur.transformPosition(new Vector3f(x, y, z));
    }

    // ------------------------------------------------------------------ core emitters

    /** Emit a quad, oriented so its front faces away from {@code inside}. */
    private void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f inside, int mat) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (n.lengthSquared() < 1e-10f) n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(a));
        if (n.lengthSquared() < 1e-10f) return;
        Vector3f centre = new Vector3f(a).add(b).add(c).add(d).mul(0.25f);
        if (n.dot(new Vector3f(centre).sub(inside)) < 0) {
            Vector3f t = b; b = d; d = t;
            n.negate();
        }
        n.normalize();
        quads[group][mat].add(new Quad(new float[]{a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z},
                n.x, n.y, n.z, inset));
    }

    /** Arbitrary hexahedron from 8 local corners: 0-3 the near face (counter-wise), 4-7 the matching far face. */
    public WMesh hexa(float[][] c, int mat) {
        Vector3f[] v = new Vector3f[8];
        Vector3f centre = new Vector3f();
        for (int i = 0; i < 8; i++) {
            v[i] = tf(c[i][0], c[i][1], c[i][2]);
            centre.add(v[i]);
        }
        centre.mul(1 / 8f);
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 2, 6, 7}, {0, 3, 7, 4}, {1, 2, 6, 5}};
        for (int[] f : faces) quad(v[f[0]], v[f[1]], v[f[2]], v[f[3]], centre, mat);
        return this;
    }

    public WMesh box(float x0, float y0, float z0, float x1, float y1, float z1, int mat) {
        return hexa(new float[][]{
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, mat);
    }

    /** Box mirrored across x = mx (for symmetric left/right parts). */
    public WMesh boxMirror(float mx, float x0, float y0, float z0, float x1, float y1, float z1, int mat) {
        box(x0, y0, z0, x1, y1, z1, mat);
        return box(2 * mx - x1, y0, z0, 2 * mx - x0, y1, z1, mat);
    }

    /**
     * Rectangular taper along Z: centred on (cx, cy), cross-section w0 x h0 at z0 and w1 x h1 at z1,
     * with an optional vertical offset of the far face (dy1) for raked noses and spines.
     */
    public WMesh taperZ(float cx, float cy, float z0, float w0, float h0, float z1, float w1, float h1, float dy1, int mat) {
        float a = w0 / 2, b = h0 / 2, c = w1 / 2, d = h1 / 2;
        return hexa(new float[][]{
                {cx - a, cy - b, z0}, {cx + a, cy - b, z0}, {cx + a, cy + b, z0}, {cx - a, cy + b, z0},
                {cx - c, cy - d + dy1, z1}, {cx + c, cy - d + dy1, z1}, {cx + c, cy + d + dy1, z1}, {cx - c, cy + d + dy1, z1}}, mat);
    }

    /**
     * A flat lifting surface. Root at x = xr spanning chord [zr0, zr1], tip at x = xt spanning [zt0, zt1],
     * thickness tr at the root and tt at the tip, centred on y (tip raised by dihedral).
     */
    public WMesh wing(float xr, float zr0, float zr1, float xt, float zt0, float zt1, float y, float tr, float tt, float dihedral, int mat) {
        float yt = y + dihedral;
        return hexa(new float[][]{
                {xr, y - tr / 2, zr0}, {xt, yt - tt / 2, zt0}, {xt, yt + tt / 2, zt0}, {xr, y + tr / 2, zr0},
                {xr, y - tr / 2, zr1}, {xt, yt - tt / 2, zt1}, {xt, yt + tt / 2, zt1}, {xr, y + tr / 2, zr1}}, mat);
    }

    /** Vertical fin: root on y = yr from zr0..zr1, tip at height yt from zt0..zt1, leaning sideways by lean. */
    public WMesh fin(float x, float yr, float zr0, float zr1, float yt, float zt0, float zt1, float t, float lean, int mat) {
        float h = t / 2;
        return hexa(new float[][]{
                {x - h, yr, zr0}, {x - h + lean, yt, zt0}, {x + h + lean, yt, zt0}, {x + h, yr, zr0},
                {x - h, yr, zr1}, {x - h + lean, yt, zt1}, {x + h + lean, yt, zt1}, {x + h, yr, zr1}}, mat);
    }

    /** Cylinder or cone along Z, centred on (cx, cy). */
    public WMesh cylZ(float cx, float cy, float z0, float z1, float r0, float r1, int sides, int mat, boolean caps) {
        Vector3f[] a = new Vector3f[sides], b = new Vector3f[sides];
        for (int i = 0; i < sides; i++) {
            double ang = (i + 0.5) / sides * Math.PI * 2;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            a[i] = tf(cx + c * r0, cy + s * r0, z0);
            b[i] = tf(cx + c * r1, cy + s * r1, z1);
        }
        Vector3f ca = tf(cx, cy, z0), cb = tf(cx, cy, z1);
        float prev = inset;
        inset = Math.max(inset, 0.28f);
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            Vector3f mid = new Vector3f(ca).add(cb).mul(0.5f);
            quad(a[i], a[j], b[j], b[i], mid, mat);
        }
        if (caps) {
            Vector3f inA = new Vector3f(ca).lerp(cb, 0.1f), inB = new Vector3f(cb).lerp(ca, 0.1f);
            for (int i = 0; i < sides; i++) {
                int j = (i + 1) % sides;
                if (r0 > 0.001f) quad(ca, a[i], a[j], a[j], inA, mat);
                if (r1 > 0.001f) quad(cb, b[i], b[j], b[j], inB, mat);
            }
        }
        inset = prev;
        return this;
    }

    /** Cylinder along X (axis through (cy, cz)). */
    public WMesh cylX(float x0, float x1, float cy, float cz, float r0, float r1, int sides, int mat, boolean caps) {
        push();
        cur.translate(x0, cy, cz);
        cur.rotateY((float) Math.PI / 2);
        cylZ(0, 0, 0, -(x1 - x0), r0, r1, sides, mat, caps);
        pop();
        return this;
    }

    /** Cylinder along Y (axis through (cx, cz)). */
    public WMesh cylY(float y0, float y1, float cx, float cz, float r0, float r1, int sides, int mat, boolean caps) {
        push();
        cur.translate(cx, y0, cz);
        cur.rotateX(-(float) Math.PI / 2);
        cylZ(0, 0, 0, y1 - y0, r0, r1, sides, mat, caps);
        pop();
        return this;
    }

    /** Annular ring around the Z axis: inner/outer radius, depth along Z. */
    public WMesh ringZ(float cx, float cy, float z0, float z1, float rIn, float rOut, int sides, int mat) {
        for (int i = 0; i < sides; i++) {
            double a0 = i / (double) sides * Math.PI * 2, a1 = (i + 1) / (double) sides * Math.PI * 2;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            hexa(new float[][]{
                    {cx + c0 * rIn, cy + s0 * rIn, z0}, {cx + c1 * rIn, cy + s1 * rIn, z0},
                    {cx + c1 * rOut, cy + s1 * rOut, z0}, {cx + c0 * rOut, cy + s0 * rOut, z0},
                    {cx + c0 * rIn, cy + s0 * rIn, z1}, {cx + c1 * rIn, cy + s1 * rIn, z1},
                    {cx + c1 * rOut, cy + s1 * rOut, z1}, {cx + c0 * rOut, cy + s0 * rOut, z1}}, mat);
        }
        return this;
    }

    /** Low-poly sphere (UV sphere) centred on a point. */
    public WMesh sphere(float cx, float cy, float cz, float r, int seg, int mat) {
        Vector3f c = tf(cx, cy, cz);
        float prev = inset;
        inset = 0.3f;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * i / seg - Math.PI / 2, t1 = Math.PI * (i + 1) / seg - Math.PI / 2;
            for (int j = 0; j < seg * 2; j++) {
                double p0 = Math.PI * j / seg, p1 = Math.PI * (j + 1) / seg;
                Vector3f a = tf(cx + (float) (Math.cos(t0) * Math.cos(p0) * r), cy + (float) (Math.sin(t0) * r), cz + (float) (Math.cos(t0) * Math.sin(p0) * r));
                Vector3f b = tf(cx + (float) (Math.cos(t0) * Math.cos(p1) * r), cy + (float) (Math.sin(t0) * r), cz + (float) (Math.cos(t0) * Math.sin(p1) * r));
                Vector3f d = tf(cx + (float) (Math.cos(t1) * Math.cos(p0) * r), cy + (float) (Math.sin(t1) * r), cz + (float) (Math.cos(t1) * Math.sin(p0) * r));
                Vector3f e = tf(cx + (float) (Math.cos(t1) * Math.cos(p1) * r), cy + (float) (Math.sin(t1) * r), cz + (float) (Math.cos(t1) * Math.sin(p1) * r));
                quad(a, b, e, d, c, mat);
            }
        }
        inset = prev;
        return this;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Emit every quad of one group+material with hand-tuned lighting: the vanilla diffuse shading
     * comes from the normals, and on top we add a view-space specular highlight and a coloured rim
     * so painted armour and chrome read as glossy metal. {@code unit} converts model units to blocks.
     */
    public void emit(PoseStack ps, VertexConsumer vc, int group, int mat, float unit,
                     float r, float g, float b, float a, float spec, float shininess, int rimRgb, float rim,
                     int light, int overlay) {
        List<Quad> list = quads[group][mat];
        if (list.isEmpty()) return;
        PoseStack.Pose last = ps.last();
        Matrix4f m = last.pose();
        Matrix3f nm = last.normal();
        float rr = ((rimRgb >> 16) & 255) / 255f, rg = ((rimRgb >> 8) & 255) / 255f, rb = (rimRgb & 255) / 255f;
        // light from the upper left, slightly toward the viewer; half vector with the view direction (+z)
        Vector3f h = new Vector3f(-0.35f, 0.75f, 0.55f).normalize().add(0, 0, 1).normalize();
        Vector3f n = new Vector3f();
        for (Quad q : list) {
            n.set(q.nx, q.ny, q.nz);
            nm.transform(n);
            if (n.lengthSquared() > 1e-8f) n.normalize();
            float cr = r, cg = g, cb = b;
            if (spec > 0) {
                float s = (float) Math.pow(Math.max(0, n.dot(h)), shininess) * spec;
                float fres = (float) Math.pow(1 - Math.min(1, Math.abs(n.z)), 3) * rim;
                cr = Math.min(1, cr + s + fres * rr);
                cg = Math.min(1, cg + s + fres * rg);
                cb = Math.min(1, cb + s + fres * rb);
            }
            float u0 = q.inset, u1 = 1 - q.inset;
            float[] p = q.p;
            vert(vc, m, n, p[0] * unit, p[1] * unit, p[2] * unit, u0, u0, cr, cg, cb, a, light, overlay);
            vert(vc, m, n, p[3] * unit, p[4] * unit, p[5] * unit, u1, u0, cr, cg, cb, a, light, overlay);
            vert(vc, m, n, p[6] * unit, p[7] * unit, p[8] * unit, u1, u1, cr, cg, cb, a, light, overlay);
            vert(vc, m, n, p[9] * unit, p[10] * unit, p[11] * unit, u0, u1, cr, cg, cb, a, light, overlay);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vector3f n, float x, float y, float z, float u, float v,
                             float r, float g, float b, float a, int light, int overlay) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(overlay).uv2(light).normal(n.x, n.y, n.z).endVertex();
    }

    public static int noOverlay() { return OverlayTexture.NO_OVERLAY; }
}
