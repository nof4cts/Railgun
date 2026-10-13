package com.naoya.ps.client.fx;

import net.minecraft.world.phys.Vec3;

public final class Ribbon {
   public final Vec3[] spine;
   public final Vec3[] sides;
   public final float[] width;
   public final float[] alpha;
   public final int rgb;
   public final Ribbon.Style style;

   public Ribbon(Vec3[] spine, Vec3[] sides, float[] width, float[] alpha, int rgb, Ribbon.Style style) {
      this.spine = spine;
      this.sides = sides;
      this.width = width;
      this.alpha = alpha;
      this.rgb = rgb;
      this.style = style;
   }

   public static enum Style {
      GLOW,
      INK,
      TINT;
   }
}
