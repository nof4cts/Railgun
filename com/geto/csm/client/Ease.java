package com.geto.csm.client;

import net.minecraft.util.Mth;

public enum Ease {
   LINEAR,
   IN_OUT,
   OUT_EXPO,
   IN_EXPO,
   OUT_CUBIC,
   OUT_BACK;

   public float apply(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);

      return switch (this) {
         case LINEAR -> t;
         case IN_OUT -> t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
         case OUT_EXPO -> t >= 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, (double)(-10.0F * t));
         case IN_EXPO -> t <= 0.0F ? 0.0F : (float)Math.pow(2.0, (double)(10.0F * t - 10.0F));
         case OUT_CUBIC -> 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
         case OUT_BACK -> {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            yield 1.0F + c3 * (float)Math.pow((double)(t - 1.0F), 3.0) + c1 * (float)Math.pow((double)(t - 1.0F), 2.0);
         }
      };
   }

   public static float outExpo(float t) {
      return OUT_EXPO.apply(t);
   }

   public static float inExpo(float t) {
      return IN_EXPO.apply(t);
   }

   public static float inOut(float t) {
      return IN_OUT.apply(t);
   }

   public static float outBack(float t) {
      return OUT_BACK.apply(t);
   }

   public static float outCubic(float t) {
      return OUT_CUBIC.apply(t);
   }
}
