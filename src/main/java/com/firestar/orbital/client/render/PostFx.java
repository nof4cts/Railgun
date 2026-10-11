package com.firestar.orbital.client.render;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.client.Strikes;
import com.firestar.orbital.client.fx.ScreenFx;
import com.firestar.orbital.config.OConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The post pipeline, run at the very end of the level render whenever a technique is playing: copy the frame, bloom
 * it (6 levels down, 5 up), then one composite pass that writes the final picture back into Minecraft's framebuffer.
 */
public final class PostFx {
    private static final int LEVELS = 6;
    private static GlProgram down;
    private static GlProgram up;
    private static GlProgram composite;
    private static String failure;
    private static boolean warned;
    private static RenderTex scene;
    private static final RenderTex[] DOWN = new RenderTex[LEVELS];
    private static final RenderTex[] UP = new RenderTex[LEVELS - 1];
    private static int vao = -1;
    private static final long T0 = System.nanoTime();
    private static final ScreenFx FX = new ScreenFx();

    private PostFx() {}

    public static void reload(ResourceManager rm) {
        RenderSystem.recordRenderCall(() -> load(rm));
    }

    private static void load(ResourceManager rm) {
        closePrograms();
        try {
            down = GlProgram.load(rm, "fullscreen.vsh", "bloom_down.fsh");
            up = GlProgram.load(rm, "fullscreen.vsh", "bloom_up.fsh");
            composite = GlProgram.load(rm, "fullscreen.vsh", "composite.fsh");
            failure = null;
            warned = false;
            OrbitalMod.LOGGER.info("Orbital Arsenal effect shaders compiled");
        } catch (Exception e) {
            closePrograms();
            failure = e.getMessage();
            OrbitalMod.LOGGER.error("Orbital Arsenal effect shaders failed to compile; screen effects disabled", e);
        }
    }

    private static void closePrograms() {
        for (GlProgram p : new GlProgram[]{down, up, composite}) {
            if (p != null) {
                p.close();
            }
        }
        down = up = composite = null;
    }

    private static void ensureTargets(int w, int h) {
        if (scene != null && scene.width == w && scene.height == h) {
            return;
        }
        closeTargets();
        scene = new RenderTex(w, h, false);
        int lw = w, lh = h;
        for (int i = 0; i < LEVELS; i++) {
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
            DOWN[i] = new RenderTex(lw, lh, true);
            if (i < LEVELS - 1) {
                UP[i] = new RenderTex(lw, lh, true);
            }
        }
        if (vao < 0) {
            vao = GL30.glGenVertexArrays();
        }
    }

    private static void closeTargets() {
        if (scene != null) {
            scene.close();
        }
        scene = null;
        for (int i = 0; i < LEVELS; i++) {
            if (DOWN[i] != null) DOWN[i].close();
            DOWN[i] = null;
            if (i < LEVELS - 1) {
                if (UP[i] != null) UP[i].close();
                UP[i] = null;
            }
        }
    }

    /** The picture this frame should have, for the camera shake (applied to the camera angles) and the HUD. */
    public static ScreenFx current() {
        return FX;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        FX.reset();
        if (mc.level == null) {
            return;
        }
        Strikes.screen(FX, event.getPartialTick());
        if (FX.idle()) {
            return;
        }
        if (composite == null) {
            if (failure != null && !warned && mc.player != null) {
                warned = true;
                mc.player.displayClientMessage(Component.literal("§cOrbital Arsenal: screen-effect shaders failed to compile on this GPU (see latest.log)."), false);
            }
            return;
        }
        try {
            draw(mc, event);
        } catch (Throwable t) {
            OrbitalMod.LOGGER.error("Orbital Arsenal post pipeline failed; disabling screen effects until resources reload", t);
            failure = String.valueOf(t.getMessage());
            closePrograms();
        } finally {
            restore(mc);
        }
    }

    private static void draw(Minecraft mc, RenderLevelStageEvent event) {
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        ensureTargets(w, h);
        float time = (System.nanoTime() - T0) / 1.0E9F % 3600.0F;

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GL30.glBindVertexArray(vao);

        // copy the finished frame
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // bloom: down
        float bloomCfg = OConfig.CLIENT.bloom.get().floatValue();
        down.use();
        down.set("Src", 0);
        down.set("Threshold", 0.78F);
        RenderTex src = scene;
        for (int i = 0; i < LEVELS; i++) {
            DOWN[i].bindWrite();
            bind(0, src.texture);
            down.set("SrcTexel", 1.0F / src.width, 1.0F / src.height);
            down.set("Prefilter", i == 0 ? 1.0F : 0.0F);
            tri();
            src = DOWN[i];
        }
        // bloom: up
        up.use();
        up.set("Low", 0);
        up.set("Cur", 1);
        up.set("Radius", 1.0F);
        for (int i = LEVELS - 2; i >= 0; i--) {
            RenderTex low = i == LEVELS - 2 ? DOWN[LEVELS - 1] : UP[i + 1];
            UP[i].bindWrite();
            bind(0, low.texture);
            bind(1, DOWN[i].texture);
            up.set("LowTexel", 1.0F / low.width, 1.0F / low.height);
            tri();
        }

        // composite back into Minecraft's framebuffer
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._viewport(0, 0, w, h);
        GlProgram c = composite;
        c.use();
        bind(0, scene.texture);
        bind(1, UP[0].texture);
        c.set("Scene", 0);
        c.set("Bloom", 1);
        c.set("ScreenSize", (float) w, (float) h);
        c.set("Time", time);
        float flashCfg = OConfig.CLIENT.flash.get().floatValue();
        c.set("Flash", FX.flashR, FX.flashG, FX.flashB, FX.flash * flashCfg);
        c.set("Punch", Math.min(FX.punch, 1.2F));
        c.set("Aberration", FX.aberration * OConfig.CLIENT.aberration.get().floatValue());
        c.set("Desaturate", FX.desaturate);
        c.set("Contrast", FX.contrast);
        c.set("Vignette", FX.vignetteR, FX.vignetteG, FX.vignetteB, FX.vignette);
        c.set("Grain", FX.grain * OConfig.CLIENT.grain.get().floatValue());
        c.set("Haki", FX.haki);
        c.set("BloomStrength", (0.35F + FX.bloom) * bloomCfg);
        c.set("Accent", FX.accentR, FX.accentG, FX.accentB);
        c.set("Shake", 0.0F, 0.0F);

        float iu = 0.5F, iv = 0.5F;
        if (FX.impactAt != null) {
            Vector4f s = project(FX.impactAt.subtract(event.getCamera().getPosition()), event.getProjectionMatrix());
            if (s != null) {
                iu = Mth.clamp(s.x, 0.08F, 0.92F);
                iv = Mth.clamp(s.y, 0.08F, 0.92F);
            }
        }
        boolean frames = OConfig.CLIENT.impactFrames.get() && FX.impactMode > 0;
        c.set("Impact", frames ? FX.impactMode : 0.0F, frames ? FX.impactAmount : 0.0F, iu, iv);
        c.set("ImpactSeed", FX.impactSeed);
        tri();
    }

    private static Vector4f project(Vec3 rel, Matrix4f proj) {
        Matrix3f invViewRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        Matrix3f viewRot = new Matrix3f(invViewRot).invert();
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        viewRot.transform(v);
        Vector4f clip = new Vector4f(v, 1.0F);
        proj.transform(clip);
        if (clip.w <= 0.0F) {
            return null;
        }
        return new Vector4f(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F, 0.0F, 0.0F);
    }

    private static void bind(int unit, int texture) {
        GlStateManager._activeTexture(GL30.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
    }

    private static void tri() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void restore(Minecraft mc) {
        GL30.glBindVertexArray(0);
        BufferUploader.reset();
        bind(1, 0);
        bind(0, 0);
        GlStateManager._glUseProgram(0);
        ShaderInstance any = GameRenderer.getPositionTexShader();
        if (any != null) {
            any.clear();
        }
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }
}
