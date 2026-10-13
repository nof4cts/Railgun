package com.ryu.mod.fx.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class FxDraw {
   private static final float TWO_PI = (float) (Math.PI * 2);
   private static float leftX;
   private static float leftY;
   private static float leftZ;
   private static float upX;
   private static float upY;
   private static float upZ;

   private FxDraw() {
   }

   public static void setCamera(Camera camera) {
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      leftX = left.x();
      leftY = left.y();
      leftZ = left.z();
      upX = up.x();
      upY = up.y();
      upZ = up.z();
   }

   public static float red(int rgb) {
      return (float)(rgb >> 16 & 0xFF) / 255.0F;
   }

   public static float green(int rgb) {
      return (float)(rgb >> 8 & 0xFF) / 255.0F;
   }

   public static float blue(int rgb) {
      return (float)(rgb & 0xFF) / 255.0F;
   }

   public static void billboard(VertexConsumer vc, Matrix4f m, float x, float y, float z, float size, int rgb, float alpha, float rotation) {
      if (!(alpha <= 0.003F) && !(size <= 0.0F)) {
         float dist = Mth.sqrt(x * x + y * y + z * z);
         if (size > dist * 2.0F) {
            alpha *= Math.max(0.15F, dist * 2.0F / size);
         }

         float half = size * 0.5F;
         float c = Mth.cos(rotation) * half;
         float s = Mth.sin(rotation) * half;
         float ax = leftX * c + upX * s;
         float ay = leftY * c + upY * s;
         float az = leftZ * c + upZ * s;
         float bx = -leftX * s + upX * c;
         float by = -leftY * s + upY * c;
         float bz = -leftZ * s + upZ * c;
         float r = red(rgb);
         float g = green(rgb);
         float b = blue(rgb);
         float a = Math.min(1.0F, alpha);
         vc.vertex(m, x - ax - bx, y - ay - by, z - az - bz).color(r, g, b, a).uv(0.0F, 1.0F).endVertex();
         vc.vertex(m, x - ax + bx, y - ay + by, z - az + bz).color(r, g, b, a).uv(0.0F, 0.0F).endVertex();
         vc.vertex(m, x + ax + bx, y + ay + by, z + az + bz).color(r, g, b, a).uv(1.0F, 0.0F).endVertex();
         vc.vertex(m, x + ax - bx, y + ay - by, z + az - bz).color(r, g, b, a).uv(1.0F, 1.0F).endVertex();
      }
   }

   public static void beam(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float width, int rgb, float alpha) {
      if (!(alpha <= 0.003F) && !(width <= 0.0F)) {
         float dx = bx - ax;
         float dy = by - ay;
         float dz = bz - az;
         float mx = (ax + bx) * 0.5F;
         float my = (ay + by) * 0.5F;
         float mz = (az + bz) * 0.5F;
         float sx = dy * mz - dz * my;
         float sy = dz * mx - dx * mz;
         float sz = dx * my - dy * mx;
         float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(len < 1.0E-5F)) {
            float k = width * 0.5F / len;
            sx *= k;
            sy *= k;
            sz *= k;
            float r = red(rgb);
            float g = green(rgb);
            float b = blue(rgb);
            float a = Math.min(1.0F, alpha);
            vc.vertex(m, ax - sx, ay - sy, az - sz).color(r, g, b, a).uv(0.0F, 0.0F).endVertex();
            vc.vertex(m, ax + sx, ay + sy, az + sz).color(r, g, b, a).uv(1.0F, 0.0F).endVertex();
            vc.vertex(m, bx + sx, by + sy, bz + sz).color(r, g, b, a).uv(1.0F, 1.0F).endVertex();
            vc.vertex(m, bx - sx, by - sy, bz - sz).color(r, g, b, a).uv(0.0F, 1.0F).endVertex();
         }
      }
   }

   public static void ring(
      VertexConsumer vc,
      Matrix4f m,
      float cx,
      float cy,
      float cz,
      float nx,
      float ny,
      float nz,
      float radius,
      float thickness,
      int rgb,
      float alpha,
      int segments
   ) {
      if (!(alpha <= 0.003F) && !(radius <= 0.0F)) {
         float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
         if (nl < 1.0E-5F) {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
         } else {
            nx /= nl;
            ny /= nl;
            nz /= nl;
         }

         float tx = Math.abs(ny) < 0.9F ? 0.0F : 1.0F;
         float ty = Math.abs(ny) < 0.9F ? 1.0F : 0.0F;
         float e1x = ny * 0.0F - nz * ty;
         float e1y = nz * tx - nx * 0.0F;
         float e1z = nx * ty - ny * tx;
         float e1l = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
         e1x /= e1l;
         e1y /= e1l;
         e1z /= e1l;
         float e2x = ny * e1z - nz * e1y;
         float e2y = nz * e1x - nx * e1z;
         float e2z = nx * e1y - ny * e1x;
         float inner = Math.max(0.0F, radius - thickness * 0.5F);
         float outer = radius + thickness * 0.5F;
         float r = red(rgb);
         float g = green(rgb);
         float b = blue(rgb);
         float a = Math.min(1.0F, alpha);

         for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2) * (float)i / (float)segments;
            float a1 = (float) (Math.PI * 2) * (float)(i + 1) / (float)segments;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float d0x = e1x * c0 + e2x * s0;
            float d0y = e1y * c0 + e2y * s0;
            float d0z = e1z * c0 + e2z * s0;
            float d1x = e1x * c1 + e2x * s1;
            float d1y = e1y * c1 + e2y * s1;
            float d1z = e1z * c1 + e2z * s1;
            vc.vertex(m, cx + d0x * inner, cy + d0y * inner, cz + d0z * inner).color(r, g, b, a).uv(0.0F, 0.0F).endVertex();
            vc.vertex(m, cx + d0x * outer, cy + d0y * outer, cz + d0z * outer).color(r, g, b, a).uv(1.0F, 0.0F).endVertex();
            vc.vertex(m, cx + d1x * outer, cy + d1y * outer, cz + d1z * outer).color(r, g, b, a).uv(1.0F, 1.0F).endVertex();
            vc.vertex(m, cx + d1x * inner, cy + d1y * inner, cz + d1z * inner).color(r, g, b, a).uv(0.0F, 1.0F).endVertex();
         }
      }
   }

   public static void sphere(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float radius, int rgb, float alpha, int stacks, int slices) {
      if (!(alpha <= 0.003F) && !(radius <= 0.0F)) {
         float r = red(rgb);
         float g = green(rgb);
         float b = blue(rgb);
         float a = Math.min(1.0F, alpha);

         for (int i = 0; i < stacks; i++) {
            float p0 = (float) Math.PI * (float)i / (float)stacks;
            float p1 = (float) Math.PI * (float)(i + 1) / (float)stacks;
            float y0 = Mth.cos(p0) * radius;
            float r0 = Mth.sin(p0) * radius;
            float y1 = Mth.cos(p1) * radius;
            float r1 = Mth.sin(p1) * radius;

            for (int j = 0; j < slices; j++) {
               float t0 = (float) (Math.PI * 2) * (float)j / (float)slices;
               float t1 = (float) (Math.PI * 2) * (float)(j + 1) / (float)slices;
               float c0 = Mth.cos(t0);
               float s0 = Mth.sin(t0);
               float c1 = Mth.cos(t1);
               float s1 = Mth.sin(t1);
               vc.vertex(m, cx + r0 * c0, cy + y0, cz + r0 * s0).color(r, g, b, a).endVertex();
               vc.vertex(m, cx + r1 * c0, cy + y1, cz + r1 * s0).color(r, g, b, a).endVertex();
               vc.vertex(m, cx + r1 * c1, cy + y1, cz + r1 * s1).color(r, g, b, a).endVertex();
               vc.vertex(m, cx + r0 * c1, cy + y0, cz + r0 * s1).color(r, g, b, a).endVertex();
            }
         }
      }
   }

   public static int arc(long seed, float ax, float ay, float az, float bx, float by, float bz, int segments, float jitter, float[] out) {
      segments = Math.min(segments, out.length / 3 - 1);
      long s = seed * -7046029254386353131L + 7146057691288625177L;

      for (int i = 0; i <= segments; i++) {
         float t = (float)i / (float)segments;
         float px = ax + (bx - ax) * t;
         float py = ay + (by - ay) * t;
         float pz = az + (bz - az) * t;
         if (i > 0 && i < segments) {
            float taper = Mth.sin(t * (float) Math.PI) * jitter;
            long var20 = next(s);
            px += (unit(var20) - 0.5F) * 2.0F * taper;
            long var21 = next(var20);
            py += (unit(var21) - 0.5F) * 2.0F * taper;
            s = next(var21);
            pz += (unit(s) - 0.5F) * 2.0F * taper;
         }

         out[i * 3] = px;
         out[i * 3 + 1] = py;
         out[i * 3 + 2] = pz;
      }

      return segments + 1;
   }

   public static void polyline(VertexConsumer vc, Matrix4f m, float[] points, int count, float width, int rgb, float alpha) {
      for (int i = 0; i < count - 1; i++) {
         int j = i * 3;
         beam(vc, m, points[j], points[j + 1], points[j + 2], points[j + 3], points[j + 4], points[j + 5], width, rgb, alpha);
      }
   }

   public static float hash(long seed) {
      return unit(next(next(seed * -7046029254386353131L + 2685821657736338717L)));
   }

   private static long next(long s) {
      s ^= s << 13;
      s ^= s >>> 7;
      return s ^ s << 17;
   }

   private static float unit(long s) {
      return (float)(s >>> 40 & 16777215L) / 1.6777216E7F;
   }
}
