package com.yuta.mod;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DhruvPath {
   public static final int LIFE = 200;
   public static final int TRAIL = 26;
   public static final int COUNT = 2;

   private DhruvPath() {
   }

   public static Vec3 pos(Vec3 c, float r, int seed, int i, float t) {
      float ph = (float)seed * 0.37F + (float)i * (float) Math.PI;
      float w = 0.085F;
      float wobble = 0.8F + 0.2F * Mth.sin(t * 0.05F + (float)i * 2.0F);
      double x = c.x + (double)(r * Mth.cos(w * t + ph) * wobble);
      double y = c.y + 1.7 + 1.3 * (double)Mth.sin(2.0F * w * t + ph * 0.5F);
      double z = c.z + (double)(r * Mth.sin(1.3F * w * t + ph) * wobble);
      return new Vec3(x, y, z);
   }
}
