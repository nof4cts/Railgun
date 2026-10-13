package com.shrine.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.shrine.ShrineMod;
import com.shrine.client.Casts;
import com.shrine.client.ScreenFx;
import com.shrine.config.ShrineConfig;
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

public final class PostFx {
   private static final int BLOOM_LEVELS = 6;
   private static final int MAX_SLASH = 48;
   private static GlProgram technique;
   private static GlProgram down;
   private static GlProgram up;
   private static GlProgram composite;
   private static String failure;
   private static boolean warned;
   private static RenderTex scene;
   private static RenderTex hdr;
   private static final RenderTex[] DOWN = new RenderTex[6];
   private static final RenderTex[] UP = new RenderTex[5];
   private static int vao = -1;
   private static final long T0 = System.nanoTime();
   private static final float[] SLO = new float[192];
   private static final float[] SLD = new float[192];
   private static final float[] SLS = new float[192];
   private static final float[] SLP = new float[192];

   private PostFx() {
   }

   public static void reload(ResourceManager rm) {
      closePrograms();

      try {
         technique = GlProgram.load(rm, "fullscreen.vsh", "technique.fsh");
         down = GlProgram.load(rm, "fullscreen.vsh", "bloom_down.fsh");
         up = GlProgram.load(rm, "fullscreen.vsh", "bloom_up.fsh");
         composite = GlProgram.load(rm, "fullscreen.vsh", "composite.fsh");
         failure = null;
         warned = false;
         ShrineMod.LOGGER.info("Shrine effect shaders compiled");
      } catch (Exception var2) {
         closePrograms();
         failure = var2.getMessage();
         ShrineMod.LOGGER.error("Shrine effect shaders failed to compile; effects disabled", var2);
      }
   }

   private static void closePrograms() {
      for (GlProgram p : new GlProgram[]{technique, down, up, composite}) {
         if (p != null) {
            p.close();
         }
      }

      composite = null;
      up = null;
      down = null;
      technique = null;
   }

   private static void ensureTargets(int w, int h) {
      if (scene == null || scene.width != w || scene.height != h) {
         closeTargets();
         scene = new RenderTex(w, h, false);
         hdr = new RenderTex(w, h, true);
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

      if (hdr != null) {
         hdr.close();
      }

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

      hdr = null;
      scene = null;
   }

   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         Minecraft mc = Minecraft.getInstance();
         if (technique == null) {
            if (failure != null && !warned && mc.player != null && Casts.any()) {
               warned = true;
               mc.player.displayClientMessage(Component.literal("§cShrine: effect shaders failed to compile on this GPU; see latest.log."), false);
            }
         } else {
            ScreenFx.update();
            Casts.Frame fx = Casts.frame(event.getPartialTick());
            if (fx.any() || ScreenFx.active()) {
               try {
                  draw(mc, event, fx);
               } catch (Throwable var7) {
                  ShrineMod.LOGGER.error("Shrine post pipeline failed; disabling effects until resources reload", var7);
                  failure = String.valueOf(var7.getMessage());
                  closePrograms();
               } finally {
                  restoreState(mc);
               }
            }
         }
      }
   }

   private static void rel(GlProgram p, String name, Vec3 v, Vec3 cam, float w) {
      if (v == null) {
         p.set(name, 0.0F, 0.0F, 0.0F, w);
      } else {
         p.set(name, (float)(v.x - cam.x), (float)(v.y - cam.y), (float)(v.z - cam.z), w);
      }
   }

   private static void dir(GlProgram p, String name, Vec3 v, float w) {
      if (v == null) {
         v = new Vec3(0.0, 0.0, 1.0);
      }

      p.set(name, (float)v.x, (float)v.y, (float)v.z, w);
   }

   private static void draw(Minecraft mc, RenderLevelStageEvent event, Casts.Frame fx) {
      RenderTarget main = mc.getMainRenderTarget();
      int w = main.width;
      int h = main.height;
      ensureTargets(w, h);
      Vec3 cam = event.getCamera().getPosition();
      Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
      Matrix4f invProj = new Matrix4f(proj).invert();
      Matrix3f invViewRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
      Matrix3f viewRot = new Matrix3f(invViewRot).invert();
      float time = (float)(System.nanoTime() - T0) / 1.0E9F % 3600.0F;
      RenderSystem.disableBlend();
      RenderSystem.disableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      GL30.glBindVertexArray(vao);
      GlStateManager._glBindFramebuffer(36008, main.frameBufferId);
      GlStateManager._glBindFramebuffer(36009, scene.fbo);
      GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, 16384, 9728);
      hdr.bindWrite();
      GlProgram p = technique;
      p.use();
      bindTexture(0, scene.texture);
      bindTexture(1, main.getDepthTextureId());
      p.set("SceneTex", 0);
      p.set("DepthTex", 1);
      p.set("Proj", proj);
      p.set("InvProj", invProj);
      p.set("ViewRot", viewRot);
      p.set("InvViewRot", invViewRot);
      p.set("Time", time);
      p.set("Steps", (Integer)ShrineConfig.CLIENT.volumeSteps.get());
      int n = Math.min(48, fx.slashes.size());

      for (int i = 0; i < n; i++) {
         float[] s = fx.slashes.get(i);
         SLO[i * 4] = (float)((double)s[0] - cam.x);
         SLO[i * 4 + 1] = (float)((double)s[1] - cam.y);
         SLO[i * 4 + 2] = (float)((double)s[2] - cam.z);
         SLO[i * 4 + 3] = s[3];
         System.arraycopy(s, 4, SLD, i * 4, 4);
         System.arraycopy(s, 8, SLS, i * 4, 4);
         System.arraycopy(s, 12, SLP, i * 4, 4);
      }

      p.set("NSlash", n);
      if (n > 0) {
         p.setVec4Array("SlO", SLO, n);
         p.setVec4Array("SlD", SLD, n);
         p.setVec4Array("SlS", SLS, n);
         p.setVec4Array("SlP", SLP, n);
      }

      rel(p, "WebC", fx.webC, cam, fx.webR);
      p.set("WebP", fx.webAge, fx.webSeed, fx.webAge >= 0.0F ? 1.0F : 0.0F, 0.0F);
      rel(p, "BfC", fx.bfC, cam, fx.bfAge);
      p.set("BfSeed", fx.bfSeed);
      p.set("FOn", fx.fuga ? 1.0F : 0.0F);
      if (fx.fuga) {
         rel(p, "FHand", fx.fHand, cam, fx.fHandAmt);
         rel(p, "FBow", fx.fBow, cam, fx.fBowAmt);
         dir(p, "FDir", fx.fDir, fx.fDraw);
         dir(p, "FUp", fx.fUp, fx.fFlight);
         rel(p, "FFrom", fx.fFrom, cam, 1.0F);
         rel(p, "FTarget", fx.fTarget, cam, fx.fBlast);
         p.set("FRadius", fx.fRadius);
      }

      rel(p, "DomC", fx.domC, cam, fx.domR);
      p.set("DomP", fx.domAge, fx.domStrength, fx.domFull, fx.domain ? 1.0F : 0.0F);
      rel(p, "WsO", fx.wsO, cam, fx.wsAge);
      dir(p, "WsD", fx.wsD, fx.wsLen);
      p.set("WsP", fx.wsSplit, 3.0F, fx.wsSky, fx.ws ? 1.0F : 0.0F);
      drawTriangle();
      float[] rays = null;
      if (fx.fuga && fx.fBlast >= 0.0F && fx.fTarget != null) {
         Vector4f s = project(fx.fTarget.add(0.0, (double)fx.fRadius * 1.5, 0.0).subtract(cam), viewRot, proj);
         if (s != null) {
            rays = new float[]{s.x, s.y, 0.9F * (1.0F - Mth.clamp((fx.fBlast - 3.0F) / 5.0F, 0.0F, 1.0F)), 1.0F, 0.55F, 0.2F};
         }
      } else if (fx.fuga && fx.fBowAmt > 0.05F && fx.fBow != null) {
         Vector4f s = project(fx.fBow.subtract(cam), viewRot, proj);
         if (s != null) {
            rays = new float[]{s.x, s.y, 0.5F * fx.fBowAmt, 1.0F, 0.6F, 0.25F};
         }
      }

      bloomAndComposite(main, w, h, time, cam, viewRot, proj, rays, fx);
   }

   private static void bloomAndComposite(RenderTarget main, int w, int h, float time, Vec3 cam, Matrix3f viewRot, Matrix4f proj, float[] rays, Casts.Frame fx) {
      RenderTex src = hdr;
      down.use();
      down.set("Src", 0);
      down.set("Threshold", 1.3F);

      for (int i = 0; i < 6; i++) {
         DOWN[i].bindWrite();
         bindTexture(0, src.texture);
         down.set("SrcTexel", 1.0F / (float)src.width, 1.0F / (float)src.height);
         down.set("Prefilter", i == 0 ? 1.0F : 0.0F);
         drawTriangle();
         src = DOWN[i];
      }

      up.use();
      up.set("Low", 0);
      up.set("Cur", 1);
      up.set("Radius", 1.0F);
      up.set("LowWeight", 0.6F);

      for (int i = 4; i >= 0; i--) {
         RenderTex low = i == 4 ? DOWN[5] : UP[i + 1];
         UP[i].bindWrite();
         bindTexture(0, low.texture);
         bindTexture(1, DOWN[i].texture);
         up.set("LowTexel", 1.0F / (float)low.width, 1.0F / (float)low.height);
         drawTriangle();
      }

      GlStateManager._glBindFramebuffer(36160, main.frameBufferId);
      GlStateManager._viewport(0, 0, w, h);
      composite.use();
      bindTexture(0, hdr.texture);
      bindTexture(1, UP[0].texture);
      composite.set("Hdr", 0);
      composite.set("Bloom", 1);
      composite.set("ScreenSize", (float)w, (float)h);
      composite.set("Time", time);
      composite.set("BloomStrength", 0.4F * ((Double)ShrineConfig.CLIENT.bloom.get()).floatValue());
      composite.set("Exposure", 1.0F);
      composite.set("Aberration", ScreenFx.aberration(fx.domain ? 0.25F * fx.domStrength : 0.0F));
      composite.set("Flash", 1.0F, 0.97F, 0.94F, ScreenFx.flash());
      composite.set("Afterimage", ScreenFx.afterimage());
      composite.set("Desaturate", ScreenFx.desaturate());
      composite.set("Vignette", 0.25F + (fx.domain ? 0.35F * fx.domStrength : 0.0F));
      composite.set("Grain", 0.3F * ((Double)ShrineConfig.CLIENT.filmGrain.get()).floatValue());
      composite.set("Shake", 0.0F, 0.0F);
      float shock = ScreenFx.shockStrength();
      Vector4f clip = shock > 0.0F ? project(ScreenFx.shockAt().subtract(cam), viewRot, proj) : null;
      if (clip != null) {
         composite.set("Shockwave", clip.x, clip.y, ScreenFx.shockRadius(), shock);
      } else {
         composite.set("Shockwave", 0.5F, 0.5F, 0.0F, 0.0F);
      }

      float[] imp = ShrineConfig.CLIENT.impactFrames.get() ? ScreenFx.impactFrame() : null;
      float iu = ScreenFx.impactU();
      float iv = ScreenFx.impactV();
      if (imp != null && ScreenFx.impactWorld() != null) {
         Vector4f c = project(ScreenFx.impactWorld().subtract(cam), viewRot, proj);
         if (c != null) {
            iu = Mth.clamp(c.x, 0.05F, 0.95F);
            iv = Mth.clamp(c.y, 0.05F, 0.95F);
         }
      }

      if (imp != null) {
         composite.set("Impact", imp[0], imp[1], iu, iv);
         composite.set("ImpactSeed", imp[2]);
      } else {
         composite.set("Impact", 0.0F, 0.0F, 0.5F, 0.5F);
         composite.set("ImpactSeed", 0.0F);
      }

      float[] acc = ScreenFx.accent();
      composite.set("Accent", acc[0], acc[1], acc[2]);
      composite.set("Punch", ScreenFx.punch());
      if (rays != null && rays[2] > 0.001F) {
         composite.set("GodRays", rays[0], rays[1], rays[2], 0.0F);
         composite.set("GodRayColor", rays[3], rays[4], rays[5]);
      } else {
         composite.set("GodRays", 0.5F, 0.5F, 0.0F, 0.0F);
         composite.set("GodRayColor", 1.0F, 1.0F, 1.0F);
      }

      drawTriangle();
   }

   private static Vector4f project(Vec3 rel, Matrix3f viewRot, Matrix4f proj) {
      Vector3f v = new Vector3f((float)rel.x, (float)rel.y, (float)rel.z);
      viewRot.transform(v);
      Vector4f clip = new Vector4f(v, 1.0F);
      proj.transform(clip);
      return clip.w <= 0.0F ? null : new Vector4f(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F, 0.0F, 0.0F);
   }

   private static void bindTexture(int unit, int texture) {
      GlStateManager._activeTexture(33984 + unit);
      GlStateManager._bindTexture(texture);
   }

   private static void drawTriangle() {
      GL11.glDrawArrays(4, 0, 3);
   }

   private static void restoreState(Minecraft mc) {
      GL30.glBindVertexArray(0);
      BufferUploader.reset();
      bindTexture(1, 0);
      bindTexture(0, 0);
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
