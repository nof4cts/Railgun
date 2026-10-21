package com.yuji.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.yuji.Y;
import com.yuji.client.render.GlProgram;
import com.yuji.client.render.RenderTex;
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
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Yuji's picture: his effects are drawn into the finished frame by one world-space shader (yuji.fsh) reading the
 * depth buffer, then bloomed, then composited (ycomposite.fsh) with the impact frames, ink, smears, cracks,
 * shockwave and grade. Nothing here uses Minecraft particles.
 */
public final class YPost {
    private static final int LEVELS = 6;
    private static GlProgram world, down, up, composite;
    private static String failure;
    private static boolean warned;
    private static RenderTex scene, hdr;
    private static final RenderTex[] DOWN = new RenderTex[LEVELS];
    private static final RenderTex[] UP = new RenderTex[LEVELS - 1];
    private static int vao = -1, fxTex = -1;
    private static FloatBuffer fxBuf;
    private static final long T0 = System.nanoTime();
    // the camera of the frame being drawn (for projecting points to the screen)
    private static Matrix4f proj = new Matrix4f();
    private static Matrix3f viewRot = new Matrix3f();
    private static Vec3 camPos = Vec3.ZERO;

    private YPost() {}

    public static void reload(ResourceManager rm) {
        closePrograms();
        try {
            world = GlProgram.load(rm, "fullscreen.vsh", "yuji.fsh");
            down = GlProgram.load(rm, "fullscreen.vsh", "bloom_down.fsh");
            up = GlProgram.load(rm, "fullscreen.vsh", "bloom_up.fsh");
            composite = GlProgram.load(rm, "fullscreen.vsh", "ycomposite.fsh");
            failure = null;
            warned = false;
            Y.LOG.info("[yuji] effect shaders compiled");
        } catch (Exception e) {
            closePrograms();
            failure = e.getMessage();
            Y.LOG.error("[yuji] effect shaders failed to compile; effects disabled", e);
        }
    }

    private static void closePrograms() {
        for (GlProgram p : new GlProgram[]{world, down, up, composite}) if (p != null) p.close();
        world = down = up = composite = null;
    }

    /** Early in the frame: remember the camera (for projecting points to the screen). */
    public static void begin(RenderLevelStageEvent e) {
        proj = new Matrix4f(e.getProjectionMatrix());
        Matrix3f inv = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        viewRot = new Matrix3f(inv).invert();
        camPos = e.getCamera().getPosition();
    }

    /** Screen-space direction (uv units, y up) of a movement from a to b. */
    public static float[] screenDir(Vec3 a, Vec3 b) {
        Vector4f pa = project(a.subtract(camPos)), pb = project(b.subtract(camPos));
        if (pa == null || pb == null) return new float[]{1, 0};
        float dx = pb.x - pa.x, dy = pb.y - pa.y;
        if (dx * dx + dy * dy < 1e-8) return new float[]{1, 0};
        return new float[]{dx, dy};
    }

    private static Vector4f project(Vec3 rel) {
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        viewRot.transform(v);
        Vector4f clip = new Vector4f(v, 1.0f);
        proj.transform(clip);
        if (clip.w <= 0) return null;
        return new Vector4f(clip.x / clip.w * 0.5f + 0.5f, clip.y / clip.w * 0.5f + 0.5f, 0, 0);
    }

    public static void render(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (world == null) {
            if (failure != null && !warned && mc.player != null && YCasts.any()) {
                warned = true;
                mc.player.displayClientMessage(Component.literal("§cModulo Yuji: effect shaders failed to compile on this GPU; see latest.log."), false);
            }
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        YCasts.Frame fx = YCasts.pack(cam, e.getPartialTick());
        if (!fx.any() && !YFx.active()) return;
        try {
            draw(mc, e, fx, cam);
        } catch (Throwable t) {
            Y.LOG.error("[yuji] post pipeline failed; effects off until resources reload", t);
            failure = String.valueOf(t.getMessage());
            closePrograms();
        } finally {
            restore(mc);
        }
    }

    private static void ensure(int w, int h) {
        if (scene != null && scene.width == w && scene.height == h) return;
        closeTargets();
        scene = new RenderTex(w, h, false);
        hdr = new RenderTex(w, h, true);
        int lw = w, lh = h;
        for (int i = 0; i < LEVELS; i++) {
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
            DOWN[i] = new RenderTex(lw, lh, true);
            if (i < LEVELS - 1) UP[i] = new RenderTex(lw, lh, true);
        }
        if (vao < 0) vao = GL30.glGenVertexArrays();
        if (fxTex < 0) {
            fxTex = GL11.glGenTextures();
            GlStateManager._bindTexture(fxTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, 0x8814, 4, YCasts.ROWS, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            GlStateManager._bindTexture(0);
            fxBuf = MemoryUtil.memAllocFloat(YCasts.ROWS * 16);
        }
    }

    private static void closeTargets() {
        if (scene != null) scene.close();
        if (hdr != null) hdr.close();
        for (int i = 0; i < LEVELS; i++) {
            if (DOWN[i] != null) DOWN[i].close();
            DOWN[i] = null;
            if (i < LEVELS - 1) {
                if (UP[i] != null) UP[i].close();
                UP[i] = null;
            }
        }
        scene = hdr = null;
    }

    private static void draw(Minecraft mc, RenderLevelStageEvent e, YCasts.Frame fx, Vec3 cam) {
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        ensure(w, h);
        Matrix4f invProj = new Matrix4f(proj).invert();
        Matrix3f invViewRot = new Matrix3f(viewRot).invert();
        float time = (System.nanoTime() - T0) / 1.0e9f % 3600f;

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GL30.glBindVertexArray(vao);

        // the frame as Minecraft drew it
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // effect data
        fxBuf.clear();
        fxBuf.put(fx.data).flip();
        GlStateManager._activeTexture(GL30.GL_TEXTURE0);
        GlStateManager._bindTexture(fxTex);
        // Upload from our own memory with clean unpack state. Minecraft (and other mods) leave row-length / skip
        // settings and sometimes a pixel-unpack buffer bound; with those, NVIDIA's driver reads far outside our
        // buffer and kills the game (EXCEPTION_ACCESS_VIOLATION in nvoglv64.dll).
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, GL11.GL_FALSE);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 4, YCasts.ROWS, GL11.GL_RGBA, GL11.GL_FLOAT, fxBuf);

        // 1. the world pass
        hdr.bindWrite();
        GlProgram p = world;
        p.use();
        bind(0, scene.texture);
        bind(1, main.getDepthTextureId());
        bind(2, fxTex);
        p.set("SceneTex", 0);
        p.set("DepthTex", 1);
        p.set("FxData", 2);
        p.set("Proj", proj);
        p.set("InvProj", invProj);
        p.set("ViewRot", viewRot);
        p.set("InvViewRot", invViewRot);
        p.set("Time", time);
        p.set("Steps", 48);
        String[] names = {"NSlash", "NFlash", "NRing", "NDust", "NBolt", "NOrb", "NShell", "NCharge", "NAura", "NMark", "NTrench", "NSphere"};
        for (int i = 0; i < names.length; i++) p.set(names[i], fx.n[i]);
        if (fx.domC != null) {
            p.set("DomC", (float) (fx.domC.x - cam.x), (float) (fx.domC.y - cam.y), (float) (fx.domC.z - cam.z), fx.domR);
            p.set("DomP", fx.domAge, fx.domStrength, fx.domSeed, 1f);
        } else {
            p.set("DomC", 0, 0, 0, 0);
            p.set("DomP", 0, 0, 0, 0);
        }
        p.set("Dark", fx.dark, 0.85f, 0.62f, 0.66f);
        p.set("Overdrive", fx.overdrive);
        tri();

        // 2. bloom
        RenderTex src = hdr;
        down.use();
        down.set("Src", 0);
        down.set("Threshold", 1.2f);
        for (int i = 0; i < LEVELS; i++) {
            DOWN[i].bindWrite();
            bind(0, src.texture);
            down.set("SrcTexel", 1f / src.width, 1f / src.height);
            down.set("Prefilter", i == 0 ? 1f : 0f);
            tri();
            src = DOWN[i];
        }
        up.use();
        up.set("Low", 0);
        up.set("Cur", 1);
        up.set("Radius", 1f);
        up.set("LowWeight", 0.6f);
        for (int i = LEVELS - 2; i >= 0; i--) {
            RenderTex low = i == LEVELS - 2 ? DOWN[LEVELS - 1] : UP[i + 1];
            UP[i].bindWrite();
            bind(0, low.texture);
            bind(1, DOWN[i].texture);
            up.set("LowTexel", 1f / low.width, 1f / low.height);
            tri();
        }

        // 3. composite back into Minecraft's frame
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._viewport(0, 0, w, h);
        GlProgram c = composite;
        c.use();
        bind(0, hdr.texture);
        bind(1, UP[0].texture);
        bind(2, hdr.texture);   // distances ride in its alpha (never sample the depth buffer being drawn into)
        c.set("Hdr", 0);
        c.set("Bloom", 1);
        c.set("Depth", 2);
        c.set("InvProj", invProj);
        c.set("ScreenSize", (float) w, (float) h);
        c.set("Time", time);
        c.set("BloomStrength", 0.45f);
        c.set("Exposure", 1f);
        c.set("Aberration", YFx.aberrationAmount() + (fx.domStrength > 0 ? 0.1f * fx.domStrength : 0f));
        float[] fc = YFx.flashColor();
        c.set("Flash", fc[0], fc[1], fc[2], YFx.flashAmount());
        c.set("Afterimage", YFx.afterimageAmount());
        c.set("Desaturate", Math.min(0.6f, fx.dark * 0.5f));
        c.set("Vignette", 0.25f + 0.35f * fx.dark + 0.2f * fx.domStrength + 0.15f * fx.overdrive);
        c.set("Grain", 0.18f);
        float shock = YFx.shockStrength();
        Vector4f sc = shock > 0 && YFx.shockAt() != null ? project(YFx.shockAt().subtract(cam)) : null;
        if (sc != null) c.set("Shockwave", sc.x, sc.y, YFx.shockRadius(), shock);
        else c.set("Shockwave", 0.5f, 0.5f, 0f, 0f);
        float[] frame = YFx.frame();
        if (frame != null) {
            float iu = 0.5f, iv = 0.5f;
            Vec3 at = YFx.impactWorld();
            Vector4f ip = at == null ? null : project(at.subtract(cam));
            if (ip != null) {
                iu = Mth.clamp(ip.x, 0.05f, 0.95f);
                iv = Mth.clamp(ip.y, 0.05f, 0.95f);
            }
            c.set("Impact", frame[0], frame[1] * (float) Math.min(1.0, Y.flash), iu, iv);
            float[] d = YFx.impactDir();
            c.set("ImpactDir", d[0], d[1], d[2], d[3]);
            c.set("ImpactSeed", YFx.impactSeed());
        } else {
            c.set("Impact", 0f, 0f, 0.5f, 0.5f);
            c.set("ImpactDir", 1f, 0f, 0f, 0f);
            c.set("ImpactSeed", 0f);
        }
        float[] acc = YFx.accentColor();
        c.set("Accent", acc[0], acc[1], acc[2]);
        c.set("Punch", YFx.punchAmount());
        float[] sm = YFx.smearVec();
        c.set("Smear", sm[0], sm[1], sm[2], 0f);
        c.set("Blackout", YFx.blackout());
        tri();
    }

    private static void bind(int unit, int tex) {
        GlStateManager._activeTexture(GL30.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(tex);
    }

    private static void tri() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void restore(Minecraft mc) {
        GL30.glBindVertexArray(0);
        BufferUploader.reset();
        bind(2, 0);
        bind(1, 0);
        bind(0, 0);
        GlStateManager._glUseProgram(0);
        ShaderInstance any = GameRenderer.getPositionTexShader();
        if (any != null) any.clear();
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }
}
