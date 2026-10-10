package com.hakari.idg.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class ScreenFx {
   public static final int INVERT = 0;
   public static final int INK = 1;
   public static final int RED = 2;
   public static final int GOLD = 3;
   private static final List<ScreenFx.Impact> IMPACTS = new ArrayList<>();
   private static final List<ScreenFx.Flash> FLASHES = new ArrayList<>();
   private static final List<ScreenFx.Card> CARDS = new ArrayList<>();
   private static ScreenFx.Split split;
   private static long speedUntil;
   private static float speedIntensity;
   private static float letterTarget;
   private static float letterNow;
   private static long rulesStart = -1L;
   private static long rulesLife;
   private static float vignetteTarget;
   private static float vignetteNow;
   private static long lastFrame = Vfx.now();
   private static TextureTarget copy;
   private static final Matrix4f VIEW = new Matrix4f();
   private static final Matrix4f PROJ = new Matrix4f();
   private static Vec3 camPos = Vec3.ZERO;
   private static final String[] RULES = new String[]{
      "RULE 1 - Every visual effect he makes spins the reels.",
      "RULE 2 - Two visuals trigger a RIICHI: a scenario that can end in a JACKPOT.",
      "RULE 3 - Matching numbers pay out. Odd numbers raise the odds of the next one.",
      "RULE 4 - A JACKPOT grants 4 minutes 11 seconds of limitless cursed energy.",
      "RULE 5 - For that time, his body repairs itself automatically."
   };

   public static void captureMatrices(Matrix4f view, Matrix4f proj, Vec3 cam) {
      VIEW.set(view);
      PROJ.set(proj);
      camPos = cam;
   }

   public static float[] project(Vec3 world) {
      if (world == null) {
         return new float[]{0.5F, 0.5F};
      } else {
         Vector4f v = new Vector4f((float)(world.x - camPos.x), (float)(world.y - camPos.y), (float)(world.z - camPos.z), 1.0F);
         VIEW.transform(v);
         PROJ.transform(v);
         if (v.w <= 0.05F) {
            return new float[]{0.5F, 0.5F};
         } else {
            float x = v.x / v.w * 0.5F + 0.5F;
            float y = 1.0F - (v.y / v.w * 0.5F + 0.5F);
            return new float[]{Mth.clamp(x, 0.05F, 0.95F), Mth.clamp(y, 0.05F, 0.95F)};
         }
      }
   }

   public static void impact(int style, long life, Vec3 focus) {
      float[] f = project(focus);
      IMPACTS.add(new ScreenFx.Impact(Vfx.now(), life, style, f[0], f[1], Vfx.now() * 31L));
   }

   public static void flash(int color, long life) {
      FLASHES.add(new ScreenFx.Flash(Vfx.now(), life, color));
   }

   public static void split(float angleDeg, long life, float mag) {
      split = new ScreenFx.Split(Vfx.now(), life, angleDeg, mag);
   }

   public static void speed(long life, float intensity) {
      speedUntil = Math.max(speedUntil, Vfx.now() + life);
      speedIntensity = Math.max(intensity, Vfx.now() < speedUntil ? speedIntensity : 0.0F);
   }

   public static void letterbox(float target) {
      letterTarget = target;
   }

   public static void vignette(float target) {
      vignetteTarget = target;
   }

   public static void card(ScreenFx.Card c) {
      CARDS.add(c);
   }

   public static void rules(long life) {
      rulesStart = Vfx.now();
      rulesLife = life;
   }

   public static void clear() {
      IMPACTS.clear();
      FLASHES.clear();
      CARDS.clear();
      split = null;
      speedUntil = 0L;
      letterNow = 0.0F;
      letterTarget = 0.0F;
      vignetteNow = 0.0F;
      vignetteTarget = 0.0F;
      rulesStart = -1L;
   }

   private static BufferBuilder gb(Mode mode) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
      return b;
   }

   private static void gend(BufferBuilder b) {
      BufferUploader.drawWithShader(b.end());
   }

   private static void gv(BufferBuilder b, Matrix4f m, float x, float y, int c) {
      b.vertex(m, x, y, 0.0F).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24 & 0xFF).endVertex();
   }

   private static void rect(Matrix4f m, float x0, float y0, float x1, float y1, int c) {
      BufferBuilder b = gb(Mode.QUADS);
      gv(b, m, x0, y0, c);
      gv(b, m, x0, y1, c);
      gv(b, m, x1, y1, c);
      gv(b, m, x1, y0, c);
      gend(b);
   }

   private static void invertScreen(Matrix4f m, float w, float h) {
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO);
      rect(m, 0.0F, 0.0F, w, h, -1);
      RenderSystem.defaultBlendFunc();
   }

   private static void multiply(Matrix4f m, float w, float h, int c) {
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.DST_COLOR, DestFactor.ZERO);
      rect(m, 0.0F, 0.0F, w, h, c);
      RenderSystem.defaultBlendFunc();
   }

   private static void ink(Matrix4f m, float w, float h, float fx, float fy, long seed, int lineColor, int coreColor) {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      rect(m, 0.0F, 0.0F, w, h, -234881024);
      float cx = fx * w;
      float cy = fy * h;
      float far = (float)Math.hypot((double)w, (double)h);
      BufferBuilder b = gb(Mode.TRIANGLES);

      for (int i = 0; i < 64; i++) {
         float th = (float)(Draw.hash(seed + (long)i) * Math.PI * 2.0);
         float half = (float)(0.004 + Draw.hash(seed + (long)(i * 3)) * 0.022);
         float rin = (float)((double)h * (0.06 + Draw.hash(seed + (long)(i * 7)) * 0.22));
         gv(b, m, cx + Mth.cos(th - half) * rin, cy + Mth.sin(th - half) * rin, lineColor);
         gv(b, m, cx + Mth.cos(th + half) * rin, cy + Mth.sin(th + half) * rin, lineColor);
         gv(b, m, cx + Mth.cos(th) * far, cy + Mth.sin(th) * far, lineColor);
      }

      float core = h * 0.07F;

      for (int i = 0; i < 24; i++) {
         float a0 = (float)((double)i * Math.PI * 2.0 / 24.0);
         float a1 = (float)((double)(i + 1) * Math.PI * 2.0 / 24.0);
         float j = (float)(0.75 + Draw.hash(seed * 3L + (long)i) * 0.6);
         gv(b, m, cx, cy, coreColor);
         gv(b, m, cx + Mth.cos(a0) * core * j, cy + Mth.sin(a0) * core * j, coreColor);
         gv(b, m, cx + Mth.cos(a1) * core * j, cy + Mth.sin(a1) * core * j, coreColor);
      }

      gend(b);
   }

   private static void speedLines(Matrix4f m, float w, float h, float intensity, long frame) {
      float cx = w / 2.0F;
      float cy = h / 2.0F;
      float far = (float)Math.hypot((double)w, (double)h);
      BufferBuilder b = gb(Mode.TRIANGLES);
      int col = Draw.alpha(-1, 0.55F * intensity);

      for (int i = 0; i < 70; i++) {
         float th = (float)(Draw.hash(frame * 977L + (long)i) * Math.PI * 2.0);
         float half = (float)(0.002 + Draw.hash(frame * 13L + (long)i) * 0.006);
         float rin = (float)((double)far * (0.32 + Draw.hash(frame * 7L + (long)i) * 0.18));
         gv(b, m, cx + Mth.cos(th - half) * far, cy + Mth.sin(th - half) * far, col);
         gv(b, m, cx + Mth.cos(th + half) * far, cy + Mth.sin(th + half) * far, col);
         gv(b, m, cx + Mth.cos(th) * rin, cy + Mth.sin(th) * rin, Draw.alpha(col, 0.0F));
      }

      gend(b);
   }

   private static void vignetteDraw(Matrix4f m, float w, float h, float a) {
      int dark = Draw.alpha(-16777216, a);
      int clear = 0;
      float bw = w * 0.22F;
      float bh = h * 0.25F;
      BufferBuilder b = gb(Mode.QUADS);
      gv(b, m, 0.0F, 0.0F, dark);
      gv(b, m, 0.0F, h, dark);
      gv(b, m, bw, h, clear);
      gv(b, m, bw, 0.0F, clear);
      gv(b, m, w - bw, 0.0F, clear);
      gv(b, m, w - bw, h, clear);
      gv(b, m, w, h, dark);
      gv(b, m, w, 0.0F, dark);
      gv(b, m, 0.0F, 0.0F, dark);
      gv(b, m, 0.0F, bh, clear);
      gv(b, m, w, bh, clear);
      gv(b, m, w, 0.0F, dark);
      gv(b, m, 0.0F, h - bh, clear);
      gv(b, m, 0.0F, h, dark);
      gv(b, m, w, h, dark);
      gv(b, m, w, h - bh, clear);
      gend(b);
   }

   private static void grabFrame() {
      RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
      if (copy == null || copy.width != main.width || copy.height != main.height) {
         if (copy != null) {
            copy.destroyBuffers();
         }

         copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
      }

      GlStateManager._glBindFramebuffer(36008, main.frameBufferId);
      GlStateManager._glBindFramebuffer(36009, copy.frameBufferId);
      GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, 16384, 9728);
      main.bindWrite(false);
   }

   private static List<float[]> clipHalf(float w, float h, float cx, float cy, float nx, float ny, float sign) {
      float[][] poly = new float[][]{{0.0F, 0.0F}, {w, 0.0F}, {w, h}, {0.0F, h}};
      List<float[]> out = new ArrayList<>();

      for (int i = 0; i < poly.length; i++) {
         float[] a = poly[i];
         float[] b = poly[(i + 1) % poly.length];
         float da = sign * ((a[0] - cx) * nx + (a[1] - cy) * ny);
         float db = sign * ((b[0] - cx) * nx + (b[1] - cy) * ny);
         if (da >= 0.0F) {
            out.add(a);
         }

         if (da >= 0.0F != db >= 0.0F) {
            float t = da / (da - db);
            out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
         }
      }

      return out;
   }

   private static void drawHalf(Matrix4f m, List<float[]> poly, float w, float h, float ox, float oy) {
      if (poly.size() >= 3) {
         RenderSystem.setShader(GameRenderer::getPositionTexShader);
         RenderSystem.setShaderTexture(0, copy.getColorTextureId());
         BufferBuilder b = Tesselator.getInstance().getBuilder();
         b.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
         float[] p0 = poly.get(0);

         for (int i = 1; i < poly.size() - 1; i++) {
            float[][] tri = new float[][]{p0, poly.get(i), poly.get(i + 1)};

            for (float[] p : tri) {
               b.vertex(m, p[0] + ox, p[1] + oy, 0.0F).uv(p[0] / w, 1.0F - p[1] / h).endVertex();
            }
         }

         BufferUploader.drawWithShader(b.end());
      }
   }

   private static void fillPoly(Matrix4f m, List<float[]> poly, float ox, float oy, int c) {
      if (poly.size() >= 3) {
         BufferBuilder b = gb(Mode.TRIANGLES);
         float[] p0 = poly.get(0);

         for (int i = 1; i < poly.size() - 1; i++) {
            gv(b, m, p0[0] + ox, p0[1] + oy, c);
            gv(b, m, poly.get(i)[0] + ox, poly.get(i)[1] + oy, c);
            gv(b, m, poly.get(i + 1)[0] + ox, poly.get(i + 1)[1] + oy, c);
         }

         gend(b);
      }
   }

   public static void preGui(GuiGraphics g) {
      ScreenFx.Split s = split;
      if (s != null) {
         long el = Vfx.now() - s.start;
         if (el > s.life) {
            split = null;
         } else {
            float w = (float)g.guiWidth();
            float h = (float)g.guiHeight();
            Matrix4f m = g.pose().last().pose();
            float k = el < 110L ? Ease.outExpo((float)el / 110.0F) : (el > s.life - 160L ? Math.max(0.0F, (float)(s.life - el) / 160.0F) : 1.0F);
            float off = s.mag * k;
            float rad = s.angle * (float) (Math.PI / 180.0);
            float tx = Mth.cos(rad);
            float ty = Mth.sin(rad);
            float nx = -ty;
            float ny = tx;
            float cx = w / 2.0F;
            float cy = h / 2.0F;
            grabFrame();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            rect(m, 0.0F, 0.0F, w, h, -16777216);
            List<float[]> a = clipHalf(w, h, cx, cy, nx, tx, 1.0F);
            List<float[]> b = clipHalf(w, h, cx, cy, nx, tx, -1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            drawHalf(m, a, w, h, nx * off + tx * off * 0.7F, tx * off + ty * off * 0.7F);
            RenderSystem.setShaderColor(1.0F, 0.82F, 0.88F, 1.0F);
            drawHalf(m, b, w, h, -nx * off - tx * off * 0.7F, -tx * off - ty * off * 0.7F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (el / 55L % 2L == 0L && (double)el < (double)s.life * 0.6) {
               RenderSystem.blendFunc(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO);
               fillPoly(m, b, -nx * off - tx * off * 0.7F, -tx * off - ty * off * 0.7F, -1);
               RenderSystem.defaultBlendFunc();
            }

            Draw.additive();
            float L = (float)Math.hypot((double)w, (double)h);
            BufferBuilder bb = gb(Mode.QUADS);
            float[] widths = new float[]{Math.max(2.0F, off * 0.25F), Math.max(6.0F, off * 0.9F)};
            int[] cols = new int[]{-1, Draw.alpha(-45128, 0.55F)};

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
      }
   }

   public static void postGui(GuiGraphics g, float partial) {
      long t = Vfx.now();
      float dt = Math.min(0.1F, (float)(t - lastFrame) / 1000.0F);
      lastFrame = t;
      letterNow = letterNow + (letterTarget - letterNow) * Math.min(1.0F, dt * 7.0F);
      vignetteNow = vignetteNow + (vignetteTarget - vignetteNow) * Math.min(1.0F, dt * 5.0F);
      float w = (float)g.guiWidth();
      float h = (float)g.guiHeight();
      Matrix4f m = g.pose().last().pose();
      Font font = Minecraft.getInstance().font;
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      if (vignetteNow > 0.01F) {
         vignetteDraw(m, w, h, vignetteNow);
      }

      if (t < speedUntil) {
         speedLines(m, w, h, speedIntensity, t / 45L);
      }

      drawRules(g, m, w, h, t, font);
      IMPACTS.removeIf(i -> t - i.start > i.life);

      for (ScreenFx.Impact i : IMPACTS) {
         long el = t - i.start;
         int frame = (int)(el / 42L);
         switch (i.style) {
            case 0:
               if (frame % 3 != 2) {
                  invertScreen(m, w, h);
               } else {
                  ink(m, w, h, i.fx, i.fy, i.seed + (long)frame, -1, -1);
               }
               break;
            case 1:
               if (frame % 2 == 0) {
                  ink(m, w, h, i.fx, i.fy, i.seed + (long)frame, -1, -1);
               } else {
                  invertScreen(m, w, h);
               }
               break;
            case 2:
               invertScreen(m, w, h);
               multiply(m, w, h, frame % 2 == 0 ? -57296 : -13631480);
               break;
            case 3:
               if (frame % 2 == 0) {
                  invertScreen(m, w, h);
                  multiply(m, w, h, -11443);
               } else {
                  ink(m, w, h, i.fx, i.fy, i.seed + (long)frame, -11443, -1);
               }
         }
      }

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      FLASHES.removeIf(f -> t - f.start > f.life);

      for (ScreenFx.Flash f : FLASHES) {
         float p = (float)(t - f.start) / (float)f.life;
         rect(m, 0.0F, 0.0F, w, h, Draw.alpha(f.color, (1.0F - p) * (1.0F - p)));
      }

      if (letterNow > 0.005F) {
         float bh = h * 0.115F * letterNow;
         rect(m, 0.0F, 0.0F, w, bh, -16777216);
         rect(m, 0.0F, h - bh, w, h, -16777216);
      }

      CARDS.removeIf(cx -> t - cx.start > cx.life);

      for (ScreenFx.Card c : CARDS) {
         drawCard(g, c, t, w, h, font);
      }

      RenderSystem.enableDepthTest();
      RenderSystem.defaultBlendFunc();
   }

   static void bigText(GuiGraphics g, Font font, String s, float cx, float cy, float scale, int color, boolean chroma, float alpha) {
      if (s != null && !s.isEmpty() && !(alpha <= 0.02F)) {
         g.pose().pushPose();
         g.pose().translate(cx, cy, 0.0F);
         g.pose().scale(scale, scale, 1.0F);
         int x = -font.width(s) / 2;
         if (chroma) {
            g.drawString(font, s, x - 1, -4, Draw.alpha(-57264, alpha * 0.85F), false);
            g.drawString(font, s, x + 1, -4, Draw.alpha(-14622465, alpha * 0.85F), false);
         }

         g.drawString(font, s, x, -4, Draw.alpha(color, alpha), false);
         g.pose().popPose();
      }
   }

   private static void drawCard(GuiGraphics g, ScreenFx.Card c, long t, float w, float h, Font font) {
      long el = t - c.start;
      float alpha = el > c.life - 250L ? Math.max(0.0F, (float)(c.life - el) / 250.0F) : 1.0F;
      int col = c.rainbow ? Draw.hsv((float)t / 600.0F, 0.75F, 1.0F) : c.color;
      long jit = t / 35L;
      float jx = (float)(Draw.hash(jit) - 0.5) * (el < 300L ? 4.0F : 0.6F);
      float jy = (float)(Draw.hash(jit + 99L) - 0.5) * (el < 300L ? 4.0F : 0.6F);
      switch (c.style) {
         case 0: {
            float s = el < 140L ? Mth.lerp(Ease.outExpo((float)el / 140.0F), 18.0F, 5.5F) : 5.5F + (float)(el - 140L) * 4.0E-4F;
            bigText(g, font, c.main, w / 2.0F + jx, h * 0.42F + jy, s, col, true, alpha);
            if (el > 180L) {
               bigText(g, font, c.sub, w / 2.0F, h * 0.42F + 34.0F, 1.6F, -1, false, alpha * Math.min(1.0F, (float)(el - 180L) / 200.0F));
            }
            break;
         }
         case 1: {
            float s = 6.5F + (float)el * 3.5E-4F;
            bigText(g, font, c.main, w / 2.0F + jx, h * 0.38F + jy, s, col, true, alpha);
            float a2 = alpha * Math.min(1.0F, (float)el / 300.0F);
            bigText(g, font, c.sub, w / 2.0F, h * 0.38F + 40.0F, 2.6F, -1, true, a2);
            bigText(g, font, c.sub2, w / 2.0F, h * 0.38F + 62.0F, 1.3F, -16160, false, alpha * Math.min(1.0F, Math.max(0.0F, (float)(el - 250L) / 300.0F)));
            break;
         }
         case 2: {
            float s = el < 120L ? Mth.lerp(Ease.outBack((float)el / 120.0F), 0.5F, 2.2F) : 2.2F;
            bigText(g, font, c.main, w / 2.0F, h * 0.72F, s, col, true, alpha);
            bigText(g, font, c.sub, w / 2.0F, h * 0.72F + 20.0F, 1.1F, -1, false, alpha);
            break;
         }
         case 3:
            float slide = Ease.outExpo(Math.min(1.0F, (float)el / 400.0F));
            g.pose().pushPose();
            g.pose().translate(Mth.lerp(slide, -200.0F, 18.0F), h * 0.7F, 0.0F);
            g.pose().scale(2.4F, 2.4F, 1.0F);
            g.drawString(font, c.main, 0, 0, Draw.alpha(col, alpha), false);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(Mth.lerp(slide, -200.0F, 20.0F), h * 0.7F + 26.0F, 0.0F);
            g.pose().scale(1.1F, 1.1F, 1.0F);
            g.drawString(font, c.sub, 0, 0, Draw.alpha(-1, alpha), false);
            g.pose().popPose();
            break;
         case 4: {
            float pulse = 1.0F + 0.06F * Mth.sin((float)el / 80.0F);
            float s = (el < 160L ? Mth.lerp(Ease.outExpo((float)el / 160.0F), 22.0F, 9.0F) : 9.0F) * pulse;
            bigText(g, font, c.main, w / 2.0F + jx, h * 0.45F + jy, s, col, true, alpha);
            bigText(g, font, c.sub, w / 2.0F, h * 0.45F + 52.0F, 1.4F, -1, false, alpha * Math.min(1.0F, (float)el / 400.0F));
            bigText(g, font, c.sub2, w / 2.0F, h * 0.45F + 68.0F, 1.2F, -8054, false, alpha * Math.min(1.0F, Math.max(0.0F, (float)(el - 400L) / 300.0F)));
         }
      }
   }

   private static void drawRules(GuiGraphics g, Matrix4f m, float w, float h, long t, Font font) {
      if (rulesStart >= 0L) {
         long el = t - rulesStart;
         if (el > rulesLife) {
            rulesStart = -1L;
         } else {
            float a = el > rulesLife - 300L ? (float)(rulesLife - el) / 300.0F : Math.min(1.0F, (float)el / 150.0F);
            rect(m, 0.0F, 0.0F, w, h, Draw.alpha(-15073274, 0.72F * a));
            long frame = t / 55L;
            BufferBuilder b = gb(Mode.QUADS);

            for (int i = 0; i < 420; i++) {
               float x = (float)(Draw.hash(frame * 1000L + (long)i) * (double)w);
               float y = (float)(Draw.hash(frame * 2000L + (long)i) * (double)h);
               float s = 1.0F + (float)Draw.hash(frame * 3000L + (long)i) * 2.5F;
               int c = Draw.alpha(Draw.hash((long)i + frame) > 0.5 ? -1 : -49798, 0.35F * a);
               gv(b, m, x, y, c);
               gv(b, m, x, y + s, c);
               gv(b, m, x + s * 3.0F, y + s, c);
               gv(b, m, x + s * 3.0F, y, c);
            }

            gend(b);
            float glitch = frame % 7L == 0L ? 3.0F : 0.0F;
            bigText(g, font, "THE RULES ARE BEING FORCED INTO YOUR MIND", w / 2.0F + glitch, h * 0.2F, 1.6F, -45128, true, a);
            int chars = (int)(el / 14L);
            float y = h * 0.32F;

            for (String line : RULES) {
               int n = Math.min(line.length(), Math.max(0, chars));
               chars -= line.length();
               if (n <= 0) {
                  break;
               }

               g.pose().pushPose();
               g.pose().translate(w * 0.12F, y, 0.0F);
               g.pose().scale(1.25F, 1.25F, 1.0F);
               g.drawString(font, line.substring(0, n), 0, 0, Draw.alpha(-1, a), false);
               g.pose().popPose();
               y += 17.0F;
            }
         }
      }
   }

   private ScreenFx() {
   }

   public static final class Card {
      public static final int SLAM = 0;
      public static final int TITLE = 1;
      public static final int POP = 2;
      public static final int CORNER = 3;
      public static final int TIMER = 4;
      final String main;
      final String sub;
      final String sub2;
      final int style;
      final int color;
      final long start;
      final long life;
      final boolean rainbow;

      public Card(String main, String sub, String sub2, int style, int color, long life, boolean rainbow) {
         this.main = main;
         this.sub = sub;
         this.sub2 = sub2;
         this.style = style;
         this.color = color;
         this.start = Vfx.now();
         this.life = life;
         this.rainbow = rainbow;
      }
   }

   private static record Flash(long start, long life, int color) {
   }

   private static record Impact(long start, long life, int style, float fx, float fy, long seed) {
   }

   private static record Split(long start, long life, float angle, float mag) {
   }
}
