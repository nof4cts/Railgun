package com.cataclysm.spells.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * The sky tears open to deep space: a procedural starfield and nebula painted behind the
 * terrain (drawn right after the vanilla sky, without writing depth, so the world covers it).
 */
public final class SpaceSky {
    private static long start, fadeIn, hold, fadeOut;
    private static float max;
    private static int tint = 0xFFFFFFFF;

    public static void open(float strength, long in, long holdMs, long out, int color) {
        start = Vfx.now();
        fadeIn = in; hold = holdMs; fadeOut = out; max = strength; tint = color;
    }

    static float alpha() {
        long e = Vfx.now() - start;
        if (max <= 0) return 0;
        if (e < fadeIn) return max * e / (float) Math.max(1, fadeIn);
        if (e < fadeIn + hold) return max;
        float k = 1f - (e - fadeIn - hold) / (float) Math.max(1, fadeOut);
        if (k <= 0) {
            max = 0;
            return 0;
        }
        return max * k;
    }

    public static void render(PoseStack ps) {
        ShaderInstance sh = SpellShaders.SPACE;
        float a = alpha();
        if (sh == null || a <= 0.005f) return;
        sh.safeGetUniform("Alpha").set(a);
        sh.safeGetUniform("Time").set((Vfx.now() % 1000000) / 1000f);
        sh.safeGetUniform("Tint").set(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sh);
        Matrix4f m = ps.last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int lat = 24, lon = 48;
        float R = 50f;
        for (int i = 0; i < lat; i++) {
            float e0 = (float) (-Math.PI / 2 + Math.PI * i / lat), e1 = (float) (-Math.PI / 2 + Math.PI * (i + 1) / lat);
            for (int j = 0; j < lon; j++) {
                float a0 = (float) (2 * Math.PI * j / lon), a1 = (float) (2 * Math.PI * (j + 1) / lon);
                vert(b, m, R, e0, a0);
                vert(b, m, R, e0, a1);
                vert(b, m, R, e1, a1);
                vert(b, m, R, e1, a0);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void vert(BufferBuilder b, Matrix4f m, float R, float el, float az) {
        float x = (float) (Math.cos(el) * Math.cos(az)) * R, y = (float) Math.sin(el) * R, z = (float) (Math.cos(el) * Math.sin(az)) * R;
        b.vertex(m, x, y, z).uv((float) (az / (2 * Math.PI)), (float) (el / Math.PI + 0.5)).endVertex();
    }

    public static void clear() {
        max = 0;
    }

    private SpaceSky() {}
}
