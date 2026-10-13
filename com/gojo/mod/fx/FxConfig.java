package com.gojo.mod.fx;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

public final class FxConfig {
   public static final ForgeConfigSpec SPEC;
   public static final BooleanValue SCREEN_SHAKE;
   public static final BooleanValue SCREEN_FLASH;
   public static final BooleanValue FOV_KICK;
   public static final DoubleValue PARTICLE_DENSITY;

   private FxConfig() {
   }

   public static boolean shake() {
      return get(SCREEN_SHAKE, true);
   }

   public static boolean flash() {
      return get(SCREEN_FLASH, true);
   }

   public static boolean fovKick() {
      return get(FOV_KICK, true);
   }

   public static float density() {
      try {
         return ((Double)PARTICLE_DENSITY.get()).floatValue();
      } catch (IllegalStateException var1) {
         return 1.0F;
      }
   }

   private static boolean get(BooleanValue value, boolean fallback) {
      try {
         return (Boolean)value.get();
      } catch (IllegalStateException var3) {
         return fallback;
      }
   }

   static {
      Builder builder = new Builder();
      builder.push("effects");
      SCREEN_SHAKE = builder.comment("Shake the camera on big hits.").define("screenShake", true);
      SCREEN_FLASH = builder.comment("Flash the screen on big hits and darken it inside Domains.").define("screenFlash", true);
      FOV_KICK = builder.comment("Briefly widen the field of view on big hits.").define("fovKick", true);
      PARTICLE_DENSITY = builder.comment("Multiplier for effect particle counts (0 = none).").defineInRange("particleDensity", 1.0, 0.0, 2.0);
      builder.pop();
      SPEC = builder.build();
   }
}
