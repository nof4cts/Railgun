package com.naoya.ps.client.fx;

import net.minecraft.world.phys.Vec3;

public final class ScreenFx {
   public float shake;
   public float flash;
   public float flashR = 1.0F;
   public float flashG = 1.0F;
   public float flashB = 1.0F;
   public int impactMode;
   public float impactAmount;
   public float impactSeed;
   public Vec3 impactAt;
   public float accentR = 1.0F;
   public float accentG = 0.15F;
   public float accentB = 0.1F;
   public float punch;
   public float aberration;
   public float desaturate;
   public float vignette;
   public float vignetteR;
   public float vignetteG;
   public float vignetteB;
   public float grain;
   public float haki;
   public float bloom;
   public float contrast;

   public void reset() {
      this.shake = this.flash = this.punch = this.aberration = this.desaturate = this.vignette = this.grain = this.haki = this.bloom = this.contrast = 0.0F;
      this.flashR = this.flashG = this.flashB = 1.0F;
      this.impactMode = 0;
      this.impactAmount = 0.0F;
      this.impactSeed = 0.0F;
      this.impactAt = null;
      this.vignetteR = this.vignetteG = this.vignetteB = 0.0F;
   }

   public boolean idle() {
      return this.shake < 0.001F
         && this.flash < 0.001F
         && this.impactMode == 0
         && this.punch < 0.001F
         && this.aberration < 0.001F
         && this.desaturate < 0.001F
         && this.vignette < 0.001F
         && this.grain < 0.001F
         && this.haki < 0.001F
         && this.bloom < 0.001F
         && this.contrast < 0.001F;
   }

   public void shake(float v) {
      this.shake = Math.max(this.shake, v);
   }

   public void flash(int rgb, float v) {
      if (v > this.flash) {
         this.flash = v;
         this.flashR = (float)(rgb >> 16 & 0xFF) / 255.0F;
         this.flashG = (float)(rgb >> 8 & 0xFF) / 255.0F;
         this.flashB = (float)(rgb & 0xFF) / 255.0F;
      }
   }

   public void vignette(int rgb, float v) {
      if (v > this.vignette) {
         this.vignette = v;
         this.vignetteR = (float)(rgb >> 16 & 0xFF) / 255.0F;
         this.vignetteG = (float)(rgb >> 8 & 0xFF) / 255.0F;
         this.vignetteB = (float)(rgb & 0xFF) / 255.0F;
      }
   }

   public void punch(float v) {
      this.punch = Math.max(this.punch, v);
   }

   public void aberration(float v) {
      this.aberration = Math.max(this.aberration, v);
   }

   public void desaturate(float v) {
      this.desaturate = Math.max(this.desaturate, v);
   }

   public void grain(float v) {
      this.grain = Math.max(this.grain, v);
   }

   public void haki(float v) {
      this.haki = Math.max(this.haki, v);
   }

   public void bloom(float v) {
      this.bloom = Math.max(this.bloom, v);
   }

   public void contrast(float v) {
      this.contrast = Math.max(this.contrast, v);
   }

   public void impact(int[][] seq, float since, Vec3 at, int accent) {
      if (!(since < 0.0F)) {
         int tick = (int)Math.floor((double)since);
         int acc = 0;

         for (int i = 0; i < seq.length; i++) {
            if (tick < acc + seq[i][1]) {
               this.impactMode = seq[i][0];
               this.impactAmount = 1.0F;
               this.impactSeed = (float)i * 17.31F + (float)acc;
               this.impactAt = at;
               this.accentR = (float)(accent >> 16 & 0xFF) / 255.0F;
               this.accentG = (float)(accent >> 8 & 0xFF) / 255.0F;
               this.accentB = (float)(accent & 0xFF) / 255.0F;
               return;
            }

            acc += seq[i][1];
         }
      }
   }

   public static float window(float t, float in0, float in1, float out0, float out1) {
      float a = Math.max(0.0F, Math.min(1.0F, (t - in0) / Math.max(in1 - in0, 0.001F)));
      float b = 1.0F - Math.max(0.0F, Math.min(1.0F, (t - out0) / Math.max(out1 - out0, 0.001F)));
      return Math.min(a, b);
   }

   public static float hit(float t, float at, float tau) {
      return t < at ? 0.0F : (float)Math.exp((double)(-(t - at) / tau));
   }

   public static float smooth(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return x * x * (3.0F - 2.0F * x);
   }
}
