package com.gojo.rework.client.fx;

import com.gojo.mod.cine.client.CineShaders;
import com.gojo.mod.cine.client.Gfx;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The orbs drawn the way Gojo's original techniques drew them: the mod's own orb shader on a sphere (Blue's inward
 * spiral, Red's turbulent white-hot core, Purple's inky body with white veins and a blazing shell), with its energy
 * shader for glows and crackling arcs. Shapes are queued while the frame's effects are worked out, then drawn in one
 * go after the world.
 */
public final class GShade {
    public static final float[] BLUE_A = {0.22F, 0.5F, 1.0F}, BLUE_B = {0.85F, 0.95F, 1.0F};
    public static final float[] RED_A = {1.0F, 0.12F, 0.08F}, RED_B = {1.0F, 0.85F, 0.6F};
    public static final float[] PUR_A = {0.55F, 0.18F, 1.0F}, PUR_B = {0.95F, 0.85F, 1.0F};
    public static final float[] WHITE = {1, 1, 1}, MAGENTA = {1.0F, 0.3F, 0.9F};

    private record Orb(Vec3 at, float r, int mode, float[] a, float[] b, float intensity, float seed) {}

    private record Glow(Vec3 at, float half, float[] c, float alpha, int mode) {}

    private record Ribbon(Vec3[] pts, float[] w, float[] c, float alpha, int mode) {}

    private static final List<Orb> ORBS = new ArrayList<>();
    private static final List<Glow> GLOWS = new ArrayList<>();
    private static final List<Ribbon> RIBBONS = new ArrayList<>();
    private static VertexBuffer sphere;

    private GShade() {}

    public static boolean ready() {
        return CineShaders.orb != null && CineShaders.energy != null;
    }

    public static void orb(Vec3 at, double r, int mode, float[] a, float[] b, float intensity, long seed) {
        if (r > 0.01 && intensity > 0.01F) {
            ORBS.add(new Orb(at, (float) r, mode, a, b, intensity, (float) (seed & 63L)));
            float gk = mode == 2 ? 0.35F : 1.0F;
            glow(at, r * 2.6, a, 0.5F * gk * intensity / 1.5F, 0);
            glow(at, r * 6.0, a, 0.18F * gk * intensity / 1.5F, 0);
        }
    }

    /** Mode 0 soft glow, 2 thin ring, 4 four-point flare. */
    public static void glow(Vec3 at, double half, float[] c, float alpha, int mode) {
        if (half > 0.01 && alpha > 0.005F) {
            GLOWS.add(new Glow(at, (float) half, c, alpha, mode));
        }
    }

    /** Mode 1 streak, 3 crackling arc. */
    public static void ribbon(Vec3[] pts, float[] w, float[] c, float alpha, int mode) {
        if (pts.length > 1 && alpha > 0.005F) {
            RIBBONS.add(new Ribbon(pts, w, c, alpha, mode));
        }
    }

    public static void clear() {
        ORBS.clear();
        GLOWS.clear();
        RIBBONS.clear();
    }

    static VertexBuffer sphere() {
        if (sphere == null) {
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.TRIANGLES, Gfx.FORMAT);
            int nt = 48, np = 28;
            for (int i = 0; i < nt; i++) {
                for (int j = 0; j < np; j++) {
                    float[][] q = new float[4][];
                    int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                    for (int c = 0; c < 4; c++) {
                        float th = (float) (Math.PI * 2) * ij[c][0] / nt;
                        float ph = (float) Math.PI * ij[c][1] / np;
                        q[c] = new float[]{Mth.sin(ph) * Mth.cos(th), Mth.cos(ph), Mth.sin(ph) * Mth.sin(th)};
                    }
                    for (int o : new int[]{0, 1, 2, 0, 2, 3}) {
                        float[] p = q[o];
                        b.vertex(p[0], p[1], p[2]).uv(0, 0).color(1F, 1F, 1F, 1F).normal(p[0], p[1], p[2]).endVertex();
                    }
                }
            }
            sphere = new VertexBuffer(VertexBuffer.Usage.STATIC);
            sphere.bind();
            sphere.upload(b.end());
            VertexBuffer.unbind();
        }
        return sphere;
    }

    /** Draw everything queued this frame. */
    public static void flush(Matrix4f view, Matrix4f proj, Vec3 cam, float time) {
        if (!ready()) {
            clear();
            return;
        }
        Gfx.setCamera(view, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9F);
        ShaderInstance orb = CineShaders.orb, en = CineShaders.energy;
        Gfx.f(orb, "Time", time);
        Gfx.f(en, "Time", time);
        // the glows first, then the bodies over them, then the arcs and rings over everything
        for (int mode : new int[]{0}) {
            drawGlows(view, proj, cam, en, mode);
        }
        for (Orb o : ORBS) {
            Gfx.f(orb, "Mode", o.mode);
            Gfx.f(orb, "Intensity", o.intensity);
            Gfx.f(orb, "Seed", o.seed);
            Gfx.v3(orb, "ColorA", o.a[0], o.a[1], o.a[2]);
            Gfx.v3(orb, "ColorB", o.b[0], o.b[1], o.b[2]);
            Matrix4f mv = new Matrix4f(view).translate((float) (o.at.x - cam.x), (float) (o.at.y - cam.y), (float) (o.at.z - cam.z))
                    .rotateY(time * 0.05F).scale(o.r);
            Gfx.meshBlend(sphere(), orb, mv, proj);
        }
        for (int mode : new int[]{1, 3}) {
            boolean any = false;
            for (Ribbon r : RIBBONS) {
                if (r.mode != mode) {
                    continue;
                }
                if (!any) {
                    Gfx.begin();
                    any = true;
                }
                int n = r.pts.length;
                float[] pts = new float[n * 3], u = new float[n];
                for (int i = 0; i < n; i++) {
                    pts[i * 3] = (float) (r.pts[i].x - cam.x);
                    pts[i * 3 + 1] = (float) (r.pts[i].y - cam.y);
                    pts[i * 3 + 2] = (float) (r.pts[i].z - cam.z);
                    u[i] = i / (float) (n - 1);
                }
                Gfx.ribbon(pts, r.w, u, r.c[0], r.c[1], r.c[2], r.alpha);
            }
            if (any) {
                Gfx.f(en, "Mode", mode);
                Gfx.flush(en, view, proj, true);
            }
        }
        for (int mode : new int[]{2, 4}) {
            drawGlows(view, proj, cam, en, mode);
        }
        clear();
    }

    private static void drawGlows(Matrix4f view, Matrix4f proj, Vec3 cam, ShaderInstance en, int mode) {
        boolean any = false;
        for (Glow g : GLOWS) {
            if (g.mode != mode) {
                continue;
            }
            if (!any) {
                Gfx.begin();
                any = true;
            }
            Gfx.billboard(g.at.x - cam.x, g.at.y - cam.y, g.at.z - cam.z, g.half, 0, g.c[0], g.c[1], g.c[2], g.alpha);
        }
        if (any) {
            Gfx.f(en, "Mode", mode);
            Gfx.flush(en, view, proj, true);
        }
    }
}
