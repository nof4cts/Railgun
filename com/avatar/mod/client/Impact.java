package com.avatar.mod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

public final class Impact {
   public static final int WHITE = 0;
   public static final int BLUE = 3;
   public static final int PANEL = 5;
   public static final int INVERT = 6;
   public static final int FREEZE = 7;
   public static final int ELECTRIC = 8;
   public static final int SEISMIC = 9;
   private static final List<Impact.Frame> FRAMES = new ArrayList<>();
   private static TextureTarget copy;
   private static boolean frozen;
   private static long counter;

   private Impact() {
   }

   public static void hit(int style, int delay, int hold, int fade, float cx, float cy, boolean freeze) {
      FRAMES.add(new Impact.Frame(style, delay, hold, fade, cx, cy, counter++, freeze));
   }

   public static boolean active() {
      for (Impact.Frame f : FRAMES) {
         if (f.age >= f.delay) {
            return true;
         }
      }

      return false;
   }

   public static void tick() {
      Iterator<Impact.Frame> it = FRAMES.iterator();

      while (it.hasNext()) {
         Impact.Frame f = it.next();
         if (++f.age > f.delay + f.hold + f.fade) {
            it.remove();
         }
      }
   }

   public static void clear() {
      FRAMES.clear();
      frozen = false;
   }

   public static void render(float partial) {
      Impact.Frame f = null;

      for (Impact.Frame x : FRAMES) {
         if (x.age >= x.delay && (f == null || x.seed > f.seed)) {
            f = x;
         }
      }

      if (f != null && AShaders.impact != null) {
         float t = (float)(f.age - f.delay) + partial;
         float strength = t <= (float)f.hold ? 1.0F : Math.max(0.0F, 1.0F - (t - (float)f.hold) / (float)Math.max(1, f.fade));
         if (strength <= 0.01F) {
            frozen = false;
         } else {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            if (copy == null || copy.width != main.width || copy.height != main.height) {
               if (copy != null) {
                  copy.destroyBuffers();
               }

               copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
               frozen = false;
            }

            if (!f.freeze || !frozen) {
               GlStateManager._glBindFramebuffer(36008, main.frameBufferId);
               GlStateManager._glBindFramebuffer(36009, copy.frameBufferId);
               GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, 16384, 9728);
               main.bindWrite(true);
            }

            frozen = f.freeze;
            ShaderInstance sh = AShaders.impact;
            sh.setSampler("DiffuseSampler", copy.getColorTextureId());
            sh.safeGetUniform("Time").set((float)(mc.level == null ? 0L : mc.level.getGameTime() % 100000L) + partial);
            sh.safeGetUniform("Strength").set(strength);
            sh.safeGetUniform("Style").set((float)f.style);
            sh.safeGetUniform("Center").set(f.cx, f.cy);
            sh.safeGetUniform("Seed").set((float)(f.seed % 97L));
            sh.safeGetUniform("Texel").set(1.0F / (float)main.width, 1.0F / (float)main.height);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(Mode.QUADS, Gfx.FORMAT);
            b.vertex(-1.0, -1.0, 0.0).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).normal(0.0F, 0.0F, 1.0F).endVertex();
            b.vertex(1.0, -1.0, 0.0).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).normal(0.0F, 0.0F, 1.0F).endVertex();
            b.vertex(1.0, 1.0, 0.0).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F).normal(0.0F, 0.0F, 1.0F).endVertex();
            b.vertex(-1.0, 1.0, 0.0).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F).normal(0.0F, 0.0F, 1.0F).endVertex();
            sh.apply();
            BufferUploader.draw(b.end());
            sh.clear();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
         }
      } else {
         frozen = false;
      }
   }

   private static final class Frame {
      final int style;
      final int delay;
      final int hold;
      final int fade;
      final float cx;
      final float cy;
      final long seed;
      final boolean freeze;
      int age;

      Frame(int style, int delay, int hold, int fade, float cx, float cy, long seed, boolean freeze) {
         this.style = style;
         this.delay = delay;
         this.hold = hold;
         this.fade = fade;
         this.cx = cx;
         this.cy = cy;
         this.seed = seed;
         this.freeze = freeze;
      }
   }
}
