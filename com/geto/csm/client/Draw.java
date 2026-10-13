package com.geto.csm.client;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Draw {
   public static final Vec3 X = new Vec3(1.0, 0.0, 0.0);
   public static final Vec3 Y = new Vec3(0.0, 1.0, 0.0);
   public static final Vec3 Z = new Vec3(0.0, 0.0, 1.0);

   public static int argb(int a, int r, int g, int b) {
      return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
   }

   public static int alpha(int c, float a) {
      int na = Mth.clamp((int)((float)(c >>> 24 & 0xFF) * a), 0, 255);
      return c & 16777215 | na << 24;
   }

   public static int lerp(int c1, int c2, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int a = (int)Mth.lerp(t, (float)(c1 >>> 24 & 0xFF), (float)(c2 >>> 24 & 0xFF));
      int r = (int)Mth.lerp(t, (float)(c1 >> 16 & 0xFF), (float)(c2 >> 16 & 0xFF));
      int g = (int)Mth.lerp(t, (float)(c1 >> 8 & 0xFF), (float)(c2 >> 8 & 0xFF));
      int b = (int)Mth.lerp(t, (float)(c1 & 0xFF), (float)(c2 & 0xFF));
      return argb(a, r, g, b);
   }

   public static int scaleRgb(int c, float k) {
      int r = Mth.clamp((int)((float)(c >> 16 & 0xFF) * k), 0, 255);
      int g = Mth.clamp((int)((float)(c >> 8 & 0xFF) * k), 0, 255);
      int b = Mth.clamp((int)((float)(c & 0xFF) * k), 0, 255);
      return c & 0xFF000000 | r << 16 | g << 8 | b;
   }

   public static int hsv(float h, float s, float v) {
      return 0xFF000000 | Mth.hsvToRgb((h % 1.0F + 1.0F) % 1.0F, s, v);
   }

   public static BufferBuilder begin(Mode mode) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
      return b;
   }

   public static void end(BufferBuilder b) {
      BufferUploader.drawWithShader(b.end());
   }

   public static void additive() {
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
   }

   public static void translucent() {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
   }

   public static void worldPassBegin(boolean glow) {
      if (glow) {
         additive();
      } else {
         translucent();
      }

      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(!glow);
   }

   public static void worldPassEnd() {
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
   }

   public static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, int c) {
      vc.vertex(m, (float)x, (float)y, (float)z).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24 & 0xFF).endVertex();
   }

   public static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int c) {
      v(vc, m, p.x, p.y, p.z, c);
   }

   public static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
      v(vc, m, a, ca);
      v(vc, m, b, cb);
      v(vc, m, c, cc);
      v(vc, m, d, cd);
   }

   public static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col) {
      quad(vc, m, a, b, c, d, col, col, col, col);
   }

   public static void tri(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, int ca, int cb, int cc) {
      quad(vc, m, a, b, c, c, ca, cb, cc, cc);
   }

   public static void sphere(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, int lat, int lon, Draw.ColorFn fn) {
      for (int i = 0; i < lat; i++) {
         double t0 = Math.PI * (double)i / (double)lat;
         double t1 = Math.PI * (double)(i + 1) / (double)lat;
         double y0 = Math.cos(t0);
         double y1 = Math.cos(t1);
         double s0 = Math.sin(t0);
         double s1 = Math.sin(t1);

         for (int j = 0; j < lon; j++) {
            double p0 = (Math.PI * 2) * (double)j / (double)lon;
            double p1 = (Math.PI * 2) * (double)(j + 1) / (double)lon;
            double c0 = Math.cos(p0);
            double n0 = Math.sin(p0);
            double c1 = Math.cos(p1);
            double n1 = Math.sin(p1);
            double ax = s0 * c0;
            double az = s0 * n0;
            double bx = s1 * c0;
            double bz = s1 * n0;
            double cxx = s1 * c1;
            double czz = s1 * n1;
            double dx = s0 * c1;
            double dz = s0 * n1;
            v(vc, m, cx + ax * r, cy + y0 * r, cz + az * r, fn.at(ax, y0, az));
            v(vc, m, cx + bx * r, cy + y1 * r, cz + bz * r, fn.at(bx, y1, bz));
            v(vc, m, cx + cxx * r, cy + y1 * r, cz + czz * r, fn.at(cxx, y1, czz));
            v(vc, m, cx + dx * r, cy + y0 * r, cz + dz * r, fn.at(dx, y0, dz));
         }
      }
   }

   public static void sphere(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, int lat, int lon, int col) {
      sphere(vc, m, cx, cy, cz, r, lat, lon, (x, y, z) -> col);
   }

   public static void glowOrb(VertexConsumer vc, Matrix4f m, double x, double y, double z, double r, int col, float a) {
      sphere(vc, m, x, y, z, r * 0.45, 8, 12, alpha(-1, a));
      sphere(vc, m, x, y, z, r, 8, 12, alpha(col, a * 0.55F));
      sphere(vc, m, x, y, z, r * 1.7, 8, 12, alpha(col, a * 0.18F));
   }

   public static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double rIn, double rOut, int cIn, int cOut, int segs) {
      for (int i = 0; i < segs; i++) {
         double a0 = (Math.PI * 2) * (double)i / (double)segs;
         double a1 = (Math.PI * 2) * (double)(i + 1) / (double)segs;
         Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
         Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
         quad(vc, m, c.add(d0.scale(rIn)), c.add(d0.scale(rOut)), c.add(d1.scale(rOut)), c.add(d1.scale(rIn)), cIn, cOut, cOut, cIn);
      }
   }

   public static void ringXZ(VertexConsumer vc, Matrix4f m, Vec3 c, double rIn, double rOut, int cIn, int cOut, int segs) {
      ring(vc, m, c, X, Z, rIn, rOut, cIn, cOut, segs);
   }

   public static void box(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int c) {
      int bot = scaleRgb(c, 0.5F);
      int ns = scaleRgb(c, 0.8F);
      int ew = scaleRgb(c, 0.66F);
      v(vc, m, x0, y1, z0, c);
      v(vc, m, x0, y1, z1, c);
      v(vc, m, x1, y1, z1, c);
      v(vc, m, x1, y1, z0, c);
      v(vc, m, x0, y0, z0, bot);
      v(vc, m, x1, y0, z0, bot);
      v(vc, m, x1, y0, z1, bot);
      v(vc, m, x0, y0, z1, bot);
      v(vc, m, x0, y0, z0, ns);
      v(vc, m, x0, y1, z0, ns);
      v(vc, m, x1, y1, z0, ns);
      v(vc, m, x1, y0, z0, ns);
      v(vc, m, x0, y0, z1, ns);
      v(vc, m, x1, y0, z1, ns);
      v(vc, m, x1, y1, z1, ns);
      v(vc, m, x0, y1, z1, ns);
      v(vc, m, x0, y0, z0, ew);
      v(vc, m, x0, y0, z1, ew);
      v(vc, m, x0, y1, z1, ew);
      v(vc, m, x0, y1, z0, ew);
      v(vc, m, x1, y0, z0, ew);
      v(vc, m, x1, y1, z0, ew);
      v(vc, m, x1, y1, z1, ew);
      v(vc, m, x1, y0, z1, ew);
   }

   public static void cube(VertexConsumer vc, Matrix4f m, double s, int c) {
      box(vc, m, -s, -s, -s, s, s, s, c);
   }

   public static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double wa, double wb, int ca, int cb, Vec3 cam) {
      Vec3 dir = b.subtract(a);
      Vec3 mid = a.add(b).scale(0.5);
      Vec3 side = dir.cross(cam.subtract(mid));
      double len = side.length();
      if (!(len < 1.0E-6)) {
         side = side.scale(1.0 / len);
         quad(vc, m, a.add(side.scale(wa)), b.add(side.scale(wb)), b.subtract(side.scale(wb)), a.subtract(side.scale(wa)), ca, cb, cb, ca);
      }
   }

   public static void billboard(VertexConsumer vc, Matrix4f m, Vec3 p, double size, int c, Vector3f left, Vector3f up) {
      Vec3 l = new Vec3((double)left.x(), (double)left.y(), (double)left.z()).scale(size);
      Vec3 u = new Vec3((double)up.x(), (double)up.y(), (double)up.z()).scale(size);
      quad(vc, m, p.add(l).add(u), p.subtract(l).add(u), p.subtract(l).subtract(u), p.add(l).subtract(u), c);
   }

   public static void beam(VertexConsumer vc, Matrix4f m, Vec3 base, double h, double r, int cBot, int cTop, int segs) {
      for (int i = 0; i < segs; i++) {
         double a0 = (Math.PI * 2) * (double)i / (double)segs;
         double a1 = (Math.PI * 2) * (double)(i + 1) / (double)segs;
         Vec3 p0 = base.add(Math.cos(a0) * r, 0.0, Math.sin(a0) * r);
         Vec3 p1 = base.add(Math.cos(a1) * r, 0.0, Math.sin(a1) * r);
         quad(vc, m, p0, p1, p1.add(0.0, h, 0.0), p0.add(0.0, h, 0.0), cBot, cBot, cTop, cTop);
      }
   }

   public static void cone(VertexConsumer vc, Matrix4f m, Vec3 apex, Vec3 dir, double len, double r, int cApex, int cRim, int segs) {
      Vec3 d = dir.normalize();
      Vec3 u = perp(d);
      Vec3 w = d.cross(u);
      Vec3 base = apex.add(d.scale(len));

      for (int i = 0; i < segs; i++) {
         double a0 = (Math.PI * 2) * (double)i / (double)segs;
         double a1 = (Math.PI * 2) * (double)(i + 1) / (double)segs;
         Vec3 p0 = base.add(u.scale(Math.cos(a0) * r)).add(w.scale(Math.sin(a0) * r));
         Vec3 p1 = base.add(u.scale(Math.cos(a1) * r)).add(w.scale(Math.sin(a1) * r));
         tri(vc, m, apex, p0, p1, cApex, cRim, cRim);
      }
   }

   public static Vec3 perp(Vec3 d) {
      Vec3 a = Math.abs(d.y) < 0.9 ? Y : X;
      return d.cross(a).normalize();
   }

   public static Vec3 forward(float yawDeg, float pitchDeg) {
      float y = yawDeg * (float) (Math.PI / 180.0);
      float p = pitchDeg * (float) (Math.PI / 180.0);
      return new Vec3((double)(-Mth.sin(y) * Mth.cos(p)), (double)(-Mth.sin(p)), (double)(Mth.cos(y) * Mth.cos(p)));
   }

   public static double hash(long seed) {
      long x = seed * -7046029254386353131L;
      x ^= x >>> 31;
      x *= -4658895280553007687L;
      x ^= x >>> 27;
      return (double)(x >>> 11 & 9007199254740991L) / 9.007199E15F;
   }

   public static Vec3 randDir(long seed) {
      double u = hash(seed) * 2.0 - 1.0;
      double th = hash(seed + 7919L) * Math.PI * 2.0;
      double s = Math.sqrt(1.0 - u * u);
      return new Vec3(s * Math.cos(th), u, s * Math.sin(th));
   }

   private Draw() {
   }

   public interface ColorFn {
      int at(double var1, double var3, double var5);
   }
}
