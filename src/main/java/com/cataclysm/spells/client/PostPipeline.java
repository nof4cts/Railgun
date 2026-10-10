package com.cataclysm.spells.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * HDR-style bloom (bright pass → 4-level 13-tap downsample → tent upsample) plus radial god rays
 * from the brightest spell light on screen, composited back over the frame. Only runs while
 * spell effects are alive, so normal gameplay is untouched.
 */
public final class PostPipeline {
    private static TextureTarget scene, half, quarter, eighth, sixteenth, rays;
    private static long activeUntil;
    private static float level;
    private static Vec3 lightWorld;
    private static float lightStrength;
    private static long lightUntil;
    private static int rayTint = 0xFFFFE0B0;

    /** Keep bloom running at the given strength for a little longer (called every frame by live effects). */
    public static void keep(float strength) {
        level = Math.max(level, strength);
        activeUntil = Math.max(activeUntil, Vfx.now() + 120);
    }

    /** A god-ray source this frame. The strongest one wins. */
    public static void light(Vec3 world, float strength, int tint) {
        if (Vfx.now() > lightUntil || strength >= lightStrength) {
            lightWorld = world;
            lightStrength = strength;
            rayTint = tint;
        }
        lightUntil = Vfx.now() + 80;
    }

    private static TextureTarget make(TextureTarget t, int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (t != null && t.width == w && t.height == h) return t;
        if (t != null) t.destroyBuffers();
        TextureTarget n = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        n.setFilterMode(GL11.GL_LINEAR);
        return n;
    }

    private static void quad() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1, -1, 0).uv(0, 0).endVertex();
        b.vertex(1, -1, 0).uv(1, 0).endVertex();
        b.vertex(1, 1, 0).uv(1, 1).endVertex();
        b.vertex(-1, 1, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private static void pass(ShaderInstance sh, RenderTarget dst, int srcTex, int srcW, int srcH, float mode, float intensity) {
        dst.bindWrite(true);
        sh.safeGetUniform("Mode").set(mode);
        sh.safeGetUniform("TexelSize").set(1f / srcW, 1f / srcH);
        sh.safeGetUniform("Intensity").set(intensity);
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, srcTex);
        quad();
    }

    public static void run() {
        ShaderInstance post = SpellShaders.POST, add = SpellShaders.POST_ADD;
        long t = Vfx.now();
        if (post == null || add == null) return;
        if (t > activeUntil) {
            level = 0;
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        scene = make(scene, w, h);
        half = make(half, w / 2, h / 2);
        quarter = make(quarter, w / 4, h / 4);
        eighth = make(eighth, w / 8, h / 8);
        sixteenth = make(sixteenth, w / 16, h / 16);
        rays = make(rays, w / 4, h / 4);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        post.safeGetUniform("Threshold").set(0.78f);
        pass(post, half, scene.getColorTextureId(), w, h, 0f, 1f);
        pass(post, quarter, half.getColorTextureId(), half.width, half.height, 1f, 1f);
        pass(post, eighth, quarter.getColorTextureId(), quarter.width, quarter.height, 1f, 1f);
        pass(post, sixteenth, eighth.getColorTextureId(), eighth.width, eighth.height, 1f, 1f);
        pass(add, eighth, sixteenth.getColorTextureId(), sixteenth.width, sixteenth.height, 2f, 1f);
        pass(add, quarter, eighth.getColorTextureId(), eighth.width, eighth.height, 2f, 1f);
        pass(add, half, quarter.getColorTextureId(), quarter.width, quarter.height, 2f, 1f);

        float rayStrength = 0f;
        float lx = 0.5f, ly = 0.5f;
        if (t < lightUntil && lightWorld != null) {
            float[] p = ScreenFx.projectRaw(lightWorld);
            if (p != null) {
                lx = p[0];
                ly = 1f - p[1];
                float edge = Math.max(Math.abs(p[0] - 0.5f), Math.abs(p[1] - 0.5f));
                rayStrength = lightStrength * Math.max(0f, 1f - Math.max(0f, edge - 0.5f) * 2f);
            }
        }
        rays.setClearColor(0, 0, 0, 1);
        rays.clear(Minecraft.ON_OSX);
        if (rayStrength > 0.01f) {
            post.safeGetUniform("LightPos").set(lx, ly);
            post.safeGetUniform("RayStrength").set(rayStrength);
            post.safeGetUniform("RayTint").set(((rayTint >> 16) & 255) / 255f, ((rayTint >> 8) & 255) / 255f, (rayTint & 255) / 255f);
            pass(post, rays, half.getColorTextureId(), half.width, half.height, 3f, 1f);
        }

        main.bindWrite(true);
        post.safeGetUniform("Mode").set(4f);
        post.safeGetUniform("Intensity").set(0.9f * level + 0.3f);
        RenderSystem.setShader(() -> post);
        RenderSystem.setShaderTexture(0, scene.getColorTextureId());
        RenderSystem.setShaderTexture(1, half.getColorTextureId());
        RenderSystem.setShaderTexture(2, rays.getColorTextureId());
        quad();

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        level *= 0.92f;
    }

    private PostPipeline() {}
}
