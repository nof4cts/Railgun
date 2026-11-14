package com.aegis.command.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Anime-style impact frames for cannon-class weapons and the airstrike:
 * <ol>
 *   <li>0-60 ms: the screen is colour-inverted, with black radial speed lines and a white core (the "impact frame");</li>
 *   <li>60-130 ms: a hot flash in the weapon's energy colour;</li>
 *   <li>to ~450 ms: fading energy-coloured speed lines, an expanding shock ring and a chromatic vignette.</li>
 * </ol>
 * The camera is also frozen for the first frames (hit-stop) and given an FOV punch.
 */
public final class ImpactFrames {
    private static final RandomSource R = RandomSource.create();
    private static long startMs = -100000;
    private static float strength;
    private static int color = 0xFFFFFF;
    private static long seed;
    private static float frozenYaw, frozenPitch;
    private static boolean frozenCaptured;

    private ImpactFrames() {}

    /** strength 0..1+. Stronger hits invert longer and throw more lines. */
    public static void trigger(float s, int rgb) {
        long now = System.currentTimeMillis();
        // don't let rapid triggers restart a stronger frame that is still playing
        if (now - startMs < 120 && s <= strength) return;
        startMs = now;
        strength = Math.max(0.3f, s);
        color = rgb;
        seed = R.nextLong();
        frozenCaptured = false;
        ScreenFx.addTrauma(0.35f * strength);
    }

    private static float age() { return (System.currentTimeMillis() - startMs) / 1000f; }
    private static float invertTime() { return 0.035f + 0.035f * Math.min(1, strength); }

    public static boolean active() { return age() < 0.5f; }

    /** Camera hit-stop window. */
    public static boolean frozen() { return age() < 0.05f + 0.04f * Math.min(1, strength); }

    /** Lock the camera angles during the hit-stop; returns {yaw, pitch}. */
    public static float[] freeze(float yaw, float pitch) {
        if (!frozenCaptured) {
            frozenYaw = yaw;
            frozenPitch = pitch;
            frozenCaptured = true;
        }
        return new float[]{frozenYaw, frozenPitch};
    }

    public static float fovScale() {
        float a = age();
        if (a > 0.45f) return 1f;
        // punch in, then ease back out
        float k = a < 0.05f ? a / 0.05f : (float) Math.exp(-(a - 0.05f) * 9);
        return 1 - 0.09f * Math.min(1, strength) * k;
    }

    public static void render(GuiGraphics g, int w, int h) {
        float a = age();
        if (a > 0.5f) return;
        g.flush();
        Matrix4f m = g.pose().last().pose();
        float cx = w / 2f, cy = h / 2f;
        float er = ((color >> 16) & 255) / 255f, eg = ((color >> 8) & 255) / 255f, eb = (color & 255) / 255f;
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float inv = invertTime();
        if (a < inv) {
            // 1. invert everything already on screen: dst = 1 - dst
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            rect(m, 0, 0, w, h, 1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            // 2. high-contrast black speed lines converging on the centre + a white core burst
            lines(m, w, h, cx, cy, 0, 0, 0, 0.95f, 48, 0.55f, seed);
            burst(m, cx, cy, Math.min(w, h) * (0.10f + 0.1f * strength), 1, 1, 1, 1);
        } else if (a < inv + 0.07f) {
            float k = 1 - (a - inv) / 0.07f;
            rect(m, 0, 0, w, h, Math.min(1, er * 0.5f + 0.5f), Math.min(1, eg * 0.5f + 0.5f), Math.min(1, eb * 0.5f + 0.5f), 0.55f * k * Math.min(1, strength));
            lines(m, w, h, cx, cy, 1, 1, 1, 0.8f * k, 40, 0.5f, seed);
        }
        // 3. lingering energy speed lines, shock ring and chromatic vignette
        float fade = Mth.clamp(1 - (a - inv) / 0.42f, 0, 1);
        if (fade > 0) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            lines(m, w, h, cx, cy, er, eg, eb, 0.55f * fade * fade, 34, 0.62f + 0.2f * (1 - fade), seed ^ 0x5DEECE66DL);
            float rr = Math.min(w, h) * (0.08f + 0.9f * (1 - fade));
            ring(m, cx, cy, rr, 3 + 6 * fade, er, eg, eb, 0.7f * fade);
            ring(m, cx, cy, rr * 0.82f, 1.5f, 1, 1, 1, 0.5f * fade);
            vignette(m, w, h, er, eg, eb, 0.35f * fade * Math.min(1, strength));
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ primitives

    private static void rect(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
        Tesselator.getInstance().end();
    }

    /** Radial wedges from beyond the screen edge toward the centre, stopping at an inner radius. */
    private static void lines(Matrix4f m, int w, int h, float cx, float cy, float r, float g, float b, float a, int n, float inner, long sd) {
        RandomSource rs = RandomSource.create(sd);
        float far = (float) Math.hypot(w, h);
        float in = Math.min(w, h) * inner * 0.5f;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            double ang = rs.nextDouble() * Math.PI * 2;
            double half = 0.004 + rs.nextDouble() * 0.016;
            float start = in * (0.7f + rs.nextFloat() * 0.8f);
            float x0 = cx + (float) Math.cos(ang - half) * far, y0 = cy + (float) Math.sin(ang - half) * far;
            float x1 = cx + (float) Math.cos(ang + half) * far, y1 = cy + (float) Math.sin(ang + half) * far;
            float tx = cx + (float) Math.cos(ang) * start, ty = cy + (float) Math.sin(ang) * start;
            bb.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, tx, ty, 0).color(r, g, b, 0f).endVertex();
            bb.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        }
        Tesselator.getInstance().end();
    }

    /** Spiky star at the centre of the impact. */
    private static void burst(Matrix4f m, float cx, float cy, float rad, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int spikes = 14;
        RandomSource rs = RandomSource.create(seed + 7);
        for (int i = 0; i < spikes * 2; i++) {
            double a0 = Math.PI * 2 * i / (spikes * 2), a1 = Math.PI * 2 * (i + 1) / (spikes * 2);
            float r0 = (i % 2 == 0) ? rad * (0.8f + rs.nextFloat() * 0.9f) : rad * 0.3f;
            float r1 = (i % 2 == 0) ? rad * 0.3f : rad * (0.8f + rs.nextFloat() * 0.9f);
            bb.vertex(m, cx, cy, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + (float) Math.cos(a0) * r0, cy + (float) Math.sin(a0) * r0, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + (float) Math.cos(a1) * r1, cy + (float) Math.sin(a1) * r1, 0).color(r, g, b, a).endVertex();
        }
        Tesselator.getInstance().end();
    }

    private static void ring(Matrix4f m, float cx, float cy, float rad, float thick, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            bb.vertex(m, cx + c0 * (rad + thick), cy + s0 * (rad + thick), 0).color(r, g, b, 0f).endVertex();
            bb.vertex(m, cx + c1 * (rad + thick), cy + s1 * (rad + thick), 0).color(r, g, b, 0f).endVertex();
            bb.vertex(m, cx + c1 * rad, cy + s1 * rad, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + c0 * rad, cy + s0 * rad, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + c0 * rad, cy + s0 * rad, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + c1 * rad, cy + s1 * rad, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + c1 * Math.max(0, rad - thick), cy + s1 * Math.max(0, rad - thick), 0).color(r, g, b, 0f).endVertex();
            bb.vertex(m, cx + c0 * Math.max(0, rad - thick), cy + s0 * Math.max(0, rad - thick), 0).color(r, g, b, 0f).endVertex();
        }
        Tesselator.getInstance().end();
    }

    private static void vignette(Matrix4f m, int w, int h, float r, float g, float b, float a) {
        float e = Math.min(w, h) * 0.22f;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // left, right, top, bottom gradients
        quadGrad(bb, m, 0, 0, e, h, r, g, b, a, 0, true);
        quadGrad(bb, m, w - e, 0, w, h, r, g, b, 0, a, true);
        quadGrad(bb, m, 0, 0, w, e, r, g, b, a, 0, false);
        quadGrad(bb, m, 0, h - e, w, h, r, g, b, 0, a, false);
        Tesselator.getInstance().end();
    }

    private static void quadGrad(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1,
                                 float r, float g, float b, float a0, float a1, boolean horizontal) {
        if (horizontal) {
            bb.vertex(m, x0, y1, 0).color(r, g, b, a0).endVertex();
            bb.vertex(m, x1, y1, 0).color(r, g, b, a1).endVertex();
            bb.vertex(m, x1, y0, 0).color(r, g, b, a1).endVertex();
            bb.vertex(m, x0, y0, 0).color(r, g, b, a0).endVertex();
        } else {
            bb.vertex(m, x0, y1, 0).color(r, g, b, a1).endVertex();
            bb.vertex(m, x1, y1, 0).color(r, g, b, a1).endVertex();
            bb.vertex(m, x1, y0, 0).color(r, g, b, a0).endVertex();
            bb.vertex(m, x0, y0, 0).color(r, g, b, a0).endVertex();
        }
    }
}
