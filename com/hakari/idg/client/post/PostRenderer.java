package com.hakari.idg.client.post;

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
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.LoggerFactory;

public final class PostRenderer {
   private static final int LEVELS = 6;
   private static GlProgram down;
   private static GlProgram up;
   private static GlProgram composite;
   private static String failure;
   private static boolean warned;
   private static RenderTex scene;
   private static final RenderTex[] DOWN = new RenderTex[6];
   private static final RenderTex[] UP = new RenderTex[5];
   private static int vao = -1;
   private static final long T0 = System.nanoTime();

   private PostRenderer() {
   }

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
         LoggerFactory.getLogger("Hakari").info("Hakari effect shaders compiled");
      } catch (Exception var2) {
         closePrograms();
         failure = var2.getMessage();
         LoggerFactory.getLogger("Hakari").error("Hakari effect shaders failed to compile; screen effects disabled", var2);
      }
   }

   private static void closePrograms() {
      for (GlProgram p : new GlProgram[]{down, up, composite}) {
         if (p != null) {
            p.close();
         }
      }

      composite = null;
      up = null;
      down = null;
   }

   private static void ensureTargets(int w, int h) {
      if (scene == null || scene.width != w || scene.height != h) {
         closeTargets();
         scene = new RenderTex(w, h, false);
         int lw = w;
         int lh = h;

         for (int i = 0; i < 6; i++) {
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
            DOWN[i] = new RenderTex(lw, lh, true);
            if (i < 5) {
               UP[i] = new RenderTex(lw, lh, true);
            }
         }

         if (vao < 0) {
            vao = GL30.glGenVertexArrays();
         }
      }
   }

   private static void closeTargets() {
      if (scene != null) {
         scene.close();
      }

      scene = null;

      for (int i = 0; i < 6; i++) {
         if (DOWN[i] != null) {
            DOWN[i].close();
         }

         DOWN[i] = null;
         if (i < 5) {
            if (UP[i] != null) {
               UP[i].close();
            }

            UP[i] = null;
         }
      }
   }

   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            Post.Frame FX = Post.frame();
            if (!FX.idle()) {
               if (composite == null) {
                  if (failure != null && !warned && mc.player != null) {
                     warned = true;
                     mc.player
                        .displayClientMessage(Component.literal("§cHakari: screen-effect shaders failed to compile on this GPU (see latest.log)."), false);
                  }
               } else {
                  try {
                     draw(mc, event, FX);
                  } catch (Throwable var7) {
                     LoggerFactory.getLogger("Hakari").error("Hakari post pipeline failed; disabling screen effects until resources reload", var7);
                     failure = String.valueOf(var7.getMessage());
                     closePrograms();
                  } finally {
                     restore(mc);
                  }
               }
            }
         }
      }
   }

   private static void draw(Minecraft mc, RenderLevelStageEvent event, Post.Frame FX) {
      RenderTarget main = mc.getMainRenderTarget();
      int w = main.width;
      int h = main.height;
      ensureTargets(w, h);
      float time = (float)(System.nanoTime() - T0) / 1.0E9F % 3600.0F;
      RenderSystem.disableBlend();
      RenderSystem.disableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      GL30.glBindVertexArray(vao);
      GlStateManager._glBindFramebuffer(36008, main.frameBufferId);
      GlStateManager._glBindFramebuffer(36009, scene.fbo);
      GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, 16384, 9728);
      float bloomCfg = 1.0F;
      down.use();
      down.set("Src", 0);
      down.set("Threshold", 0.78F);
      RenderTex src = scene;

      for (int i = 0; i < 6; i++) {
         DOWN[i].bindWrite();
         bind(0, src.texture);
         down.set("SrcTexel", 1.0F / (float)src.width, 1.0F / (float)src.height);
         down.set("Prefilter", i == 0 ? 1.0F : 0.0F);
         tri();
         src = DOWN[i];
      }

      up.use();
      up.set("Low", 0);
      up.set("Cur", 1);
      up.set("Radius", 1.0F);

      for (int i = 4; i >= 0; i--) {
         RenderTex low = i == 4 ? DOWN[5] : UP[i + 1];
         UP[i].bindWrite();
         bind(0, low.texture);
         bind(1, DOWN[i].texture);
         up.set("LowTexel", 1.0F / (float)low.width, 1.0F / (float)low.height);
         tri();
      }

      GlStateManager._glBindFramebuffer(36160, main.frameBufferId);
      GlStateManager._viewport(0, 0, w, h);
      GlProgram c = composite;
      c.use();
      bind(0, scene.texture);
      bind(1, UP[0].texture);
      c.set("Scene", 0);
      c.set("Bloom", 1);
      c.set("ScreenSize", (float)w, (float)h);
      c.set("Time", time);
      float flashCfg = 1.0F;
      c.set("Flash", FX.flashR, FX.flashG, FX.flashB, FX.flash * flashCfg);
      c.set("Punch", Math.min(FX.punch, 1.2F));
      c.set("Aberration", FX.aberration);
      c.set("Desaturate", FX.desaturate);
      c.set("Contrast", FX.grade * 0.15F);
      c.set("Vignette", FX.vr, FX.vg, FX.vb, FX.vignette);
      c.set("Grain", 0.0F);
      c.set("Haki", FX.grade);
      c.set("GradeShadow", 0.0F, 0.07F, 0.03F);
      c.set("GradeHot", 0.82F, 1.0F, 0.88F);
      c.set("BloomStrength", (0.25F + FX.bloom) * bloomCfg);
      c.set("Accent", FX.ar, FX.ag, FX.ab);
      c.set("Shake", 0.0F, 0.0F);
      float iu = 0.5F;
      float iv = 0.5F;
      if (FX.impactAt != null) {
         Vector4f s = project(FX.impactAt.subtract(event.getCamera().getPosition()), event.getProjectionMatrix());
         if (s != null) {
            iu = Mth.clamp(s.x, 0.08F, 0.92F);
            iv = Mth.clamp(s.y, 0.08F, 0.92F);
         }
      }

      boolean frames = FX.impactMode > 0;
      c.set("Impact", frames ? (float)FX.impactMode : 0.0F, frames ? 1.0F : 0.0F, iu, iv);
      c.set("ImpactSeed", FX.impactSeed);
      tri();
   }

   private static Vector4f project(Vec3 rel, Matrix4f proj) {
      Matrix3f invViewRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
      Matrix3f viewRot = new Matrix3f(invViewRot).invert();
      Vector3f v = new Vector3f((float)rel.x, (float)rel.y, (float)rel.z);
      viewRot.transform(v);
      Vector4f clip = new Vector4f(v, 1.0F);
      proj.transform(clip);
      return clip.w <= 0.0F ? null : new Vector4f(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F, 0.0F, 0.0F);
   }

   private static void bind(int unit, int texture) {
      GlStateManager._activeTexture(33984 + unit);
      GlStateManager._bindTexture(texture);
   }

   private static void tri() {
      GL11.glDrawArrays(4, 0, 3);
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
