package com.hakari.idg.client;

import java.util.HashMap;
import java.util.Map;

public final class ReelSet {
   private static final Map<Integer, ReelSet> BY_CASTER = new HashMap<>();
   private final long[] stopAt = new long[3];
   private final int[] target = new int[]{6, 6, 6};
   private final boolean[] slowLast = new boolean[3];
   private long spinStart = -1L;

   public static ReelSet of(int casterId) {
      return BY_CASTER.computeIfAbsent(casterId, k -> new ReelSet());
   }

   public static void clear() {
      BY_CASTER.clear();
   }

   public void spin(int n1, int n2, int n3, long s1, long s2, long s3, boolean dramaticLast) {
      long now = Vfx.now();
      this.spinStart = now;
      this.target[0] = n1 - 1;
      this.target[1] = n2 - 1;
      this.target[2] = n3 - 1;
      this.stopAt[0] = now + s1;
      this.stopAt[1] = now + s2;
      this.stopAt[2] = now + s3;
      this.slowLast[2] = dramaticLast;
   }

   public boolean spinning(long t) {
      return this.spinStart >= 0L && t < this.stopAt[2];
   }

   public boolean stopped(int reel, long t) {
      return this.spinStart < 0L || t >= this.stopAt[reel];
   }

   public double position(int reel, long t) {
      if (this.spinStart < 0L) {
         return (double)this.target[reel];
      } else {
         long d = this.stopAt[reel] - t;
         if (d > 0L) {
            double faces = (double)d / 85.0;
            if (this.slowLast[reel]) {
               faces *= 0.22 + 0.78 * Math.min(1.0, (double)d / 1700.0);
            }

            return (double)this.target[reel] - faces;
         } else {
            double k = (double)(-d) / 260.0;
            return (double)this.target[reel] + 0.18 * Math.exp(-k * 4.0) * Math.sin(k * 16.0);
         }
      }
   }

   public static int numberAt(double pos) {
      int idx = (int)Math.floor(pos + 0.5);
      return (idx % 7 + 7) % 7 + 1;
   }

   public int finalNumber(int reel) {
      return this.target[reel] + 1;
   }

   public static int numberColor(int n) {
      return switch (n) {
         case 1 -> -11549697;
         case 2 -> -29992;
         case 3 -> -11443;
         case 4 -> -4944641;
         case 5 -> -8585365;
         default -> -1;
         case 7 -> -54726;
      };
   }
}
