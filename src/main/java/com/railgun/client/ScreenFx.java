package com.railgun.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.railgun.ClientConfig;
import com.railgun.RailgunItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.Random;

/** Full-screen effects: impact frames (invert / ink + speed lines), flash, vignette, charge ring, shake. */
public final class ScreenFx {
    private static final long NEVER = Long.MIN_VALUE / 4;
    private static long fireStart = NEVER, hitStart = NEVER, shakeStart = NEVER, softStart = NEVER;
    private static float fireS, hitS, shakeAmp, softS;

    // ------------------------------------------------------------- triggers
    public static void onFire(float power) {
        fireStart = Util.getMillis();
        fireS = 0.6f + 0.4f * power;
        addShake(2.4f + 3.4f * power);
    }

    public static void onHit() {
        hitStart = Util.getMillis() + 75;
        hitS = 0.8f;
        addShake(2.6f);
    }

    public static void onNearby(double dBeam, double dImpact) {
        float k = (float) Math.max(Mth.clamp(1.0 - dBeam / 36.0, 0, 1), Mth.clamp(1.0 - dImpact / 60.0, 0, 1));
        if (k <= 0.02f) return;
        addShake(3.2f * k);
        if (dBeam < 5.0) { // the beam went right past you
            fireStart = Util.getMillis();
            fireS = 0.35f + 0.3f * (float) (1.0 - dBeam / 5.0);
        } else {
            softStart = Util.getMillis();
            softS = k;
        }
    }

    private static void addShake(float amp) {
        long now = Util.getMillis();
        shakeAmp = Math.max(shake(now), amp);
        shakeStart = now;
    }

    // ------------------------------------------------------------- queries
    public static float shake(long now) {
        if (!ClientConfig.SCREEN_SHAKE.get()) return 0f;
        return shakeAmp * (float) Math.exp(-(now - shakeStart) / 230.0);
    }

    public static float fovPunch(long now) {
        if (!ClientConfig.SCREEN_SHAKE.get()) return 1f;
        long dt = now - fireStart;
        if (dt < 0) return 1f;
        return 1f + 0.22f * fireS * (float) Math.exp(-dt / 140.0);
    }

    public static float charge(float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.isUsingItem()) return 0f;
        ItemStack s = p.getUseItem();
        if (!(s.getItem() instanceof RailgunItem)) return 0f;
        return Mth.clamp((p.getTicksUsingItem() + pt) / RailgunItem.FULL_CHARGE, 0f, 1f);
    }

    // ------------------------------------------------------------- overlay
    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        long now = Util.getMillis();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float charge = charge(pt);
        if (charge > 0.02f) chargeHud(m, w, h, charge, now);

        sequence(m, w, h, fireStart, fireS, now, true);
        sequence(m, w, h, hitStart, hitS, now, true);
        sequence(m, w, h, softStart, softS * 0.45f, now, false);

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void sequence(Matrix4f m, int w, int h, long start, float s, long now, boolean allowFrames) {
        long dt = now - start;
        if (dt < 0 || dt > 900 || s <= 0f) return;
        boolean frames = allowFrames && ClientConfig.IMPACT_FRAMES.get();
        if (frames && dt < 150) {
            // invert / ink / invert: the classic three-frame impact flicker
            if (dt < 45 || dt >= 105) invert(m, w, h);
            else ink(m, w, h, start, now, s);
            return;
        }
        float x = Mth.clamp(frames ? (dt - 150) / 750f : dt / 600f, 0f, 1f);
        float a = (1f - x) * (1f - x) * (frames ? 0.85f : 0.4f) * s;
        RenderSystem.defaultBlendFunc();
        fill(m, 0, 0, w, h, 1f - 0.6f * x, 1f - 0.15f * x, 1f, a);

        // cyan vignette bloom
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        float va = (1f - x) * 0.6f * s;
        float eh = h * 0.38f, ew = w * 0.30f;
        gradV(m, 0, 0, w, eh, 0.2f, 0.7f, 1f, va, 0f);
        gradV(m, 0, h - eh, w, h, 0.2f, 0.7f, 1f, 0f, va);
        gradH(m, 0, 0, ew, h, 0.2f, 0.7f, 1f, va, 0f);
        gradH(m, w - ew, 0, w, h, 0.2f, 0.7f, 1f, 0f, va);
        RenderSystem.defaultBlendFunc();
    }

    private static void invert(Matrix4f m, int w, int h) {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        fill(m, 0, 0, w, h, 1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    private static void ink(Matrix4f m, int w, int h, long start, long now, float s) {
        RenderSystem.defaultBlendFunc();
        fill(m, 0, 0, w, h, 0f, 0f, 0f, 0.9f);
        Random r = new Random(start + (now - start) / 28);
        float cx = w / 2f, cy = h / 2f, R = (float) Math.hypot(w, h) * 0.62f;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = (int) (70 + 60 * s);
        for (int i = 0; i < n; i++) {
            float a = r.nextFloat() * 6.2832f;
            float hw = 0.006f + r.nextFloat() * r.nextFloat() * 0.07f;
            float in = R * (0.07f + 0.38f * r.nextFloat());
            bb.vertex(m, cx + Mth.cos(a - hw) * R, cy + Mth.sin(a - hw) * R, 0).color(1f, 1f, 1f, 1f).endVertex();
            bb.vertex(m, cx + Mth.cos(a + hw) * R, cy + Mth.sin(a + hw) * R, 0).color(1f, 1f, 1f, 1f).endVertex();
            bb.vertex(m, cx + Mth.cos(a) * in, cy + Mth.sin(a) * in, 0).color(1f, 1f, 1f, 1f).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
        // hot centre
        BufferBuilder fb = Tesselator.getInstance().getBuilder();
        fb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float cr = Math.min(w, h) * 0.05f * (0.7f + 0.6f * s);
        for (int i = 0; i < 24; i++) {
            float a0 = 6.2832f * i / 24f, a1 = 6.2832f * (i + 1) / 24f;
            fb.vertex(m, cx, cy, 0).color(1f, 1f, 1f, 1f).endVertex();
            fb.vertex(m, cx + Mth.cos(a0) * cr, cy + Mth.sin(a0) * cr, 0).color(1f, 1f, 1f, 1f).endVertex();
            fb.vertex(m, cx + Mth.cos(a1) * cr, cy + Mth.sin(a1) * cr, 0).color(1f, 1f, 1f, 1f).endVertex();
        }
        BufferUploader.drawWithShader(fb.end());
    }

    private static void chargeHud(Matrix4f m, int w, int h, float charge, long now) {
        float cx = w / 2f, cy = h / 2f;
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        float c2 = charge * charge;
        float er = 1f - 0.3f * charge, eg = 1f, eb = 1f;
        // edge vignette that grows with charge
        float eh = h * 0.30f, ew = w * 0.24f, va = 0.45f * c2;
        gradV(m, 0, 0, w, eh, 0.2f, 0.7f, 1f, va, 0f);
        gradV(m, 0, h - eh, w, h, 0.2f, 0.7f, 1f, 0f, va);
        gradH(m, 0, 0, ew, h, 0.2f, 0.7f, 1f, va, 0f);
        gradH(m, w - ew, 0, w, h, 0.2f, 0.7f, 1f, 0f, va);
        // charge ring
        float rad = 20f + 6f * (1f - charge);
        arc(m, cx, cy, rad, rad + 2f, 1f, 64, 0.3f, 0.8f, 1f, 0.18f);
        arc(m, cx, cy, rad - 1f, rad + 3f, charge, 64, 0.2f, 0.7f, 1f, 0.35f);
        arc(m, cx, cy, rad, rad + 2f, charge, 64, er, eg, eb, 0.95f);
        // rotating tick marks
        float rot = now * 0.004f * (1f + 3f * charge);
        for (int i = 0; i < 4; i++) {
            float a = rot + i * 1.5708f;
            float x0 = cx + Mth.cos(a) * (rad + 6f), y0 = cy + Mth.sin(a) * (rad + 6f);
            float x1 = cx + Mth.cos(a) * (rad + 6f + 5f + 5f * charge), y1 = cy + Mth.sin(a) * (rad + 6f + 5f + 5f * charge);
            line(m, x0, y0, x1, y1, 1.2f, 0.6f, 0.95f, 1f, 0.8f);
        }
        if (charge >= 0.999f) { // ready pulse
            float p = 0.5f + 0.5f * Mth.sin(now * 0.03f);
            arc(m, cx, cy, rad + 3f, rad + 5f, 1f, 64, 1f, 1f, 1f, 0.4f + 0.5f * p);
        }
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------- tiny draw helpers
    private static void fill(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static void gradV(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float aTop, float aBot) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x0, y1, 0).color(r, g, b, aBot).endVertex();
        bb.vertex(m, x1, y1, 0).color(r, g, b, aBot).endVertex();
        bb.vertex(m, x1, y0, 0).color(r, g, b, aTop).endVertex();
        bb.vertex(m, x0, y0, 0).color(r, g, b, aTop).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static void gradH(Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float aLeft, float aRight) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x0, y1, 0).color(r, g, b, aLeft).endVertex();
        bb.vertex(m, x1, y1, 0).color(r, g, b, aRight).endVertex();
        bb.vertex(m, x1, y0, 0).color(r, g, b, aRight).endVertex();
        bb.vertex(m, x0, y0, 0).color(r, g, b, aLeft).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static void arc(Matrix4f m, float cx, float cy, float r0, float r1, float frac, int segs, float r, float g, float b, float a) {
        int n = (int) (segs * Mth.clamp(frac, 0f, 1f));
        if (n <= 0) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            float a0 = -1.5708f + 6.2832f * i / segs, a1 = -1.5708f + 6.2832f * (i + 1) / segs;
            bb.vertex(m, cx + Mth.cos(a0) * r0, cy + Mth.sin(a0) * r0, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + Mth.cos(a1) * r0, cy + Mth.sin(a1) * r0, 0).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void line(Matrix4f m, float x0, float y0, float x1, float y1, float w, float r, float g, float b, float a) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * w * 0.5f, ny = dx / l * w * 0.5f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x0 - nx, y0 - ny, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0 + nx, y0 + ny, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1 + nx, y1 + ny, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1 - nx, y1 - ny, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private ScreenFx() {}
}
