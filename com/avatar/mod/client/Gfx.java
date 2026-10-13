package com.avatar.mod.client;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Gfx {
   public static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL;
   private static final Vector3f RIGHT = new Vector3f(1.0F, 0.0F, 0.0F);
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
   private static float maxDist = 1.0E9F;
   private static BufferBuilder buf;

   private Gfx() {
   }

   public static void setCamera(Matrix4f view, float pullInDistance) {
      RIGHT.set(view.m00(), view.m10(), view.m20()).normalize();
      UP.set(view.m01(), view.m11(), view.m21()).normalize();
      maxDist = pullInDistance;
   }

   public static float maxDist() {
      return maxDist;
   }

   public static void begin() {
      buf = Tesselator.getInstance().getBuilder();
      buf.begin(Mode.QUADS, FORMAT);
   }

   public static void v(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
      float d = Mth.sqrt(x * x + y * y + z * z);
      if (d > maxDist) {
         float k = maxDist / d;
         x *= k;
         y *= k;
         z *= k;
      }

      buf.vertex((double)x, (double)y, (double)z).uv(u, v).color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
   }

   public static void billboard(double x, double y, double z, float half, float rot, float r, float g, float b, float a) {
      float c = Mth.cos(rot) * half;
      float s = Mth.sin(rot) * half;
      float ax = RIGHT.x * c + UP.x * s;
      float ay = RIGHT.y * c + UP.y * s;
      float az = RIGHT.z * c + UP.z * s;
      float bx = -RIGHT.x * s + UP.x * c;
      float by = -RIGHT.y * s + UP.y * c;
      float bz = -RIGHT.z * s + UP.z * c;
      float px = (float)x;
      float py = (float)y;
      float pz = (float)z;
      v(px - ax - bx, py - ay - by, pz - az - bz, 0.0F, 0.0F, r, g, b, a);
      v(px + ax - bx, py + ay - by, pz + az - bz, 1.0F, 0.0F, r, g, b, a);
      v(px + ax + bx, py + ay + by, pz + az + bz, 1.0F, 1.0F, r, g, b, a);
      v(px - ax + bx, py - ay + by, pz - az + bz, 0.0F, 1.0F, r, g, b, a);
   }

   public static void upright(double x, double y, double z, float halfWidth, float height, float r, float g, float b, float a) {
      float hx = (float)(-z);
      float hz = (float)x;
      float len = Mth.sqrt(hx * hx + hz * hz);
      if (len < 1.0E-4F) {
         hx = 1.0F;
         hz = 0.0F;
         len = 1.0F;
      }

      hx = hx / len * halfWidth;
      hz = hz / len * halfWidth;
      float px = (float)x;
      float py = (float)y;
      float pz = (float)z;
      v(px - hx, py, pz - hz, 0.0F, 0.0F, r, g, b, a);
      v(px + hx, py, pz + hz, 1.0F, 0.0F, r, g, b, a);
      v(px + hx, py + height, pz + hz, 1.0F, 1.0F, r, g, b, a);
      v(px - hx, py + height, pz - hz, 0.0F, 1.0F, r, g, b, a);
   }

   public static void ground(double x, double y, double z, float half, float r, float g, float b, float a) {
      float px = (float)x;
      float py = (float)y;
      float pz = (float)z;
      v(px - half, py, pz - half, 0.0F, 0.0F, r, g, b, a);
      v(px + half, py, pz - half, 1.0F, 0.0F, r, g, b, a);
      v(px + half, py, pz + half, 1.0F, 1.0F, r, g, b, a);
      v(px - half, py, pz + half, 0.0F, 1.0F, r, g, b, a);
   }

   public static void ribbon(float[] pts, float[] w, float[] u, float r, float g, float b, float a) {
      int n = pts.length / 3;
      float[] side = new float[n * 3];

      for (int i = 0; i < n; i++) {
         int i0 = Math.max(0, i - 1) * 3;
         int i1 = Math.min(n - 1, i + 1) * 3;
         float tx = pts[i1] - pts[i0];
         float ty = pts[i1 + 1] - pts[i0 + 1];
         float tz = pts[i1 + 2] - pts[i0 + 2];
         float px = pts[i * 3];
         float py = pts[i * 3 + 1];
         float pz = pts[i * 3 + 2];
         float sx = ty * pz - tz * py;
         float sy = tz * px - tx * pz;
         float sz = tx * py - ty * px;
         float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
         if (len < 1.0E-5F) {
            sx = RIGHT.x;
            sy = RIGHT.y;
            sz = RIGHT.z;
            len = 1.0F;
         }

         side[i * 3] = sx / len * w[i];
         side[i * 3 + 1] = sy / len * w[i];
         side[i * 3 + 2] = sz / len * w[i];
      }

      for (int i = 0; i < n - 1; i++) {
         int a0 = i * 3;
         int a1 = (i + 1) * 3;
         v(pts[a0] - side[a0], pts[a0 + 1] - side[a0 + 1], pts[a0 + 2] - side[a0 + 2], u[i], 0.0F, r, g, b, a);
         v(pts[a0] + side[a0], pts[a0 + 1] + side[a0 + 1], pts[a0 + 2] + side[a0 + 2], u[i], 1.0F, r, g, b, a);
         v(pts[a1] + side[a1], pts[a1 + 1] + side[a1 + 1], pts[a1 + 2] + side[a1 + 2], u[i + 1], 1.0F, r, g, b, a);
         v(pts[a1] - side[a1], pts[a1 + 1] - side[a1 + 1], pts[a1 + 2] - side[a1 + 2], u[i + 1], 0.0F, r, g, b, a);
      }
   }

   public static void skyBox(float s) {
      float[][] f = new float[][]{
         {-s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s},
         {-s, -s, s, -s, s, s, s, s, s, s, -s, s},
         {-s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s},
         {s, -s, -s, s, -s, s, s, s, s, s, s, -s},
         {-s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s},
         {-s, s, -s, s, s, -s, s, s, s, -s, s, s}
      };

      for (float[] q : f) {
         for (int i = 0; i < 4; i++) {
            buf.vertex((double)q[i * 3], (double)q[i * 3 + 1], (double)q[i * 3 + 2])
               .uv(0.0F, 0.0F)
               .color(1.0F, 1.0F, 1.0F, 1.0F)
               .normal(0.0F, 1.0F, 0.0F)
               .endVertex();
         }
      }
   }

   public static void flush(ShaderInstance sh, Matrix4f modelView, Matrix4f proj, boolean depthTest) {
      RenderedBuffer rb = buf.end();
      if (rb.isEmpty()) {
         rb.release();
      } else {
         matrices(sh, modelView, proj);
         sh.apply();
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
         RenderSystem.depthMask(false);
         if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
         } else {
            RenderSystem.disableDepthTest();
         }

         RenderSystem.disableCull();
         BufferUploader.draw(rb);
         sh.clear();
         restore();
      }
   }

   public static void flushEmpty() {
      buf.end().release();
   }

   public static void flushOpaque(ShaderInstance sh, Matrix4f modelView, Matrix4f proj) {
      RenderedBuffer rb = buf.end();
      matrices(sh, modelView, proj);
      sh.apply();
      RenderSystem.disableBlend();
      RenderSystem.depthMask(false);
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      BufferUploader.draw(rb);
      sh.clear();
      restore();
   }

   public static void mesh(VertexBuffer vb, ShaderInstance sh, Matrix4f modelView, Matrix4f proj) {
      matrices(sh, modelView, proj);
      sh.apply();
      RenderSystem.disableBlend();
      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(515);
      RenderSystem.disableCull();
      vb.bind();
      vb.draw();
      VertexBuffer.unbind();
      sh.clear();
      restore();
   }

   public static void meshBlend(VertexBuffer vb, ShaderInstance sh, Matrix4f modelView, Matrix4f proj) {
      matrices(sh, modelView, proj);
      sh.apply();
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
      RenderSystem.depthMask(false);
      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(515);
      RenderSystem.disableCull();
      vb.bind();
      vb.draw();
      VertexBuffer.unbind();
      sh.clear();
      restore();
   }

   private static void restore() {
      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(515);
      RenderSystem.enableCull();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
   }

   private static void matrices(ShaderInstance sh, Matrix4f modelView, Matrix4f proj) {
      sh.safeGetUniform("ModelViewMat").set(modelView);
      sh.safeGetUniform("ProjMat").set(proj);
   }

   public static void f(ShaderInstance sh, String name, float v) {
      sh.safeGetUniform(name).set(v);
   }

   public static void v3(ShaderInstance sh, String name, float x, float y, float z) {
      sh.safeGetUniform(name).set(x, y, z);
   }

   public static void v3(ShaderInstance sh, String name, Vector3f v) {
      sh.safeGetUniform(name).set(v.x, v.y, v.z);
   }
}
