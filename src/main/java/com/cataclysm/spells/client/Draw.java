package com.cataclysm.spells.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hand-rolled geometry. Every effect in the mod is built from these primitives —
 * spheres, rings, ribbons, boxes, beams — so no vanilla particle is ever spawned.
 */
public final class Draw {

    // ─────────── colour ───────────

    public static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static int alpha(int c, float a) {
        int na = Mth.clamp((int) (((c >>> 24) & 255) * a), 0, 255);
        return (c & 0xFFFFFF) | (na << 24);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a = (int) Mth.lerp(t, (c1 >>> 24) & 255, (c2 >>> 24) & 255);
        int r = (int) Mth.lerp(t, (c1 >> 16) & 255, (c2 >> 16) & 255);
        int g = (int) Mth.lerp(t, (c1 >> 8) & 255, (c2 >> 8) & 255);
        int b = (int) Mth.lerp(t, c1 & 255, c2 & 255);
        return argb(a, r, g, b);
    }

    public static int scaleRgb(int c, float k) {
        int r = Mth.clamp((int) (((c >> 16) & 255) * k), 0, 255);
        int g = Mth.clamp((int) (((c >> 8) & 255) * k), 0, 255);
        int b = Mth.clamp((int) ((c & 255) * k), 0, 255);
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    public static int hsv(float h, float s, float v) {
        return 0xFF000000 | Mth.hsvToRgb(((h % 1f) + 1f) % 1f, s, v);
    }

    // ─────────── render state ───────────

    public static BufferBuilder begin(VertexFormat.Mode mode) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    public static void end(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
    }

    public static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    public static void translucent() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void worldPassBegin(boolean glow) {
        if (glow) additive(); else translucent();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(!glow);
    }

    public static void worldPassEnd() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ─────────── vertices ───────────

    public static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, int c) {
        vc.vertex(m, (float) x, (float) y, (float) z).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    public static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int c) {
        v(vc, m, p.x, p.y, p.z, c);
    }

    public static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        v(vc, m, a, ca); v(vc, m, b, cb); v(vc, m, c, cc); v(vc, m, d, cd);
    }

    public static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col) {
        quad(vc, m, a, b, c, d, col, col, col, col);
    }

    /** A triangle written into a QUADS buffer as a degenerate quad. */
    public static void tri(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, int ca, int cb, int cc) {
        quad(vc, m, a, b, c, c, ca, cb, cc, cc);
    }

    // ─────────── shapes ───────────

    public interface ColorFn {
        int at(double nx, double ny, double nz);
    }

    public static void sphere(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, int lat, int lon, ColorFn fn) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            double y0 = Math.cos(t0), y1 = Math.cos(t1), s0 = Math.sin(t0), s1 = Math.sin(t1);
            for (int j = 0; j < lon; j++) {
                double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                double c0 = Math.cos(p0), n0 = Math.sin(p0), c1 = Math.cos(p1), n1 = Math.sin(p1);
                double ax = s0 * c0, az = s0 * n0, bx = s1 * c0, bz = s1 * n0, cxx = s1 * c1, czz = s1 * n1, dx = s0 * c1, dz = s0 * n1;
                v(vc, m, cx + ax * r, cy + y0 * r, cz + az * r, fn.at(ax, y0, az));
                v(vc, m, cx + bx * r, cy + y1 * r, cz + bz * r, fn.at(bx, y1, bz));
                v(vc, m, cx + cxx * r, cy + y1 * r, cz + czz * r, fn.at(cxx, y1, czz));
                v(vc, m, cx + dx * r, cy + y0 * r, cz + dz * r, fn.at(dx, y0, dz));
            }
        }
    }

    public static void sphere(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, int lat, int lon, int col) {
        sphere(vc, m, cx, cy, cz, r, lat, lon, (x, y, z) -> col);
    }

    /** Soft glowing orb: concentric additive shells. */
    public static void glowOrb(VertexConsumer vc, Matrix4f m, double x, double y, double z, double r, int col, float a) {
        sphere(vc, m, x, y, z, r * 0.45, 8, 12, alpha(0xFFFFFFFF, a));
        sphere(vc, m, x, y, z, r, 8, 12, alpha(col, a * 0.55f));
        sphere(vc, m, x, y, z, r * 1.7, 8, 12, alpha(col, a * 0.18f));
    }

    /** Annulus in the plane spanned by unit vectors u,v. */
    public static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double rIn, double rOut, int cIn, int cOut, int segs) {
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            quad(vc, m, c.add(d0.scale(rIn)), c.add(d0.scale(rOut)), c.add(d1.scale(rOut)), c.add(d1.scale(rIn)), cIn, cOut, cOut, cIn);
        }
    }

    public static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);

    public static void ringXZ(VertexConsumer vc, Matrix4f m, Vec3 c, double rIn, double rOut, int cIn, int cOut, int segs) {
        ring(vc, m, c, X, Z, rIn, rOut, cIn, cOut, segs);
    }

    /** Shaded axis-aligned box (faces get different brightness for a lit look). */
    public static void box(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int c) {
        int top = c, bot = scaleRgb(c, 0.5f), ns = scaleRgb(c, 0.8f), ew = scaleRgb(c, 0.66f);
        // top / bottom
        v(vc, m, x0, y1, z0, top); v(vc, m, x0, y1, z1, top); v(vc, m, x1, y1, z1, top); v(vc, m, x1, y1, z0, top);
        v(vc, m, x0, y0, z0, bot); v(vc, m, x1, y0, z0, bot); v(vc, m, x1, y0, z1, bot); v(vc, m, x0, y0, z1, bot);
        // north / south
        v(vc, m, x0, y0, z0, ns); v(vc, m, x0, y1, z0, ns); v(vc, m, x1, y1, z0, ns); v(vc, m, x1, y0, z0, ns);
        v(vc, m, x0, y0, z1, ns); v(vc, m, x1, y0, z1, ns); v(vc, m, x1, y1, z1, ns); v(vc, m, x0, y1, z1, ns);
        // west / east
        v(vc, m, x0, y0, z0, ew); v(vc, m, x0, y0, z1, ew); v(vc, m, x0, y1, z1, ew); v(vc, m, x0, y1, z0, ew);
        v(vc, m, x1, y0, z0, ew); v(vc, m, x1, y1, z0, ew); v(vc, m, x1, y1, z1, ew); v(vc, m, x1, y0, z1, ew);
    }

    public static void cube(VertexConsumer vc, Matrix4f m, double s, int c) {
        box(vc, m, -s, -s, -s, s, s, s, c);
    }

    /** Camera-facing ribbon from a to b with tapering width and colour. */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double wa, double wb, int ca, int cb, Vec3 cam) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        double len = side.length();
        if (len < 1.0e-6) return;
        side = side.scale(1.0 / len);
        quad(vc, m, a.add(side.scale(wa)), b.add(side.scale(wb)), b.subtract(side.scale(wb)), a.subtract(side.scale(wa)), ca, cb, cb, ca);
    }

    public static void billboard(VertexConsumer vc, Matrix4f m, Vec3 p, double size, int c, Vector3f left, Vector3f up) {
        Vec3 l = new Vec3(left.x(), left.y(), left.z()).scale(size);
        Vec3 u = new Vec3(up.x(), up.y(), up.z()).scale(size);
        quad(vc, m, p.add(l).add(u), p.subtract(l).add(u), p.subtract(l).subtract(u), p.add(l).subtract(u), c);
    }

    /** Open vertical cylinder, used for light pillars. */
    public static void beam(VertexConsumer vc, Matrix4f m, Vec3 base, double h, double r, int cBot, int cTop, int segs) {
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            Vec3 p0 = base.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
            Vec3 p1 = base.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            quad(vc, m, p0, p1, p1.add(0, h, 0), p0.add(0, h, 0), cBot, cBot, cTop, cTop);
        }
    }

    /** Hollow cone from apex along dir (used for spotlights and punch shock-cones). */
    public static void cone(VertexConsumer vc, Matrix4f m, Vec3 apex, Vec3 dir, double len, double r, int cApex, int cRim, int segs) {
        Vec3 d = dir.normalize();
        Vec3 u = perp(d);
        Vec3 w = d.cross(u);
        Vec3 base = apex.add(d.scale(len));
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            Vec3 p0 = base.add(u.scale(Math.cos(a0) * r)).add(w.scale(Math.sin(a0) * r));
            Vec3 p1 = base.add(u.scale(Math.cos(a1) * r)).add(w.scale(Math.sin(a1) * r));
            tri(vc, m, apex, p0, p1, cApex, cRim, cRim);
        }
    }

    public static Vec3 perp(Vec3 d) {
        Vec3 a = Math.abs(d.y) < 0.9 ? Y : X;
        return d.cross(a).normalize();
    }

    public static Vec3 forward(float yawDeg, float pitchDeg) {
        float y = yawDeg * Mth.DEG_TO_RAD, p = pitchDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    /** Deterministic hash → [0,1). */
    public static double hash(long seed) {
        long x = seed * 0x9E3779B97F4A7C15L;
        x ^= (x >>> 31);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        return ((x >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    public static Vec3 randDir(long seed) {
        double u = hash(seed) * 2 - 1, th = hash(seed + 7919) * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(th), u, s * Math.sin(th));
    }

    private Draw() {}
}
