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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen compositing: impact frames, the world-cutting split, speed lines,
 * letterbox, title cards and the "rules" brain-freeze. Driven by wall-clock ms.
 */
public final class ScreenFx {
    public static final int INVERT = 0, INK = 1, RED = 2, GOLD = 3;

    private record Impact(long start, long life, int style, float fx, float fy, long seed) {}
    private record Flash(long start, long life, int color) {}
    private record Split(long start, long life, float angle, float mag) {}

    public static final class Card {
        public static final int SLAM = 0, TITLE = 1, POP = 2, CORNER = 3, TIMER = 4;
        final String main, sub, sub2;
        final int style, color;
        final long start, life;
        final boolean rainbow;

        public Card(String main, String sub, String sub2, int style, int color, long life, boolean rainbow) {
            this.main = main; this.sub = sub; this.sub2 = sub2; this.style = style; this.color = color;
            this.start = Vfx.now(); this.life = life; this.rainbow = rainbow;
        }
    }

    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Card> CARDS = new ArrayList<>();
    private static Split split;
    private static long speedUntil;
    private static float speedIntensity;
    private static float letterTarget, letterNow;
    private static long rulesStart = -1, rulesLife;
    private static float vignetteTarget, vignetteNow;
    private static long lastFrame = Vfx.now();

    private static TextureTarget copy;

    // captured each frame in the world pass, used to aim impact frames at the hit
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;

    private static Vec3 camLeft = new Vec3(1, 0, 0);

    public static void captureMatrices(Matrix4f view, Matrix4f proj, Vec3 cam) {
        VIEW.set(view);
        PROJ.set(proj);
        camPos = cam;
    }

    public static void captureCamLeft(Vec3 left) {
        camLeft = left;
    }

    // ─────────── shader-driven cinematic layer ───────────

    public static final int S_POST = 0, S_INK = 1, S_NEG = 2, S_DUO = 3, S_BLEACH = 4;
    private record SFrame(long start, long dur, int mode, int tint, float fx, float fy, float seed) {}
    private static final List<SFrame> SFRAMES = new ArrayList<>();
    private static long postStart, postLife;
    private static float postPower, postFx = 0.5f, postFy = 0.5f;
    private static long ringStart = -1, ringLife;
    private static float ringFx = 0.5f, ringFy = 0.5f;
    private static long heatUntil;
    private static float heatPower;
    private static Vec3 lensWorld;
    private static double lensWorldR;
    private static long lensUntil;
    private static int gradeColor;
    private static long gradeStart, gradeIn, gradeHold, gradeOut;
    private static float gradeMax;

    public static boolean shaderReady() {
        return SpellShaders.IMPACT != null;
    }

    /** Queue a hand-timed sequence of shader impact frames (modes/durations), starting after a delay. */
    public static void frames(Vec3 focus, int tint, long delay, int[] modes, long[] durs) {
        float[] f = project(focus);
        long t = Vfx.now() + delay;
        for (int i = 0; i < modes.length; i++) {
            SFRAMES.add(new SFrame(t, durs[i], modes[i], tint, f[0], f[1], (float) Draw.hash(t + i)));
            t += durs[i];
        }
    }

    /** Post layer: aberration + zoom blur + grain, decaying over life. */
    public static void post(Vec3 focus, float power, long life) {
        float[] f = project(focus);
        postFx = f[0]; postFy = f[1];
        postStart = Vfx.now(); postLife = life; postPower = power;
    }

    /** A refraction shock ring expanding across the screen from a world point. */
    public static void ring(Vec3 focus, long life) {
        float[] f = project(focus);
        ringFx = f[0]; ringFy = f[1];
        ringStart = Vfx.now(); ringLife = life;
    }

    public static void heat(float power, long life) {
        heatPower = power;
        heatUntil = Vfx.now() + life;
    }

    /** Called every frame by a black hole that is on screen. */
    public static void lens(Vec3 world, double worldRadius) {
        lensWorld = world;
        lensWorldR = worldRadius;
        lensUntil = Vfx.now() + 80;
    }

    /** Colour grade over the whole frame (multiply), with fade in / hold / fade out. */
    public static void grade(int color, float strength, long in, long hold, long out) {
        gradeColor = color; gradeMax = strength;
        gradeStart = Vfx.now(); gradeIn = in; gradeHold = hold; gradeOut = out;
    }

    /** The signature hit: bleach → ink → negative → duotone → ink → negative, then aberration and a shock ring. */
    public static void cineHit(Vec3 focus, int tint, float power) {
        if (!shaderReady()) {
            impactLegacy(GOLD, 300, focus);
            return;
        }
        if (power >= 1f) {
            frames(focus, tint, 0, new int[]{S_BLEACH, S_INK, S_NEG, S_DUO, S_INK, S_NEG, S_DUO},
                    new long[]{45, 55, 50, 60, 45, 40, 50});
        } else {
            frames(focus, tint, 0, new int[]{S_INK, S_NEG, S_DUO}, new long[]{45, 40, 45});
        }
        post(focus, 0.6f + power, 700 + (long) (power * 500));
        ring(focus, 650 + (long) (power * 300));
    }

    /** World → normalised screen (unclamped), or null if behind the camera. */
    public static float[] projectRaw(Vec3 world) {
        Vector4f v = new Vector4f((float) (world.x - camPos.x), (float) (world.y - camPos.y), (float) (world.z - camPos.z), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        if (v.w <= 0.05f) return null;
        return new float[]{v.x / v.w * 0.5f + 0.5f, 1f - (v.y / v.w * 0.5f + 0.5f)};
    }

    /** World → normalised screen [0,1]. Falls back to centre if behind the camera. */
    public static float[] project(Vec3 world) {
        if (world == null) return new float[]{0.5f, 0.5f};
        Vector4f v = new Vector4f((float) (world.x - camPos.x), (float) (world.y - camPos.y), (float) (world.z - camPos.z), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        if (v.w <= 0.05f) return new float[]{0.5f, 0.5f};
        float x = v.x / v.w * 0.5f + 0.5f, y = 1f - (v.y / v.w * 0.5f + 0.5f);
        return new float[]{Mth.clamp(x, 0.05f, 0.95f), Mth.clamp(y, 0.05f, 0.95f)};
    }

    // ─────────── triggers ───────────

    public static void impact(int style, long life, Vec3 focus) {
        if (shaderReady()) {
            int tint = style == RED ? 0xFFFF2A3A : style == GOLD ? 0xFFFFC850 : 0xFFFFFFFF;
            long a = Math.max(30, life / 3), b = Math.max(30, life - 2 * a);
            switch (style) {
                case INVERT -> frames(focus, tint, 0, new int[]{S_BLEACH, S_NEG, S_INK}, new long[]{a, b, a});
                case INK -> frames(focus, tint, 0, new int[]{S_INK, S_NEG, S_INK}, new long[]{a, b, a});
                case RED, GOLD -> frames(focus, tint, 0, new int[]{S_DUO, S_NEG, S_DUO}, new long[]{a, b, a});
                default -> frames(focus, tint, 0, new int[]{S_INK}, new long[]{life});
            }
            post(focus, 0.5f, 500);
            return;
        }
        impactLegacy(style, life, focus);
    }

    private static void impactLegacy(int style, long life, Vec3 focus) {
        float[] f = project(focus);
        IMPACTS.add(new Impact(Vfx.now(), life, style, f[0], f[1], Vfx.now() * 31));
    }

    public static void flash(int color, long life) {
        FLASHES.add(new Flash(Vfx.now(), life, color));
    }

    public static void split(float angleDeg, long life, float mag) {
        split = new Split(Vfx.now(), life, angleDeg, mag);
    }

    public static void speed(long life, float intensity) {
        speedUntil = Math.max(speedUntil, Vfx.now() + life);
        speedIntensity = Math.max(intensity, Vfx.now() < speedUntil ? speedIntensity : 0);
    }

    public static void letterbox(float target) {
        letterTarget = target;
    }

    public static void vignette(float target) {
        vignetteTarget = target;
    }

    public static void card(Card c) {
        CARDS.add(c);
    }

    public static void rules(long life) {
        rulesStart = Vfx.now();
        rulesLife = life;
    }

    public static void clear() {
        IMPACTS.clear(); FLASHES.clear(); CARDS.clear(); split = null; speedUntil = 0;
        SFRAMES.clear(); postLife = 0; ringStart = -1; heatUntil = 0; lensUntil = 0; gradeMax = 0;
        letterTarget = letterNow = 0; vignetteTarget = vignetteNow = 0; rulesStart = -1;
    }

    // ─────────── raw GUI geometry ───────────

    private static BufferBuilder gb(VertexFormat.Mode mode) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void gend(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
    }

    private static void gv(BufferBuilder b, Matrix4f m, float x, float y, int c) {
        b.vertex(m, x, y, 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    private static void rect(Matrix4f m, float x0, float y0, float x1, float y1, int c) {
        BufferBuilder b = gb(VertexFormat.Mode.QUADS);
        gv(b, m, x0, y0, c); gv(b, m, x0, y1, c); gv(b, m, x1, y1, c); gv(b, m, x1, y0, c);
        gend(b);
    }

    private static void invertScreen(Matrix4f m, float w, float h) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
        rect(m, 0, 0, w, h, 0xFFFFFFFF);
        RenderSystem.defaultBlendFunc();
    }

    private static void multiply(Matrix4f m, float w, float h, int c) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        rect(m, 0, 0, w, h, c);
        RenderSystem.defaultBlendFunc();
    }

    /** Black frame with white manga burst lines exploding from the focal point. */
    private static void ink(Matrix4f m, float w, float h, float fx, float fy, long seed, int lineColor, int coreColor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        rect(m, 0, 0, w, h, 0xF2000000);
        float cx = fx * w, cy = fy * h, far = (float) Math.hypot(w, h);
        BufferBuilder b = gb(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < 64; i++) {
            float th = (float) (Draw.hash(seed + i) * Math.PI * 2);
            float half = (float) (0.004 + Draw.hash(seed + i * 3) * 0.022);
            float rin = (float) (h * (0.06 + Draw.hash(seed + i * 7) * 0.22));
            gv(b, m, cx + Mth.cos(th - half) * rin, cy + Mth.sin(th - half) * rin, lineColor);
            gv(b, m, cx + Mth.cos(th + half) * rin, cy + Mth.sin(th + half) * rin, lineColor);
            gv(b, m, cx + Mth.cos(th) * far, cy + Mth.sin(th) * far, lineColor);
        }
        float core = h * 0.07f;
        for (int i = 0; i < 24; i++) {
            float a0 = (float) (i * Math.PI * 2 / 24), a1 = (float) ((i + 1) * Math.PI * 2 / 24);
            float j = (float) (0.75 + Draw.hash(seed * 3 + i) * 0.6);
            gv(b, m, cx, cy, coreColor);
            gv(b, m, cx + Mth.cos(a0) * core * j, cy + Mth.sin(a0) * core * j, coreColor);
            gv(b, m, cx + Mth.cos(a1) * core * j, cy + Mth.sin(a1) * core * j, coreColor);
        }
        gend(b);
    }

    private static void speedLines(Matrix4f m, float w, float h, float intensity, long frame) {
        float cx = w / 2, cy = h / 2, far = (float) Math.hypot(w, h);
        BufferBuilder b = gb(VertexFormat.Mode.TRIANGLES);
        int col = Draw.alpha(0xFFFFFFFF, 0.55f * intensity);
        for (int i = 0; i < 70; i++) {
            float th = (float) (Draw.hash(frame * 977 + i) * Math.PI * 2);
            float half = (float) (0.002 + Draw.hash(frame * 13 + i) * 0.006);
            float rin = (float) (far * (0.32 + Draw.hash(frame * 7 + i) * 0.18));
            gv(b, m, cx + Mth.cos(th - half) * far, cy + Mth.sin(th - half) * far, col);
            gv(b, m, cx + Mth.cos(th + half) * far, cy + Mth.sin(th + half) * far, col);
            gv(b, m, cx + Mth.cos(th) * rin, cy + Mth.sin(th) * rin, Draw.alpha(col, 0));
        }
        gend(b);
    }

    private static void vignetteDraw(Matrix4f m, float w, float h, float a) {
        int dark = Draw.alpha(0xFF000000, a), clear = 0x00000000;
        float bw = w * 0.22f, bh = h * 0.25f;
        BufferBuilder b = gb(VertexFormat.Mode.QUADS);
        gv(b, m, 0, 0, dark); gv(b, m, 0, h, dark); gv(b, m, bw, h, clear); gv(b, m, bw, 0, clear);
        gv(b, m, w - bw, 0, clear); gv(b, m, w - bw, h, clear); gv(b, m, w, h, dark); gv(b, m, w, 0, dark);
        gv(b, m, 0, 0, dark); gv(b, m, 0, bh, clear); gv(b, m, w, bh, clear); gv(b, m, w, 0, dark);
        gv(b, m, 0, h - bh, clear); gv(b, m, 0, h, dark); gv(b, m, w, h, dark); gv(b, m, w, h - bh, clear);
        gend(b);
    }

    // ─────────── the world cut in half ───────────

    private static void grabFrame() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (copy == null || copy.width != main.width || copy.height != main.height) {
            if (copy != null) copy.destroyBuffers();
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
    }

    /** Clip the screen rectangle against the half-plane (p - c)·n >= 0. */
    private static List<float[]> clipHalf(float w, float h, float cx, float cy, float nx, float ny, float sign) {
        float[][] poly = {{0, 0}, {w, 0}, {w, h}, {0, h}};
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < poly.length; i++) {
            float[] a = poly[i], b = poly[(i + 1) % poly.length];
            float da = sign * ((a[0] - cx) * nx + (a[1] - cy) * ny);
            float db = sign * ((b[0] - cx) * nx + (b[1] - cy) * ny);
            if (da >= 0) out.add(a);
            if ((da >= 0) != (db >= 0)) {
                float t = da / (da - db);
                out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
        }
        return out;
    }

    private static void drawHalf(Matrix4f m, List<float[]> poly, float w, float h, float ox, float oy) {
        if (poly.size() < 3) return;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        float[] p0 = poly.get(0);
        for (int i = 1; i < poly.size() - 1; i++) {
            float[][] tri = {p0, poly.get(i), poly.get(i + 1)};
            for (float[] p : tri) b.vertex(m, p[0] + ox, p[1] + oy, 0).uv(p[0] / w, 1f - p[1] / h).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
    }

    private static void fillPoly(Matrix4f m, List<float[]> poly, float ox, float oy, int c) {
        if (poly.size() < 3) return;
        BufferBuilder b = gb(VertexFormat.Mode.TRIANGLES);
        float[] p0 = poly.get(0);
        for (int i = 1; i < poly.size() - 1; i++) {
            gv(b, m, p0[0] + ox, p0[1] + oy, c);
            gv(b, m, poly.get(i)[0] + ox, poly.get(i)[1] + oy, c);
            gv(b, m, poly.get(i + 1)[0] + ox, poly.get(i + 1)[1] + oy, c);
        }
        gend(b);
    }

    /** Runs before the HUD: slices the rendered world along a diagonal and shoves the halves apart. */
    public static void preGui(GuiGraphics g) {
        splitPass(g);
        shaderPass(g);
    }

    private static void shaderPass(GuiGraphics g) {
        ShaderInstance sh = SpellShaders.IMPACT;
        if (sh == null) return;
        long t = Vfx.now();
        SFRAMES.removeIf(f -> t > f.start + f.dur);
        SFrame frame = null;
        for (SFrame f : SFRAMES) if (t >= f.start) { frame = f; break; }
        float post = 0f;
        if (t - postStart < postLife) {
            float p = (t - postStart) / (float) postLife;
            post = postPower * (1 - p) * (1 - p);
        }
        float ringR = -1f;
        if (ringStart >= 0 && t - ringStart < ringLife) ringR = 0.02f + 1.5f * Ease.outCubic((t - ringStart) / (float) ringLife);
        float heat = t < heatUntil ? heatPower : 0f;
        float w = g.guiWidth(), h = g.guiHeight();
        float aspect = w / Math.max(1f, h);
        float lensR = 0f, lx = 0.5f, ly = 0.5f;
        if (t < lensUntil && lensWorld != null) {
            float[] c = project(lensWorld);
            float[] edge = project(lensWorld.add(camLeft.scale(lensWorldR)));
            Vector4f chk = new Vector4f((float) (lensWorld.x - camPos.x), (float) (lensWorld.y - camPos.y), (float) (lensWorld.z - camPos.z), 1f);
            VIEW.transform(chk);
            PROJ.transform(chk);
            if (chk.w > 0.1f) {
                lx = c[0]; ly = c[1];
                lensR = (float) Math.hypot((edge[0] - c[0]) * aspect, edge[1] - c[1]);
                lensR = Math.min(lensR, 0.35f);
            }
        }
        if (frame == null && post < 0.01f && ringR < 0 && heat <= 0 && lensR <= 0) return;

        grabFrame();
        float fx = frame != null ? frame.fx : (ringR > 0 ? ringFx : postFx);
        float fy = frame != null ? frame.fy : (ringR > 0 ? ringFy : postFy);
        int tint = frame != null ? frame.tint : 0xFFFFFFFF;
        sh.safeGetUniform("Time").set((t % 100000) / 1000f);
        sh.safeGetUniform("Mode").set(frame != null ? (float) frame.mode : 0f);
        sh.safeGetUniform("Focus").set(fx, 1f - fy);
        sh.safeGetUniform("Strength").set(frame != null ? 1f : Math.max(post, ringR > 0 ? 0.5f : 0f));
        sh.safeGetUniform("Radius").set(ringR);
        sh.safeGetUniform("Tint").set(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
        sh.safeGetUniform("Seed").set(frame != null ? frame.seed : 0f);
        sh.safeGetUniform("Aspect").set(aspect);
        sh.safeGetUniform("LensPos").set(lx, 1f - ly);
        sh.safeGetUniform("LensR").set(lensR);
        sh.safeGetUniform("Heat").set(heat);

        Matrix4f m = g.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, 0, 0, 0).uv(0, 1).endVertex();
        b.vertex(m, 0, h, 0).uv(0, 0).endVertex();
        b.vertex(m, w, h, 0).uv(1, 0).endVertex();
        b.vertex(m, w, 0, 0).uv(1, 1).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void splitPass(GuiGraphics g) {
        Split s = split;
        if (s == null) return;
        long el = Vfx.now() - s.start;
        if (el > s.life) {
            split = null;
            return;
        }
        float w = g.guiWidth(), h = g.guiHeight();
        Matrix4f m = g.pose().last().pose();
        float k = el < 110 ? Ease.outExpo(el / 110f) : el > s.life - 160 ? Math.max(0, (s.life - el) / 160f) : 1f;
        float off = s.mag * k;
        float rad = s.angle * Mth.DEG_TO_RAD;
        float tx = Mth.cos(rad), ty = Mth.sin(rad), nx = -ty, ny = tx;
        float cx = w / 2, cy = h / 2;

        grabFrame();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        rect(m, 0, 0, w, h, 0xFF000000);
        List<float[]> a = clipHalf(w, h, cx, cy, nx, ny, 1);
        List<float[]> b = clipHalf(w, h, cx, cy, nx, ny, -1);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        drawHalf(m, a, w, h, nx * off + tx * off * 0.7f, ny * off + ty * off * 0.7f);
        RenderSystem.setShaderColor(1f, 0.82f, 0.88f, 1);
        drawHalf(m, b, w, h, -nx * off - tx * off * 0.7f, -ny * off - ty * off * 0.7f);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // stutter: invert one half on alternating frames like a hand-drawn impact
        if ((el / 55) % 2 == 0 && el < s.life * 0.6) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
            fillPoly(m, b, -nx * off - tx * off * 0.7f, -ny * off - ty * off * 0.7f, 0xFFFFFFFF);
            RenderSystem.defaultBlendFunc();
        }
        // blazing seam
        Draw.additive();
        float L = (float) Math.hypot(w, h);
        BufferBuilder bb = gb(VertexFormat.Mode.QUADS);
        float[] widths = {Math.max(2, off * 0.25f), Math.max(6, off * 0.9f)};
        int[] cols = {0xFFFFFFFF, Draw.alpha(0xFFFF4FB8, 0.55f)};
        for (int i = 0; i < 2; i++) {
            float ww = widths[i];
            gv(bb, m, cx - tx * L + nx * ww, cy - ty * L + ny * ww, cols[i]);
            gv(bb, m, cx + tx * L + nx * ww, cy + ty * L + ny * ww, cols[i]);
            gv(bb, m, cx + tx * L - nx * ww, cy + ty * L - ny * ww, cols[i]);
            gv(bb, m, cx - tx * L - nx * ww, cy - ty * L - ny * ww, cols[i]);
        }
        gend(bb);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    // ─────────── main overlay ───────────

    public static void postGui(GuiGraphics g, float partial) {
        long t = Vfx.now();
        float dt = Math.min(0.1f, (t - lastFrame) / 1000f);
        lastFrame = t;
        letterNow += (letterTarget - letterNow) * Math.min(1f, dt * 7f);
        vignetteNow += (vignetteTarget - vignetteNow) * Math.min(1f, dt * 5f);

        float w = g.guiWidth(), h = g.guiHeight();
        Matrix4f m = g.pose().last().pose();
        Font font = Minecraft.getInstance().font;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (vignetteNow > 0.01f) vignetteDraw(m, w, h, vignetteNow);

        if (t < speedUntil) speedLines(m, w, h, speedIntensity, t / 45);

        drawRules(g, m, w, h, t, font);

        IMPACTS.removeIf(i -> t - i.start > i.life);
        for (Impact i : IMPACTS) {
            long el = t - i.start;
            int frame = (int) (el / 42);
            switch (i.style) {
                case INVERT -> {
                    if (frame % 3 != 2) invertScreen(m, w, h);
                    else ink(m, w, h, i.fx, i.fy, i.seed + frame, 0xFFFFFFFF, 0xFFFFFFFF);
                }
                case INK -> {
                    if (frame % 2 == 0) ink(m, w, h, i.fx, i.fy, i.seed + frame, 0xFFFFFFFF, 0xFFFFFFFF);
                    else invertScreen(m, w, h);
                }
                case RED -> {
                    invertScreen(m, w, h);
                    multiply(m, w, h, frame % 2 == 0 ? 0xFFFF2030 : 0xFF300008);
                }
                case GOLD -> {
                    if (frame % 2 == 0) {
                        invertScreen(m, w, h);
                        multiply(m, w, h, 0xFFFFD34D);
                    } else {
                        ink(m, w, h, i.fx, i.fy, i.seed + frame, 0xFFFFD34D, 0xFFFFFFFF);
                    }
                }
                default -> {}
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        FLASHES.removeIf(f -> t - f.start > f.life);
        for (Flash f : FLASHES) {
            float p = (t - f.start) / (float) f.life;
            rect(m, 0, 0, w, h, Draw.alpha(f.color, (1 - p) * (1 - p)));
        }

        if (gradeMax > 0) {
            long ge = t - gradeStart;
            float ga = ge < gradeIn ? ge / (float) Math.max(1, gradeIn) : ge < gradeIn + gradeHold ? 1f : 1f - (ge - gradeIn - gradeHold) / (float) Math.max(1, gradeOut);
            if (ga > 0) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
                rect(m, 0, 0, w, h, Draw.lerp(0xFFFFFFFF, gradeColor, gradeMax * Math.min(1f, ga)));
                RenderSystem.defaultBlendFunc();
            } else gradeMax = 0;
        }

        if (letterNow > 0.005f) {
            float bh = h * 0.115f * letterNow;
            rect(m, 0, 0, w, bh, 0xFF000000);
            rect(m, 0, h - bh, w, h, 0xFF000000);
        }

        CARDS.removeIf(c -> t - c.start > c.life);
        for (Card c : CARDS) drawCard(g, c, t, w, h, font);

        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
    }

    // ─────────── text ───────────

    static void bigText(GuiGraphics g, Font font, String s, float cx, float cy, float scale, int color, boolean chroma, float alpha) {
        if (s == null || s.isEmpty() || alpha <= 0.02f) return;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        int x = -font.width(s) / 2;
        if (chroma) {
            g.drawString(font, s, x - 1, -4, Draw.alpha(0xFFFF2050, alpha * 0.85f), false);
            g.drawString(font, s, x + 1, -4, Draw.alpha(0xFF20E0FF, alpha * 0.85f), false);
        }
        g.drawString(font, s, x, -4, Draw.alpha(color, alpha), false);
        g.pose().popPose();
    }

    private static void drawCard(GuiGraphics g, Card c, long t, float w, float h, Font font) {
        long el = t - c.start;
        float alpha = el > c.life - 250 ? Math.max(0, (c.life - el) / 250f) : 1f;
        int col = c.rainbow ? Draw.hsv(t / 600f, 0.75f, 1f) : c.color;
        long jit = t / 35;
        float jx = (float) (Draw.hash(jit) - 0.5) * (el < 300 ? 4 : 0.6f);
        float jy = (float) (Draw.hash(jit + 99) - 0.5) * (el < 300 ? 4 : 0.6f);
        switch (c.style) {
            case Card.SLAM -> {
                float s = el < 140 ? Mth.lerp(Ease.outExpo(el / 140f), 18f, 5.5f) : 5.5f + (el - 140) * 0.0004f;
                bigText(g, font, c.main, w / 2 + jx, h * 0.42f + jy, s, col, true, alpha);
                if (el > 180) bigText(g, font, c.sub, w / 2, h * 0.42f + 34, 1.6f, 0xFFFFFFFF, false, alpha * Math.min(1, (el - 180) / 200f));
            }
            case Card.TITLE -> {
                float s = 6.5f + el * 0.00035f;
                bigText(g, font, c.main, w / 2 + jx, h * 0.38f + jy, s, col, true, alpha);
                float a2 = alpha * Math.min(1, el / 300f);
                bigText(g, font, c.sub, w / 2, h * 0.38f + 40, 2.6f, 0xFFFFFFFF, true, a2);
                bigText(g, font, c.sub2, w / 2, h * 0.38f + 62, 1.3f, 0xFFFFC0E0, false, alpha * Math.min(1, Math.max(0, (el - 250) / 300f)));
            }
            case Card.POP -> {
                float s = el < 120 ? Mth.lerp(Ease.outBack(el / 120f), 0.5f, 2.2f) : 2.2f;
                bigText(g, font, c.main, w / 2, h * 0.72f, s, col, true, alpha);
                bigText(g, font, c.sub, w / 2, h * 0.72f + 20, 1.1f, 0xFFFFFFFF, false, alpha);
            }
            case Card.CORNER -> {
                float slide = Ease.outExpo(Math.min(1, el / 400f));
                g.pose().pushPose();
                g.pose().translate(Mth.lerp(slide, -200, 18), h * 0.70f, 0);
                g.pose().scale(2.4f, 2.4f, 1);
                g.drawString(font, c.main, 0, 0, Draw.alpha(col, alpha), false);
                g.pose().popPose();
                g.pose().pushPose();
                g.pose().translate(Mth.lerp(slide, -200, 20), h * 0.70f + 26, 0);
                g.pose().scale(1.1f, 1.1f, 1);
                g.drawString(font, c.sub, 0, 0, Draw.alpha(0xFFFFFFFF, alpha), false);
                g.pose().popPose();
            }
            case Card.TIMER -> {
                float pulse = 1f + 0.06f * Mth.sin(el / 80f);
                float s = (el < 160 ? Mth.lerp(Ease.outExpo(el / 160f), 22f, 9f) : 9f) * pulse;
                bigText(g, font, c.main, w / 2 + jx, h * 0.45f + jy, s, col, true, alpha);
                bigText(g, font, c.sub, w / 2, h * 0.45f + 52, 1.4f, 0xFFFFFFFF, false, alpha * Math.min(1, el / 400f));
                bigText(g, font, c.sub2, w / 2, h * 0.45f + 68, 1.2f, 0xFFFFE08A, false, alpha * Math.min(1, Math.max(0, (el - 400) / 300f)));
            }
            default -> {}
        }
    }

    private static final String[] RULES = {
            "BINDING VOW - Neither of you may strike until the question is answered.",
            "The barrier will not break until you speak.",
            "Answer carefully. She already has the scissors out.",
    };

    private static void drawRules(GuiGraphics g, Matrix4f m, float w, float h, long t, Font font) {
        if (rulesStart < 0) return;
        long el = t - rulesStart;
        if (el > rulesLife) {
            rulesStart = -1;
            return;
        }
        float a = el > rulesLife - 300 ? (rulesLife - el) / 300f : Math.min(1, el / 150f);
        rect(m, 0, 0, w, h, Draw.alpha(0xFF1A0006, 0.72f * a));
        long frame = t / 55;
        BufferBuilder b = gb(VertexFormat.Mode.QUADS);
        for (int i = 0; i < 420; i++) {
            float x = (float) (Draw.hash(frame * 1000 + i) * w), y = (float) (Draw.hash(frame * 2000 + i) * h);
            float s = 1 + (float) Draw.hash(frame * 3000 + i) * 2.5f;
            int c = Draw.alpha(Draw.hash(i + frame) > 0.5 ? 0xFFFFFFFF : 0xFFFF3D7A, 0.35f * a);
            gv(b, m, x, y, c); gv(b, m, x, y + s, c); gv(b, m, x + s * 3, y + s, c); gv(b, m, x + s * 3, y, c);
        }
        gend(b);
        float glitch = (frame % 7 == 0) ? 3 : 0;
        bigText(g, font, "SIMPLE DOMAIN - BINDING VOW", w / 2 + glitch, h * 0.2f, 1.6f, 0xFFFF4FB8, true, a);
        int chars = (int) (el / 14);
        float y = h * 0.32f;
        for (String line : RULES) {
            int n = Math.min(line.length(), Math.max(0, chars));
            chars -= line.length();
            if (n <= 0) break;
            g.pose().pushPose();
            g.pose().translate(w * 0.12f, y, 0);
            g.pose().scale(1.25f, 1.25f, 1);
            g.drawString(font, line.substring(0, n), 0, 0, Draw.alpha(0xFFFFFFFF, a), false);
            g.pose().popPose();
            y += 17;
        }
    }

    private ScreenFx() {}
}
