package com.firestar.mih.client.render;

import com.firestar.mih.client.ClientState;
import com.firestar.mih.common.Beats;
import com.firestar.mih.config.ZConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The sky under accelerated time. The day itself is advanced smoothly every rendered frame (not in tick-sized jumps),
 * and on top of the vanilla sky the sun and moon leave trails of light along their path, longer as time runs faster,
 * until each is a complete ring - the anime's "sun as a strip of light". At night the stars wheel round the pole in
 * trails.
 */
public final class SkyFx {
    private static long base;
    private static long written = Long.MIN_VALUE;

    private SkyFx() {}

    // ------------------------------------------------------------------------------------------------ smooth time

    /** End of each client tick: remember the day the server sent, or carry ours forward if none came. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            written = Long.MIN_VALUE;
            return;
        }
        double rate = ClientState.accelRate(0.0F);
        if (rate <= 1.5) {
            written = Long.MIN_VALUE;
            return;
        }
        long d = level.getDayTime();
        if (written != Long.MIN_VALUE && (d == written || d == written + 1)) {
            base += (long) rate;
        } else {
            base = d;
        }
    }

    /** Start of each rendered frame: the day as it is right now, between ticks. */
    public static void frame(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || written == Long.MIN_VALUE && ClientState.accelRate(partial) <= 1.5) {
            return;
        }
        double rate = ClientState.accelRate(partial);
        if (rate <= 1.5) {
            return;
        }
        if (written == Long.MIN_VALUE) {
            base = level.getDayTime();
        }
        long v = base + (long) (rate * partial);
        level.setDayTime(v);
        written = v;
    }

    // ------------------------------------------------------------------------------------------------ streaks

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !ZConfig.CLIENT.sunStreaks.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick();
        double rate = ClientState.accelRate(partial);
        if (rate < 4.0) {
            return;
        }
        // how much of its path each body smears across: a sliver at first, the whole circle at full speed
        float trail = (float) Mth.clamp(rate / Beats.MAX_RATE * 430.0, 2.0, 360.0);
        float theta = level.getTimeOfDay(partial) * 360.0F;
        float night = level.getStarBrightness(partial);
        PoseStack ps = Galaxy.view(event);
        Matrix4f m = ps.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float k = (float) Mth.clamp(rate / 60.0, 0.0, 1.0);
        // the sun: hot gold at its head, cooling to white along the trail
        arc(bb, m, theta, trail, 100.0F, 13.0F, 1.0F, 0.92F, 0.62F, 0.95F * k);
        arc(bb, m, theta, trail, 100.0F, 34.0F, 1.0F, 0.75F, 0.35F, 0.28F * k);
        // the moon, opposite
        arc(bb, m, theta + 180.0F, trail, 100.0F, 8.0F, 0.75F, 0.85F, 1.0F, 0.55F * k);
        // star trails round the pole at night
        if (night > 0.05F) {
            Random r = new Random(1033L);
            float starTrail = Math.min(trail, 220.0F);
            for (int i = 0; i < 90; i++) {
                float lat = (r.nextFloat() - 0.5F) * 170.0F;
                float lon = r.nextFloat() * 360.0F;
                float w = 0.25F + r.nextFloat() * 0.5F;
                starArc(bb, m, lon + theta, lat, starTrail * (0.6F + 0.4F * r.nextFloat()), w, night * k * (0.25F + 0.5F * r.nextFloat()));
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    /** A band along the sun's great circle (world XY plane) from {@code head - length} to {@code head} degrees. */
    private static void arc(BufferBuilder bb, Matrix4f m, float head, float length, float dist, float width, float r, float g, float b, float a) {
        int n = Math.max(6, (int) (length / 4.0F));
        for (int i = 0; i < n; i++) {
            float k0 = i / (float) n, k1 = (i + 1) / (float) n;
            float p0 = head - length * (1.0F - k0), p1 = head - length * (1.0F - k1);
            float a0 = a * (float) Math.pow(k0, 1.6), a1 = a * (float) Math.pow(k1, 1.6);
            if (length >= 359.0F) {
                a0 = a * (0.55F + 0.45F * (float) Math.pow(k0, 3));
                a1 = a * (0.55F + 0.45F * (float) Math.pow(k1, 3));
            }
            float w0 = width * (0.35F + 0.65F * k0), w1 = width * (0.35F + 0.65F * k1);
            float x0 = -Mth.sin(p0 * Mth.DEG_TO_RAD) * dist, y0 = Mth.cos(p0 * Mth.DEG_TO_RAD) * dist;
            float x1 = -Mth.sin(p1 * Mth.DEG_TO_RAD) * dist, y1 = Mth.cos(p1 * Mth.DEG_TO_RAD) * dist;
            bb.vertex(m, x0, y0, -w0).color(r, g, b, 0.0F).endVertex();
            bb.vertex(m, x0, y0, 0).color(r, g, b, a0).endVertex();
            bb.vertex(m, x1, y1, 0).color(r, g, b, a1).endVertex();
            bb.vertex(m, x1, y1, -w1).color(r, g, b, 0.0F).endVertex();
            bb.vertex(m, x0, y0, 0).color(r, g, b, a0).endVertex();
            bb.vertex(m, x0, y0, w0).color(r, g, b, 0.0F).endVertex();
            bb.vertex(m, x1, y1, w1).color(r, g, b, 0.0F).endVertex();
            bb.vertex(m, x1, y1, 0).color(r, g, b, a1).endVertex();
        }
    }

    /** A star's arc round the celestial pole (the world Z axis) at latitude {@code lat}. */
    private static void starArc(BufferBuilder bb, Matrix4f m, float head, float lat, float length, float width, float a) {
        if (a <= 0.01F) {
            return;
        }
        float dist = 100.0F;
        float cl = Mth.cos(lat * Mth.DEG_TO_RAD), sl = Mth.sin(lat * Mth.DEG_TO_RAD);
        int n = Math.max(4, (int) (length / 6.0F));
        for (int i = 0; i < n; i++) {
            float k0 = i / (float) n, k1 = (i + 1) / (float) n;
            float p0 = (head - length * (1 - k0)) * Mth.DEG_TO_RAD, p1 = (head - length * (1 - k1)) * Mth.DEG_TO_RAD;
            float x0 = -Mth.sin(p0) * cl * dist, y0 = Mth.cos(p0) * cl * dist, z = sl * dist;
            float x1 = -Mth.sin(p1) * cl * dist, y1 = Mth.cos(p1) * cl * dist;
            float a0 = a * k0, a1 = a * k1;
            bb.vertex(m, x0, y0, z - width).color(0.8F, 0.88F, 1.0F, a0).endVertex();
            bb.vertex(m, x0, y0, z + width).color(0.8F, 0.88F, 1.0F, a0).endVertex();
            bb.vertex(m, x1, y1, z + width).color(0.8F, 0.88F, 1.0F, a1).endVertex();
            bb.vertex(m, x1, y1, z - width).color(0.8F, 0.88F, 1.0F, a1).endVertex();
        }
    }
}
