package com.naoya.ps.client.cine;

import java.util.List;
import net.minecraft.util.Mth;

public final class Film {
   public final Film.Clock clock;
   public final List<Shot> shots;
   public final int end;
   public final Film.Card card;
   public final String[][] lines;
   public final int blendIn;
   public final int blendOut;

   public Film(Film.Clock clock, List<Shot> shots, int end, Film.Card card, String[][] lines, int blendIn, int blendOut) {
      this.clock = clock;
      this.shots = shots;
      this.end = end;
      this.card = card;
      this.lines = lines;
      this.blendIn = blendIn;
      this.blendOut = blendOut;
   }

   CameraPose pose(float t) {
      int index = 0;

      for (int i = 0; i < this.shots.size(); i++) {
         if (t >= (float)this.shots.get(i).start()) {
            index = i;
         }
      }

      Shot shot = this.shots.get(index);
      CameraPose pose = evaluate(shot, t);
      if (index > 0 && shot.blend() > 0 && t - (float)shot.start() < (float)shot.blend()) {
         Shot prev = this.shots.get(index - 1);
         CameraPose from = evaluate(prev, Math.min(t, (float)prev.end()));
         pose = from.lerp(pose, smooth((t - (float)shot.start()) / (float)shot.blend()));
      }

      return pose;
   }

   private static CameraPose evaluate(Shot s, float t) {
      float k = Mth.clamp((t - (float)s.start()) / Math.max(1.0F, (float)(s.end() - s.start())), 0.0F, 1.0F);
      return s.path().pose(easeInOut(k), t);
   }

   static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   static float easeInOut(float x) {
      return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float)Math.pow((double)(-2.0F * x + 2.0F), 3.0) / 2.0F;
   }

   public static record Card(String style, String kanji, String title, int color, int start, int length) {
   }

   @FunctionalInterface
   public interface Clock {
      float time(float var1);
   }
}
