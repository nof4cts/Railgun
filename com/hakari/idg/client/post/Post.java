package com.hakari.idg.client.post;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;

public final class Post {
   public static final int INK = 1;
   public static final int INVERT = 2;
   public static final int CRIMSON = 3;
   public static final int NEGATIVE = 4;
   public static final int WHITEOUT = 5;
   public static final int HALFTONE = 6;
   public static final int GRADE = 7;
   public static final int SLASHED = 8;
   public static final int LINES = 9;
   public static final int RAINBOW = 10;
   public static final int JACKPOT = 11;
   public static final int[][] LIGHT = new int[][]{{1, 1}, {2, 1}};
   public static final int[][] HEAVY = new int[][]{{5, 1}, {1, 1}, {2, 1}, {11, 2}, {9, 1}};
   public static final int[][] DOMAIN = new int[][]{{5, 1}, {1, 1}, {2, 1}, {6, 1}, {9, 2}, {4, 1}, {2, 1}};
   public static final int[][] TITLE = new int[][]{{5, 1}, {10, 2}, {2, 1}, {10, 1}, {8, 1}, {10, 2}, {9, 1}};
   public static final int[][] CONFIRM = new int[][]{{10, 2}, {5, 1}, {10, 2}, {2, 1}, {10, 2}};
   public static final int[][] JACKPOT_HIT = new int[][]{{5, 1}, {11, 1}, {2, 1}, {10, 1}, {11, 1}, {8, 1}, {10, 2}, {4, 1}, {11, 2}, {9, 1}, {5, 1}, {10, 2}};
   public static final int[][] FINISHER = new int[][]{{5, 1}, {11, 1}, {2, 1}, {8, 1}, {11, 2}, {9, 1}};
   public static final int GREEN = 3800954;
   public static final int PINK = 16732088;
   public static final int GOLD = 16765773;
   private static final List<Post.Impact> IMPACTS = new ArrayList<>();
   private static final List<Post.Pulse> PULSES = new ArrayList<>();
   private static float gradeTarget;
   private static float gradeNow;
   private static float bloomTarget;
   private static float bloomNow;
   private static float vignetteTarget;
   private static float vignetteNow;
   private static int vignetteRgb;
   private static final int FLASH = 0;
   private static final int PUNCH = 1;
   private static final int ABERRATION = 2;
   private static final int DESAT = 3;
   private static final Post.Frame FRAME = new Post.Frame();
   private static long last = now();

   private Post() {
   }

   static long now() {
      return Util.getMillis();
   }

   public static void impact(int[][] seq, Vec3 at, int accent) {
      IMPACTS.add(new Post.Impact(now(), seq, at, accent));
   }

   public static void flash(int rgb, float amount, long life) {
      PULSES.add(new Post.Pulse(now(), life, 0, amount, rgb));
   }

   public static void punch(float amount, long life) {
      PULSES.add(new Post.Pulse(now(), life, 1, amount, 0));
   }

   public static void aberration(float amount, long life) {
      PULSES.add(new Post.Pulse(now(), life, 2, amount, 0));
   }

   public static void desaturate(float amount, long life) {
      PULSES.add(new Post.Pulse(now(), life, 3, amount, 0));
   }

   public static void grade(float target) {
      gradeTarget = target;
   }

   public static void bloom(float target) {
      bloomTarget = target;
   }

   public static void vignette(int rgb, float target) {
      vignetteRgb = rgb;
      vignetteTarget = target;
   }

   public static void clear() {
      IMPACTS.clear();
      PULSES.clear();
      vignetteNow = 0.0F;
      vignetteTarget = 0.0F;
      bloomNow = 0.0F;
      bloomTarget = 0.0F;
      gradeNow = 0.0F;
      gradeTarget = 0.0F;
   }

   static Post.Frame frame() {
      long t = now();
      float dt = Math.min(0.1F, (float)(t - last) / 1000.0F);
      last = t;
      float k = 1.0F - (float)Math.exp((double)(-dt * 4.0F));
      gradeNow = gradeNow + (gradeTarget - gradeNow) * k;
      bloomNow = bloomNow + (bloomTarget - bloomNow) * k;
      vignetteNow = vignetteNow + (vignetteTarget - vignetteNow) * k;
      Post.Frame f = FRAME;
      f.flash = f.punch = f.aberration = f.desaturate = 0.0F;
      f.flashR = f.flashG = f.flashB = 1.0F;
      f.impactMode = 0;
      f.impactAt = null;
      f.grade = gradeNow;
      f.bloom = bloomNow;
      f.vignette = vignetteNow;
      f.vr = (float)(vignetteRgb >> 16 & 0xFF) / 255.0F;
      f.vg = (float)(vignetteRgb >> 8 & 0xFF) / 255.0F;
      f.vb = (float)(vignetteRgb & 0xFF) / 255.0F;
      Iterator<Post.Pulse> pi = PULSES.iterator();

      while (pi.hasNext()) {
         Post.Pulse p = pi.next();
         float age = (float)(t - p.start) / (float)Math.max(1L, p.life);
         if (age >= 1.0F) {
            pi.remove();
         } else {
            float v = p.amount * (float)Math.pow((double)(1.0F - age), 2.2);
            switch (p.kind) {
               case 0:
                  if (v > f.flash) {
                     f.flash = v;
                     f.flashR = (float)(p.rgb >> 16 & 0xFF) / 255.0F;
                     f.flashG = (float)(p.rgb >> 8 & 0xFF) / 255.0F;
                     f.flashB = (float)(p.rgb & 0xFF) / 255.0F;
                  }
                  break;
               case 1:
                  f.punch = Math.max(f.punch, v);
                  break;
               case 2:
                  f.aberration = Math.max(f.aberration, v);
                  break;
               default:
                  f.desaturate = Math.max(f.desaturate, v);
            }
         }
      }

      Iterator<Post.Impact> ii = IMPACTS.iterator();

      while (ii.hasNext()) {
         Post.Impact im = ii.next();
         int tick = (int)((t - im.start) / 50L);
         int acc = 0;
         boolean live = false;

         for (int i = 0; i < im.seq.length; i++) {
            if (tick < acc + im.seq[i][1]) {
               f.impactMode = im.seq[i][0];
               f.impactSeed = (float)i * 17.31F + (float)acc + (float)(im.start % 97L);
               f.impactAt = im.at;
               f.ar = (float)(im.accent >> 16 & 0xFF) / 255.0F;
               f.ag = (float)(im.accent >> 8 & 0xFF) / 255.0F;
               f.ab = (float)(im.accent & 0xFF) / 255.0F;
               live = true;
               break;
            }

            acc += im.seq[i][1];
         }

         if (!live) {
            ii.remove();
         }
      }

      return f;
   }

   public static final class Frame {
      public float flash;
      public float flashR = 1.0F;
      public float flashG = 1.0F;
      public float flashB = 1.0F;
      public float punch;
      public float aberration;
      public float desaturate;
      public float grade;
      public float bloom;
      public float vignette;
      public float vr;
      public float vg;
      public float vb;
      public int impactMode;
      public float impactSeed;
      public Vec3 impactAt;
      public float ar = 0.22F;
      public float ag = 1.0F;
      public float ab = 0.48F;

      public boolean idle() {
         return this.flash < 0.001F
            && this.punch < 0.001F
            && this.aberration < 0.001F
            && this.desaturate < 0.001F
            && this.grade < 0.001F
            && this.bloom < 0.001F
            && this.vignette < 0.001F
            && this.impactMode == 0;
      }
   }

   private static record Impact(long start, int[][] seq, Vec3 at, int accent) {
   }

   private static record Pulse(long start, long life, int kind, float amount, int rgb) {
   }
}
