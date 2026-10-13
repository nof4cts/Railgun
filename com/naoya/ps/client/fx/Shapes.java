package com.naoya.ps.client.fx;

import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class Shapes {
   private Shapes() {
   }

   public static Vec3 rotateY(Vec3 v, double degrees) {
      double a = Math.toRadians(degrees);
      double c = Math.cos(a);
      double s = Math.sin(a);
      return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
   }

   public static Vec3 rotate(Vec3 v, Vec3 k, double radians) {
      double c = Math.cos(radians);
      double s = Math.sin(radians);
      return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1.0 - c)));
   }

   public static void crescent(
      List<Ribbon> out, Vec3 centre, Vec3 u, Vec3 v, double radius, double a0, double a1, float width, float reveal, float fade, int rgb, Ribbon.Style style
   ) {
      if (!(reveal <= 0.0F) && !(fade <= 0.0F)) {
         int n = 28;
         Vec3[] spine = new Vec3[n];
         Vec3[] sides = new Vec3[n];
         float[] w = new float[n];
         float[] a = new float[n];
         double end = a0 + (a1 - a0) * (double)Math.min(1.0F, reveal);

         for (int i = 0; i < n; i++) {
            double k = (double)i / (double)(n - 1);
            double ang = Math.toRadians(a0 + (end - a0) * k);
            Vec3 radial = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            spine[i] = centre.add(radial.scale(radius));
            sides[i] = radial;
            float taper = (float)Math.sin(Math.PI * k);
            float head = reveal < 1.0F ? (float)Math.pow(k, 0.6) : 1.0F;
            w[i] = width * (0.15F + 0.85F * taper);
            a[i] = fade * Math.min(1.0F, 0.25F + head) * (0.35F + 0.65F * taper);
         }

         out.add(new Ribbon(spine, sides, w, a, rgb, style));
      }
   }

   public static void streak(List<Ribbon> out, Vec3 a, Vec3 b, float width, float alpha, int rgb, Ribbon.Style style) {
      if (!(alpha <= 0.0F)) {
         int n = 10;
         Vec3[] spine = new Vec3[n];
         float[] w = new float[n];
         float[] al = new float[n];

         for (int i = 0; i < n; i++) {
            float k = (float)i / (float)(n - 1);
            spine[i] = a.lerp(b, (double)k);
            float shape = (float)Math.sin(Math.PI * Math.pow((double)k, 0.75));
            w[i] = width * shape;
            al[i] = alpha * shape;
         }

         out.add(new Ribbon(spine, (Vec3[])null, w, al, rgb, style));
      }
   }

   public static void helix(
      List<Ribbon> out, Vec3 base, double r0, double r1, double height, double turns, double phase, float width, float alpha, int rgb, Ribbon.Style style
   ) {
      if (!(alpha <= 0.0F)) {
         int n = Math.max(12, (int)(turns * 22.0));
         Vec3[] spine = new Vec3[n];
         float[] w = new float[n];
         float[] a = new float[n];

         for (int i = 0; i < n; i++) {
            double k = (double)i / (double)(n - 1);
            double ang = phase + k * turns * Math.PI * 2.0;
            double r = Mth.lerp(k, r0, r1);
            spine[i] = base.add(Math.cos(ang) * r, k * height, Math.sin(ang) * r);
            float taper = (float)Math.sin(Math.PI * k);
            w[i] = width * (0.3F + 0.7F * taper);
            a[i] = alpha * taper;
         }

         out.add(new Ribbon(spine, (Vec3[])null, w, a, rgb, style));
      }
   }

   public static void ring(List<Ribbon> out, Vec3 centre, Vec3 n, double radius, float width, float alpha, int rgb, Ribbon.Style style) {
      if (!(alpha <= 0.0F) && !(radius <= 0.0)) {
         Vec3 nn = n.normalize();
         Vec3 u = Math.abs(nn.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0).cross(nn).normalize() : new Vec3(0.0, 1.0, 0.0).cross(nn).normalize();
         Vec3 v = nn.cross(u);
         int segs = 48;
         Vec3[] spine = new Vec3[segs + 1];
         Vec3[] sides = new Vec3[segs + 1];
         float[] w = new float[segs + 1];
         float[] a = new float[segs + 1];

         for (int i = 0; i <= segs; i++) {
            double ang = (double)i / (double)segs * Math.PI * 2.0;
            Vec3 radial = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            spine[i] = centre.add(radial.scale(radius));
            sides[i] = radial;
            w[i] = width;
            a[i] = alpha;
         }

         out.add(new Ribbon(spine, sides, w, a, rgb, style));
      }
   }

   public static void bolt(List<Ribbon> out, Vec3 a, Vec3 b, long seed, double jag, float width, float alpha, int rgb, Ribbon.Style style) {
      if (!(alpha <= 0.0F)) {
         int n = 12;
         Vec3[] spine = new Vec3[n];
         float[] w = new float[n];
         float[] al = new float[n];
         Vec3 dir = b.subtract(a);
         Vec3 side1 = dir.cross(new Vec3(0.0, 1.0, 0.0));
         if (side1.lengthSqr() < 1.0E-6) {
            side1 = new Vec3(1.0, 0.0, 0.0);
         }

         side1 = side1.normalize();
         Vec3 side2 = dir.cross(side1).normalize();
         Random r = new Random(seed);

         for (int i = 0; i < n; i++) {
            double k = (double)i / (double)(n - 1);
            double env = Math.sin(Math.PI * k);
            spine[i] = a.add(dir.scale(k)).add(side1.scale((r.nextDouble() - 0.5) * jag * env)).add(side2.scale((r.nextDouble() - 0.5) * jag * env));
            w[i] = width * (float)(0.4 + 0.6 * (1.0 - k));
            al[i] = alpha;
         }

         out.add(new Ribbon(spine, (Vec3[])null, w, al, rgb, style));
      }
   }

   public static void blade(List<Ribbon> out, Vec3 centre, Vec3 u, Vec3 v, double radius, double a0, double a1, float width, float reveal, float fade, int rgb) {
      crescent(out, centre, u, v, radius, a0, a1, width * 2.6F, reveal, fade * 0.35F, rgb, Ribbon.Style.GLOW);
      crescent(out, centre, u, v, radius, a0, a1, width, reveal, fade, rgb, Ribbon.Style.GLOW);
      crescent(out, centre, u, v, radius, a0, a1, width * 0.35F, reveal, fade, 16777215, Ribbon.Style.GLOW);
   }

   public static void hakiBlade(
      List<Ribbon> out, Vec3 centre, Vec3 u, Vec3 v, double radius, double a0, double a1, float width, float reveal, float fade, int halo
   ) {
      crescent(out, centre, u, v, radius, a0, a1, width * 3.0F, reveal, fade * 0.45F, halo, Ribbon.Style.GLOW);
      crescent(out, centre, u, v, radius, a0, a1, width, reveal, fade * 0.95F, 327688, Ribbon.Style.INK);
      crescent(out, centre, u, v, radius, a0, a1, width * 0.18F, reveal, fade * 0.8F, 16777215, Ribbon.Style.GLOW);
   }
}
