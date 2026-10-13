package com.firestar.rose.client.gfx;

import com.firestar.rose.RoseConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Requiem Rose's renderer. Each frame: the techniques describe their light ({@link Frame}); the smoke, ink and rifts are
 * blended into the world; the light itself is drawn into a separate glow buffer that shares the world's depth (so walls
 * hide it), the bending of space into a warp buffer; at the end of the level the glow is blurred into a five-level
 * bloom and added to the picture, and the lens ({@link Look}) grades the whole frame.
 */
public final class Canvas {
    private static TextureTarget glow, warp, scene;
    private static final TextureTarget[] BLOOM = new TextureTarget[6];
    private static int w = -1, h = -1;
    private static boolean glowUsed, warpUsed;
    public static final Look LOOK = new Look();
    private static Matrix4f proj = new Matrix4f();
    private static Frame last;
    /** What the renderer did last frame (for the build server's log). */
    public static String debug = "";
    /** Build-server probes: read back the picture's centre at each stage; {@code probe} switches stages off. */
    public static boolean probing;
    public static int probe;

    static void px(String tag) {
        if (!probing) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int prev = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        java.nio.ByteBuffer b = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(main.width / 2, main.height * 3 / 4, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prev);
        debug += " " + tag + "=" + (b.get(0) & 255) + "," + (b.get(1) & 255) + "," + (b.get(2) & 255) + "," + (b.get(3) & 255)
                + "/blend" + (GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0) + "/err" + GL11.glGetError();
    }

    /** The last frame's shapes and camera (for placing screen effects on world points). */
    public static Frame lastFrame() {
        return last;
    }

    private Canvas() {}

    private static TextureTarget target(int tw, int th, boolean depth) {
        TextureTarget t = new TextureTarget(Math.max(1, tw), Math.max(1, th), depth, Minecraft.ON_OSX);
        t.setClearColor(0, 0, 0, 0);
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    private static void size(RenderTarget main) {
        if (main.width == w && main.height == h && glow != null) {
            return;
        }
        w = main.width;
        h = main.height;
        if (glow != null) {
            glow.destroyBuffers();
            warp.destroyBuffers();
            scene.destroyBuffers();
            for (TextureTarget b : BLOOM) {
                b.destroyBuffers();
            }
        }
        glow = target(w, h, true);
        warp = target(w, h, true);
        scene = target(w, h, false);
        for (int i = 0; i < BLOOM.length; i++) {
            BLOOM[i] = target(w >> (i + 1), h >> (i + 1), false);
        }
    }

    public static float time() {
        return (float) ((System.nanoTime() / 1.0E9) % 3600.0);
    }

    // ------------------------------------------------------------------------------------------------ the world pass

    /**
     * After particles: gather this frame's shapes and draw them. {@code view} is the camera's rotation, {@code projection}
     * the level projection.
     */
    public static void world(PoseStack view, Matrix4f projection, Camera camera, float partial) {
        glowUsed = warpUsed = false;
        debug = "probe=" + probe + ";";
        px("w0");
        if (!RoseShaders.ready()) {
            com.firestar.rose.client.ScytheLayer.blades(null, view);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        size(main);
        proj = new Matrix4f(projection);
        Vector3f lk = camera.getLookVector(), up = camera.getUpVector(), lf = camera.getLeftVector();
        Matrix4f vp = new Matrix4f(projection).mul(view.last().pose());
        Frame f = new Frame(camera.getPosition(), new Vec3(lk.x(), lk.y(), lk.z()), new Vec3(up.x(), up.y(), up.z()),
                new Vec3(lf.x(), lf.y(), lf.z()), time(), vp);
        last = f;
        com.firestar.rose.client.RoseChoreo.frame(f, partial);
        Embers.draw(f, partial);
        com.firestar.rose.client.ScytheLayer.blades(f, view);
        boolean aura = AuraRenderer.any(partial);
        debug += "aura=" + aura + " shapes=" + !f.empty() + " drawn=" + AuraRenderer.debug() + ";";
        if (f.empty() && !aura) {
            return;
        }
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.mulPoseMatrix(view.last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        float t = f.time;

        // the world itself: the aura (exactly as Dragon Block Rebirth draws it), smoke, the ink under every cut, the rifts
        if (probe != 2) {
            AuraRenderer.draw(view, projection, partial, 1.0F);
        }
        px("w1");
        draw(RoseShaders.SMOKE, t, Geometry.dots(f, f.smokes));
        draw(RoseShaders.ARC_INK, t, Geometry.arcs(f, f.inks));
        draw(RoseShaders.RIFT, t, Geometry.rifts(f, f.rifts));
        px("w2");

        // the light, into its own buffer, hidden by the world's depth
        if (!f.arcs.isEmpty() || !f.beams.isEmpty() || !f.rings.isEmpty() || !f.dots.isEmpty() || !f.rifts.isEmpty() || aura) {
            glowUsed = true;
            glow.clear(Minecraft.ON_OSX);
            glow.copyDepthFrom(main);
            glow.bindWrite(true);
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            // the aura's own bloom, gentle (its glow comes from the light of the shell, not a second shell)
            if (probe != 2) {
                AuraRenderer.draw(view, projection, partial, 0.16F);
            }
            draw(RoseShaders.ARC, t, Geometry.arcs(f, f.arcs));
            draw(RoseShaders.BEAM, t, Geometry.beams(f, f.beams));
            draw(RoseShaders.RING, t, Geometry.rings(f, f.rings));
            draw(RoseShaders.FLARE, t, Geometry.dots(f, f.dots));
            draw(RoseShaders.RIFT_GLOW, t, Geometry.rifts(f, f.rifts));
        }
        // the bending of space
        if (RoseConfig.CLIENT.distortion.get() && (!f.warpArcs.isEmpty() || !f.warpRings.isEmpty() || !f.rifts.isEmpty())) {
            warpUsed = true;
            warp.clear(Minecraft.ON_OSX);
            warp.copyDepthFrom(main);
            warp.bindWrite(true);
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            draw(RoseShaders.WARP, t, Geometry.warp(f));
        }
        main.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        px("w3");
        if (probe == 4) {
            glowUsed = false;
        }
    }

    static void draw(ShaderInstance sh, BufferBuilder.RenderedBuffer buf) {
        if (buf == null) {
            return;
        }
        if (sh == null) {
            buf.release();
            return;
        }
        blend(sh);
        RenderSystem.setShader(() -> sh);
        BufferUploader.drawWithShader(buf);
    }

    /**
     * Sets the blend state a program needs, every time. Minecraft only re-applies a shader's blend mode when it differs
     * from the last one it applied itself, so vanilla rendering in between can leave blending switched off: smoke and
     * ink then draw as solid quads. Setting it here first makes every draw exact whatever ran before.
     */
    static void blend(ShaderInstance sh) {
        // every shape is seen from both sides (a slash turning away from the camera must not vanish)
        RenderSystem.disableCull();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        if (sh == RoseShaders.ARC_INK || sh == RoseShaders.SMOKE || sh == RoseShaders.RIFT) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else if (sh == RoseShaders.BLOOM_DOWN || sh == RoseShaders.GRADE) {
            RenderSystem.disableBlend();
        } else {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        }
    }

    static void draw(ShaderInstance sh, float time, BufferBuilder.RenderedBuffer buf) {
        if (sh != null && buf != null) {
            sh.safeGetUniform("Time").set(time);
        }
        draw(sh, buf);
    }

    // ------------------------------------------------------------------------------------------------ the lens pass

    /** At the end of the level: bloom the glow onto the picture, then grade it. */
    public static void lens(float partial) {
        if (!RoseShaders.ready() || glow == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != w || main.height != h) {
            return;
        }
        LOOK.reset();
        com.firestar.rose.client.RoseChoreo.screen(LOOK, partial);
        boolean grade = !LOOK.idle() || warpUsed || probe == 3;
        px("l0");
        debug += " glow=" + glowUsed + " warp=" + warpUsed + " grade=" + grade + ";";
        if (!glowUsed && !grade) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (glowUsed && probe != 1) {
            bloom();
            ShaderInstance c = RoseShaders.COMPOSITE;
            c.setSampler("Glow", glow);
            c.setSampler("Bloom", BLOOM[0]);
            c.safeGetUniform("Strength").set(RoseConfig.CLIENT.bloom.get().floatValue());
            main.bindWrite(true);
            quad(c);
            px("l1");
        }
        if (grade) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.bindWrite(true);
            ShaderInstance g = RoseShaders.GRADE;
            Look l = LOOK;
            g.setSampler("Scene", scene);
            g.setSampler("Depth", main.getDepthTextureId());
            g.setSampler("Warp", warp);
            g.safeGetUniform("Res").set((float) w, (float) h);
            g.safeGetUniform("Time").set(time());
            g.safeGetUniform("Flash").set(l.flashR, l.flashG, l.flashB, l.flash);
            g.safeGetUniform("Chroma").set(l.chroma);
            g.safeGetUniform("Zoom").set(l.zoomX, l.zoomY, l.zoom);
            g.safeGetUniform("Vignette").set(l.vigR, l.vigG, l.vigB, l.vignette);
            g.safeGetUniform("Bars").set(l.bars);
            g.safeGetUniform("Grain").set(l.grain);
            g.safeGetUniform("Desat").set(l.desat);
            g.safeGetUniform("Tint").set(1.0F, 0.4F, 0.7F, l.tint);
            g.safeGetUniform("Sky").set(l.sky, l.skyDim);
            g.safeGetUniform("Impact").set((float) l.impactMode, l.impact, l.impactX, l.impactY);
            g.safeGetUniform("Accent").set(l.accR, l.accG, l.accB);
            g.safeGetUniform("Split").set(l.splitNX, l.splitNY, l.splitOff, l.split);
            g.safeGetUniform("Lines").set(l.lines, l.linesX, l.linesY);
            g.safeGetUniform("WarpOn").set(warpUsed ? 1.0F : 0.0F);
            float far = mc.gameRenderer.getDepthFar();
            g.safeGetUniform("Near").set(0.05F, far);
            quad(g);
        }
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        px("l2");
    }

    /** The end of the frame (build server only). */
    public static void endProbe() {
        px("end");
    }

    private static void bloom() {
        ShaderInstance down = RoseShaders.BLOOM_DOWN, up = RoseShaders.BLOOM_UP;
        RenderTarget src = glow;
        for (int i = 0; i < BLOOM.length; i++) {
            TextureTarget dst = BLOOM[i];
            down.setSampler("Src", src);
            down.safeGetUniform("Texel").set(1.0F / src.width, 1.0F / src.height);
            down.safeGetUniform("First").set(i == 0 ? 1.0F : 0.0F);
            dst.bindWrite(true);
            quad(down);
            src = dst;
        }
        for (int i = BLOOM.length - 1; i > 0; i--) {
            TextureTarget s = BLOOM[i], d = BLOOM[i - 1];
            up.setSampler("Src", s);
            up.safeGetUniform("Texel").set(1.0F / s.width, 1.0F / s.height);
            up.safeGetUniform("Weight").set(1.0F);
            d.bindWrite(true);
            quad(up);
        }
    }

    private static void quad(ShaderInstance sh) {
        blend(sh);
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.vertex(-1, -1, 0).endVertex();
        b.vertex(1, -1, 0).endVertex();
        b.vertex(1, 1, 0).endVertex();
        b.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /**
     * The first-person scythe's blade, drawn straight into the hand view: {@code m} maps the hand's frame to view space.
     */
    public static void handBlade(Matrix4f m, float k, float heat) {
        if (!RoseShaders.ready()) {
            return;
        }
        AuraRenderer.handScythe(m, k);
        Frame f = new Frame(Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(-1, 0, 0), time(), new Matrix4f());
        com.firestar.rose.client.ScytheLayer.blade(f, m, new Matrix4f(), k, heat);
        if (f.empty()) {
            return;
        }
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        draw(RoseShaders.ARC_INK, f.time, Geometry.arcs(f, f.inks));
        draw(RoseShaders.ARC, f.time, Geometry.arcs(f, f.arcs));
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public static Matrix4f projection() {
        return proj;
    }

    static boolean has(List<?> l) {
        return !l.isEmpty();
    }
}
