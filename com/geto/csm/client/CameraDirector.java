package com.geto.csm.client;

import com.jjk.mod.client.JjkPrefs;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;

public final class CameraDirector {
   private static Marker cam;
   private static Cutscene current;
   private static CameraType savedType;
   private static float eyeOld;
   private static float eye;
   private static float lastPartial = 1.0F;
   private static float shakeAmp;
   private static long shakeStart;
   private static long shakeLife;

   public static boolean active() {
      return current != null;
   }

   public static void play(Cutscene c) {
      if (!JjkPrefs.skipCutscenes()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            if (cam == null || cam.level() != mc.level) {
               cam = new Marker(EntityType.MARKER, mc.level);
            }

            if (current == null) {
               savedType = mc.options.getCameraType();
            }

            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (current == null) {
               eyeOld = eye = mc.player.getEyeHeight();
            }

            current = c;
            c.begin();
            c.update(1.0F);
            place(c);
            mc.setCameraEntity(cam);
         }
      }
   }

   public static void stop() {
      Minecraft mc = Minecraft.getInstance();
      if (current != null) {
         current = null;
         if (mc.player != null) {
            mc.setCameraEntity(mc.player);
         }

         if (savedType != null) {
            mc.options.setCameraType(savedType);
         }

         ScreenFx.letterbox(0.0F);
         ScreenFx.vignette(0.0F);
      }
   }

   private static void place(Cutscene c) {
      double y = c.outPos.y - (double)Mth.lerp(lastPartial, eyeOld, eye);
      cam.setPos(c.outPos.x, y, c.outPos.z);
      cam.xo = cam.xOld = c.outPos.x;
      cam.yo = cam.yOld = y;
      cam.zo = cam.zOld = c.outPos.z;
      cam.setYRot(c.outYaw);
      cam.yRotO = c.outYaw;
      cam.setXRot(c.outPitch);
      cam.xRotO = c.outPitch;
   }

   public static void frame(float partial) {
      Cutscene c = current;
      if (c != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && !c.done()) {
            lastPartial = partial;
            c.update(partial);
            place(c);
            if (mc.getCameraEntity() != cam) {
               mc.setCameraEntity(cam);
            }
         } else {
            stop();
         }
      }
   }

   public static void clientTick() {
      if (current != null) {
         eyeOld = eye;
         eye = eye + (0.0F - eye) * 0.5F;
      }
   }

   public static double fov(double vanilla) {
      return current == null ? vanilla : current.outFov;
   }

   public static float roll() {
      return current == null ? 0.0F : current.outRoll;
   }

   public static void shake(float amp, long life) {
      long now = Vfx.now();
      float remaining = currentShake(now);
      if (amp >= remaining) {
         shakeAmp = amp;
         shakeStart = now;
         shakeLife = life;
      }
   }

   private static float currentShake(long now) {
      if (now - shakeStart > shakeLife) {
         return 0.0F;
      } else {
         float p = (float)(now - shakeStart) / (float)shakeLife;
         return shakeAmp * (1.0F - p) * (1.0F - p);
      }
   }

   public static float[] shakeOffsets() {
      long now = Vfx.now();
      float a = currentShake(now);
      if (a <= 0.001F) {
         return new float[]{0.0F, 0.0F, 0.0F};
      } else {
         double t = (double)now / 1000.0;
         float yaw = (float)(Math.sin(t * 61.3) + Math.sin(t * 97.1) * 0.5) * a;
         float pitch = (float)(Math.sin(t * 53.7 + 1.3) + Math.sin(t * 89.9) * 0.5) * a;
         float roll = (float)Math.sin(t * 41.1 + 2.1) * a * 0.8F;
         return new float[]{yaw, pitch, Mth.clamp(roll, -12.0F, 12.0F)};
      }
   }

   public static void reset() {
      stop();
      cam = null;
      shakeAmp = 0.0F;
   }

   private CameraDirector() {
   }
}
