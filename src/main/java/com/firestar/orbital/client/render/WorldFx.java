package com.firestar.orbital.client.render;

import com.firestar.orbital.OrbitalMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The strikes drawn into the finished world with its depth (see world.fsh): the volumetric lance and the light it casts,
 * molten ground, heat haze and shockwaves; the cracked crimson sky, the other world's land ray-marched overhead, and the
 * lightning between. Runs after the weather and before the post pipeline.
 */
public final class WorldFx {
    private WorldFx() {}

    /** Everything the pass draws this frame; filled by the strikes, cleared every frame. */
    public static final class State {
        public Vec3 beam;
        public float beamRadius, beamTop, beamHead, beamIntensity, molten;
        public Vec3 shock;
        public float shockRadius, shockThickness, shockStrength;
        public int shockColour = 0xFFFFFF;
        public Vec3 mirror;
        public float mirrorRadius, mirrorBase, mirrorAlpha, mirrorFlash, mirrorSpan = 4.5F;
        public float crackGrowth, crackAlpha, crackHeight = 170, crackSeed;
        public float crimson, lightning;

        void reset() {
            beam = shock = mirror = null;
            beamRadius = beamIntensity = molten = 0;
            shockRadius = shockStrength = 0;
            mirrorRadius = mirrorAlpha = mirrorFlash = 0;
            crackGrowth = crackAlpha = 0;
            crimson = lightning = 0;
        }

        boolean idle() {
            return (beam == null || beamIntensity <= 0) && (shock == null || shockStrength <= 0) && (mirror == null) && crimson <= 0;
        }
    }

    public static final State STATE = new State();
    private static GlProgram program;
    private static RenderTex scene, out;
    private static int vao = -1;
    private static final long T0 = System.nanoTime();

    public static void reload(ResourceManager rm) {
        RenderSystem.recordRenderCall(() -> {
            if (program != null) {
                program.close();
                program = null;
            }
            try {
                program = GlProgram.load(rm, "fullscreen.vsh", "world.fsh");
                OrbitalMod.LOGGER.info("Orbital Arsenal world shader compiled");
            } catch (Exception e) {
                OrbitalMod.LOGGER.error("Orbital Arsenal world shader failed to compile; strikes will be drawn without it", e);
            }
        });
    }

    public static boolean available() {
        return program != null;
    }

    public static void begin() {
        STATE.reset();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || program == null || STATE.idle()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        try {
            if (scene == null || scene.width != w || scene.height != h) {
                if (scene != null) {
                    scene.close();
                    out.close();
                }
                scene = new RenderTex(w, h, false);
                out = new RenderTex(w, h, false);
            }
            if (vao < 0) {
                vao = GL30.glGenVertexArrays();
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            out.bindWrite();
            GL30.glBindVertexArray(vao);
            GlProgram p = program;
            p.use();
            GlStateManager._activeTexture(GL30.GL_TEXTURE0);
            GlStateManager._bindTexture(scene.texture);
            GlStateManager._activeTexture(GL30.GL_TEXTURE1);
            GlStateManager._bindTexture(main.getDepthTextureId());
            p.set("Scene", 0);
            p.set("Depth", 1);
            Matrix4f projView = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
            p.set("ProjView", projView);
            p.set("InvProjView", new Matrix4f(projView).invert());
            p.set("ScreenSize", (float) w, (float) h);
            p.set("Time", (System.nanoTime() - T0) / 1.0E9F % 3600.0F);
            Vec3 cam = event.getCamera().getPosition();
            State s = STATE;
            if (s.beam != null && s.beamIntensity > 0) {
                Vec3 b = s.beam.subtract(cam);
                p.set("Beam", (float) b.x, (float) b.y, (float) b.z, s.beamRadius);
                p.set("BeamP", (float) (s.beamTop - cam.y), (float) (s.beamHead - cam.y), s.beamIntensity, s.molten);
            } else {
                p.set("Beam", 0, 0, 0, 0);
            }
            if (s.shock != null && s.shockStrength > 0) {
                Vec3 c = s.shock.subtract(cam);
                p.set("Shock", (float) c.x, (float) c.y, (float) c.z, s.shockRadius);
                p.set("ShockP", s.shockThickness, s.shockStrength, 0, 0);
                p.set("ShockC", (s.shockColour >> 16 & 255) / 255.0F, (s.shockColour >> 8 & 255) / 255.0F, (s.shockColour & 255) / 255.0F);
            } else {
                p.set("Shock", 0, 0, 0, 0);
            }
            if (s.mirror != null) {
                Vec3 m = s.mirror.subtract(cam);
                p.set("Mirror", (float) m.x, (float) m.y, (float) m.z, s.mirrorRadius);
                p.set("MirrorP", s.mirrorBase, s.mirrorAlpha, s.mirrorFlash, s.mirrorSpan);
                p.set("Cracks", s.crackGrowth, s.crackAlpha, s.crackHeight, s.crackSeed);
            } else {
                p.set("Mirror", 0, 0, 0, 0);
            }
            p.set("Crimson", s.crimson);
            p.set("Lightning", s.lightning);
            p.set("CamY", (float) cam.y);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, out.fbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } catch (Throwable t) {
            OrbitalMod.LOGGER.error("Orbital Arsenal world pass failed; disabling it until resources reload", t);
            program = null;
        } finally {
            GL30.glBindVertexArray(0);
            BufferUploader.reset();
            GlStateManager._activeTexture(GL30.GL_TEXTURE1);
            GlStateManager._bindTexture(0);
            GlStateManager._activeTexture(GL30.GL_TEXTURE0);
            GlStateManager._bindTexture(0);
            GlStateManager._glUseProgram(0);
            ShaderInstance any = GameRenderer.getPositionTexShader();
            if (any != null) {
                any.clear();
            }
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
    }
}
