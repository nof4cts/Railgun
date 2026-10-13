package com.shrine.client;

import com.shrine.config.ShrineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;

public final class ScreenFx {
   private static float flash;
   private static float afterimage;
   private static float trauma;
   private static float aberration;
   private static float desaturate;
   private static double shockStart = -1.0;
   private static Vec3 shockAt = Vec3.ZERO;
   private static float shockStrength;
   private static long last = System.nanoTime();
   private static float ambientShake;
   public static final int INK = 1;
   public static final int NEGATIVE = 2;
   public static final int TWO_TONE = 3;
   public static final int HALFTONE = 4;
   public static final int WHITE_OUT = 5;
   private static ScreenFx.Hit hit;
   private static double hitStart = -1.0;
   private static Vec3 hitWorld;
   private static float hitU = 0.5F;
   private static float hitV = 0.5F;
   private static float[] accent = new float[]{1.0F, 0.2F, 0.05F};
   private static float pendingFlash;
   private static double pendingFlashAt = -1.0;
   private static float punch;
   private static int hitSalt;

   public static void hit(ScreenFx.Hit kind, Vec3 world, float u, float v, float r, float g, float b) {
      hit = kind;
      hitStart = seconds();
      hitWorld = world;
      hitU = u;
      hitV = v;
      accent = new float[]{r, g, b};
      hitSalt = (int)(Math.random() * 1000.0);
      punch = Math.max(punch, kind == ScreenFx.Hit.LIGHT ? 0.5F : 1.0F);
      trauma = Math.min(1.0F, trauma + (kind == ScreenFx.Hit.LIGHT ? 0.25F : (kind == ScreenFx.Hit.HEAVY ? 0.55F : 0.9F)));
   }

   public static float[] impactFrame() {
      if (hit != null && !(hitStart < 0.0)) {
         double ms = (seconds() - hitStart) * 1000.0;
         int acc = 0;

         for (int i = 0; i < hit.steps.length; i++) {
            int[] st = hit.steps[i];
            if (ms < (double)(acc + st[1])) {
               float amount = 1.0F;
               if (i == hit.steps.length - 1) {
                  amount = 1.0F - (float)((ms - (double)acc) / (double)st[1]);
               }

               return new float[]{(float)st[0], amount, (float)(i * 17 + hitSalt)};
            }

            acc += st[1];
         }

         hit = null;
         return null;
      } else {
         return null;
      }
   }

   public static Vec3 impactWorld() {
      return hitWorld;
   }

   public static float impactU() {
      return hitU;
   }

   public static float impactV() {
      return hitV;
   }

   public static float[] accent() {
      return accent;
   }

   public static float punch() {
      return punch * ((Double)ShrineConfig.CLIENT.cameraShake.get()).floatValue();
   }

   public static void flashAfterHit(float amount) {
      pendingFlash = Math.max(pendingFlash, amount);
      pendingFlashAt = hitStart + (hit == null ? 0.0 : (double)hit.total / 1000.0);
   }

   private ScreenFx() {
   }

   private static double seconds() {
      return (double)System.nanoTime() / 1.0E9;
   }

   public static void impact(Vec3 at, float rs, float r, float g, float b) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
         float k = Mth.clamp(1.0F - (float)(dist / (900.0 + (double)rs * 30.0)), 0.15F, 1.0F);
         hit(ScreenFx.Hit.ULTIMATE, at, 0.5F, 0.5F, r, g, b);
         flashAfterHit(k);
         afterimage = Math.max(afterimage, k);
         trauma = Math.min(1.0F, trauma + k);
         aberration = Math.max(aberration, k);
         desaturate = Math.max(desaturate, 0.6F * k);
         shockStart = seconds();
         shockAt = at;
         shockStrength = k;
      }
   }

   public static void shock(Vec3 at, float strength) {
      shockStart = seconds();
      shockAt = at;
      shockStrength = strength;
   }

   public static void cutFlash(float amount) {
      flash = Math.max(flash, amount);
   }

   public static void setAmbient(float shake) {
      ambientShake = shake;
   }

   public static boolean active() {
      return flash > 0.001F
         || afterimage > 0.001F
         || trauma > 0.001F
         || aberration > 0.001F
         || desaturate > 0.001F
         || shockStart >= 0.0
         || hit != null
         || punch > 0.001F
         || pendingFlashAt >= 0.0;
   }

   public static void update() {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - last) / 1.0E9F);
      last = now;
      flash = Math.max(0.0F, flash - dt * 1.4F);
      afterimage = Math.max(0.0F, afterimage - dt * 0.35F);
      trauma = Math.max(0.0F, trauma - dt * 0.33F);
      aberration = Math.max(0.0F, aberration - dt * 0.6F);
      desaturate = Math.max(0.0F, desaturate - dt * 0.12F);
      if (shockStart >= 0.0 && seconds() - shockStart > 4.0) {
         shockStart = -1.0;
      }

      punch = Math.max(0.0F, punch - dt * (hit != null ? 0.6F : 3.0F));
      if (pendingFlashAt >= 0.0 && seconds() >= pendingFlashAt) {
         flash = Math.max(flash, pendingFlash);
         pendingFlash = 0.0F;
         pendingFlashAt = -1.0;
      }
   }

   public static float flash() {
      float f = ((Double)ShrineConfig.CLIENT.flash.get()).floatValue();
      float x = flash;
      return x * x * f;
   }

   public static float afterimage() {
      return afterimage * ((Double)ShrineConfig.CLIENT.flash.get()).floatValue();
   }

   public static float aberration(float base) {
      return (base + aberration * 1.6F + ambientShake * 0.8F) * ((Double)ShrineConfig.CLIENT.aberration.get()).floatValue();
   }

   public static float desaturate() {
      return desaturate;
   }

   public static float shockRadius() {
      return shockStart < 0.0 ? 0.0F : (float)((seconds() - shockStart) * 0.75);
   }

   public static float shockStrength() {
      if (shockStart < 0.0) {
         return 0.0F;
      } else {
         float age = (float)(seconds() - shockStart);
         return shockStrength * Math.max(0.0F, 1.0F - age / 3.0F);
      }
   }

   public static Vec3 shockAt() {
      return shockAt;
   }

   private static float noise(double t, int seed) {
      return (float)(
         Math.sin(t * 1.0 + (double)seed * 12.9898) * 0.55 + Math.sin(t * 2.31 + (double)seed * 7.233) * 0.3 + Math.sin(t * 5.17 + (double)seed * 3.719) * 0.15
      );
   }

   public static void onCameraAngles(ComputeCameraAngles event) {
      float s = Math.min(1.0F, trauma + ambientShake);
      if (!(s <= 0.001F)) {
         s = s * s * ((Double)ShrineConfig.CLIENT.cameraShake.get()).floatValue();
         double t = seconds() * 26.0;
         event.setYaw(event.getYaw() + s * 3.2F * noise(t, 1));
         event.setPitch(event.getPitch() + s * 2.6F * noise(t, 2));
         event.setRoll(event.getRoll() + s * 4.5F * noise(t * 0.7, 3));
      }
   }

   public static void reset() {
      pendingFlash = 0.0F;
      punch = 0.0F;
      ambientShake = 0.0F;
      desaturate = 0.0F;
      aberration = 0.0F;
      trauma = 0.0F;
      afterimage = 0.0F;
      flash = 0.0F;
      shockStart = -1.0;
      pendingFlashAt = -1.0;
      hit = null;
   }

   public static enum Hit {
      LIGHT(new int[][]{{1, 45}, {2, 45}}),
      HEAVY(new int[][]{{5, 35}, {1, 55}, {2, 45}, {3, 60}, {1, 40}, {2, 70}}),
      ULTIMATE(new int[][]{{5, 50}, {1, 70}, {2, 50}, {3, 70}, {1, 45}, {4, 70}, {2, 50}, {3, 85}, {1, 50}, {5, 40}, {2, 160}});

      final int[][] steps;
      final int total;

      private Hit(int[][] steps) {
         this.steps = steps;
         int t = 0;

         for (int[] st : steps) {
            t += st[1];
         }

         this.total = t;
      }
   }
}
