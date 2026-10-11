package com.firestar.orbital.client;

import com.firestar.orbital.OrbitalMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The other world's land as Antipode brings it down: a disc of terrain hanging upside down from the sky, its mountains
 * pointing at ours, its seas crimson, its rock violet-black and split by burning fissures. It descends until its peaks
 * touch the mark, then withdraws into the sky as the exchange spreads.
 */
public final class MirrorWorld {
    private MirrorWorld() {}

    private static final ResourceLocation WHITE = OrbitalMod.id("textures/misc/white.png");
    private static final int N = 72;
    /** The hanging land's radius, in zone radii: wide enough to fill the sky. */
    static final float SPAN = 4.5F;
    private static final Map<Integer, float[][]> HEIGHTS = new HashMap<>();

    /** How far the land hangs at its deepest, relative to the zone's radius. */
    static final float HANG = 0.9F;

    /** The height of the hanging land's base over the mark at time t, and how solid it is. */
    static float base(Strikes.Active a, float t) {
        int I = a.cannon.impact;
        float dmax = a.radius * HANG;
        if (t < I) {
            float k = Mth.clamp((t - 108) / (I - 108.0F), 0, 1);
            return Mth.lerp((float) Math.pow(k, 1.7), 240, dmax);
        }
        float k = (t - I) / 70.0F;
        return dmax + 300 * k * k;
    }

    static float alpha(Strikes.Active a, float t) {
        int I = a.cannon.impact;
        return Mth.clamp((t - 104) / 8.0F, 0, 1) * Mth.clamp(1 - (t - I - 30) / 40.0F, 0, 1);
    }

    /** The hanging depth over the disc, 0..1 of the deepest, on an N x N grid; the same for a strike everywhere. */
    static float[][] heights(Strikes.Active a) {
        return HEIGHTS.computeIfAbsent(a.id, k -> {
            float[][] h = new float[N + 1][N + 1];
            long seed = a.seed;
            for (int i = 0; i <= N; i++) {
                for (int j = 0; j <= N; j++) {
                    double u = i / (double) N * 2 - 1, v = j / (double) N * 2 - 1;
                    double r2 = u * u + v * v;
                    if (r2 > 1) {
                        h[i][j] = 0;
                        continue;
                    }
                    double n = fbm(u * 7.0, v * 7.0, seed);
                    double peak = Math.max(0, (n - 0.4) / 0.6);
                    double fall = 1 - r2;
                    // the deepest point always hangs over the mark itself: a mountain upside down, a zone wide
                    double centre = Math.exp(-r2 * SPAN * SPAN * 1.6);
                    double ridges = Math.pow(peak, 1.6) * fall * 0.55 + Math.pow(1 - Math.abs(2 * fbm(u * 14, v * 14, seed + 3) - 1), 6) * 0.12 * fall;
                    h[i][j] = (float) Math.min(1, ridges + centre * (0.9 + 0.1 * peak));
                }
            }
            return h;
        });
    }

    public static void render(RenderLevelStageEvent event, Strikes.Active a, float t) {
        float alpha = alpha(a, t);
        if (alpha <= 0.01F) {
            if (t > a.cannon.end) {
                HEIGHTS.remove(a.id);
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float[][] h = heights(a);
        float rm = a.radius * SPAN;
        float dmax = a.radius * HANG;
        float baseY = (float) a.target.y + base(a, t);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(a.target.x - cam.x, baseY - cam.y, a.target.z - cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.debugQuads();
        VertexConsumer vc = buffers.getBuffer(type);
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (inside(i, j) + inside(i + 1, j) + inside(i, j + 1) + inside(i + 1, j + 1) == 0) {
                    continue;
                }
                int[][] q = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                for (int[] p : q) {
                    int ii = p[0], jj = p[1];
                    float u = ii / (float) N * 2 - 1, v = jj / (float) N * 2 - 1;
                    float d = h[ii][jj];
                    float x = u * rm, z = v * rm, y = -d * dmax;
                    // normal from neighbours
                    float dx = (h[Math.min(N, ii + 1)][jj] - h[Math.max(0, ii - 1)][jj]) * dmax / (2 * rm / N);
                    float dz = (h[ii][Math.min(N, jj + 1)] - h[ii][Math.max(0, jj - 1)]) * dmax / (2 * rm / N);
                    float nx = -dx, ny = -1, nz = -dz;
                    float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                    float[] c = colour(u, v, d, a.seed, time);
                    // its own light: the sun of its world is behind it; we see it lit from the side, rims bright
                    float lit = 0.45F + 0.55F * Math.max(0, (nx * 0.6F - ny * 0.2F + nz * 0.5F) / l);
                    float edge = Mth.clamp((1 - (u * u + v * v)) * 5, 0, 1);
                    vc.vertex(m, x, y, z).color(Math.min(1, c[0] * lit + c[3]), Math.min(1, c[1] * lit + c[4]), Math.min(1, c[2] * lit + c[5]), alpha * edge)
                            .endVertex();
                }
            }
        }
        buffers.endBatch(type);
        ps.popPose();
    }

    private static int inside(int i, int j) {
        float u = i / (float) N * 2 - 1, v = j / (float) N * 2 - 1;
        return u * u + v * v <= 1.08F ? 1 : 0;
    }

    /**
     * Crimson seas where it hangs shallow, violet-black rock and teal moss where it hangs deep, and fissures of light
     * across it. Returns the lit colour and, separately, the glow (which ignores the light).
     */
    private static float[] colour(float u, float v, float d, long seed, float time) {
        float sea = Mth.clamp(1 - d / 0.05F, 0, 1);
        float moss = (float) Mth.clamp((fbm(u * 9 + 5, v * 9, seed + 11) - 0.5) * 6, 0, 1) * (1 - sea);
        float r = Mth.lerp(sea, 0.30F, 0.62F), g = Mth.lerp(sea, 0.12F, 0.06F), b = Mth.lerp(sea, 0.36F, 0.14F);
        r = Mth.lerp(moss, r, 0.10F);
        g = Mth.lerp(moss, g, 0.42F);
        b = Mth.lerp(moss, b, 0.42F);
        // the sea shimmers a little
        float wave = sea * 0.06F * Mth.sin(time * 1.3F + u * 40 + v * 30);
        double ridge = 1 - Math.abs(2 * fbm(u * 18 + 3, v * 18 - 1, seed + 7) - 1);
        float fis = (float) Math.pow(ridge, 12) * (1 - sea);
        float pulse = 0.75F + 0.25F * Mth.sin(time * 2.0F + u * 9);
        return new float[]{r + wave, g, b + wave, fis * 1.0F * pulse, fis * 0.25F * pulse, fis * 0.7F * pulse};
    }

    // ------------------------------------------------------------------------------------------------ noise

    private static double hash(long x, long y, long seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double vnoise(double x, double y, long seed) {
        long xi = (long) Math.floor(x), yi = (long) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
        return Mth.lerp(uy, Mth.lerp(ux, a, b), Mth.lerp(ux, c, d));
    }

    static double fbm(double x, double y, long seed) {
        double v = 0, amp = 0.5;
        for (int i = 0; i < 5; i++) {
            v += amp * vnoise(x, y, seed + i * 31);
            x = x * 2.03 + 1.7;
            y = y * 2.03 + 9.2;
            amp *= 0.5;
        }
        return v;
    }

    public static void clear() {
        HEIGHTS.clear();
    }
}
