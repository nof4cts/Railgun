package com.higuruma.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class Cubes {
   static final ResourceLocation WHITE = new ResourceLocation("avatar", "textures/entity/white.png");
   static final RenderType SOLID = RenderType.entityCutoutNoCull(WHITE);
   static final RenderType GLOW = RenderType.eyes(WHITE);

   private Cubes() {
   }

   static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, int light) {
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float[][] faces = new float[][]{
         {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0.0F, 0.0F, -1.0F},
         {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0.0F, 0.0F, 1.0F},
         {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1.0F, 0.0F, 0.0F},
         {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1.0F, 0.0F, 0.0F},
         {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0.0F, -1.0F, 0.0F},
         {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0.0F, 1.0F, 0.0F}
      };
      float[] shade = new float[]{0.8F, 0.8F, 0.65F, 0.65F, 0.5F, 1.0F};
      float[][] uv = new float[][]{{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};

      for (int f = 0; f < 6; f++) {
         float[] q = faces[f];
         float s = shade[f];

         for (int i = 0; i < 4; i++) {
            vc.vertex(m, q[i * 3], q[i * 3 + 1], q[i * 3 + 2])
               .color(r * s, g * s, b * s, 1.0F)
               .uv(uv[i][0], uv[i][1])
               .overlayCoords(OverlayTexture.NO_OVERLAY)
               .uv2(light)
               .normal(n, q[12], q[13], q[14])
               .endVertex();
         }
      }
   }

   static void quad(PoseStack ps, VertexConsumer vc, Vec3[] q, float[] col, int light, float nx, float ny, float nz) {
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float[][] uv = new float[][]{{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};

      for (int i = 0; i < 4; i++) {
         vc.vertex(m, (float)q[i].x, (float)q[i].y, (float)q[i].z)
            .color(col[0], col[1], col[2], 1.0F)
            .uv(uv[i][0], uv[i][1])
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(n, nx, ny, nz)
            .endVertex();
      }
   }

   static void cone(PoseStack ps, VertexConsumer vc, Vec3 base, float r0, float r1, float height, float r, float g, float b) {
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      int sides = 16;

      for (int i = 0; i < sides; i++) {
         double a1 = (double)i * Math.PI * 2.0 / (double)sides;
         double a2 = (double)(i + 1) * Math.PI * 2.0 / (double)sides;
         float[][] pts = new float[][]{
            {(float)(base.x + Math.cos(a1) * (double)r1), (float)base.y + 0.05F, (float)(base.z + Math.sin(a1) * (double)r1), 1.0F},
            {(float)(base.x + Math.cos(a2) * (double)r1), (float)base.y + 0.05F, (float)(base.z + Math.sin(a2) * (double)r1), 1.0F},
            {(float)(base.x + Math.cos(a2) * (double)r0), (float)base.y + height, (float)(base.z + Math.sin(a2) * (double)r0), 0.35F},
            {(float)(base.x + Math.cos(a1) * (double)r0), (float)base.y + height, (float)(base.z + Math.sin(a1) * (double)r0), 0.35F}
         };

         for (float[] q : pts) {
            vc.vertex(m, q[0], q[1], q[2])
               .color(r * q[3], g * q[3], b * q[3], 1.0F)
               .uv(0.5F, 0.5F)
               .overlayCoords(OverlayTexture.NO_OVERLAY)
               .uv2(15728880)
               .normal(n, 0.0F, 1.0F, 0.0F)
               .endVertex();
         }
      }
   }

   static void cbox(PoseStack ps, VertexConsumer vc, float cx, float cy, float cz, float hx, float hy, float hz, float r, float g, float b, int light) {
      box(ps, vc, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, r, g, b, light);
   }

   static void gavel(PoseStack ps, VertexConsumer vc, float size, int light) {
      float s = size;
      cbox(ps, vc, 0.0F, 0.25F * size, 0.0F, 0.035F * size, 0.32F * size, 0.035F * size, 0.32F, 0.18F, 0.09F, light);
      cbox(ps, vc, 0.0F, -0.06F * size, 0.0F, 0.045F * size, 0.025F * size, 0.045F * size, 0.85F, 0.66F, 0.22F, light);
      cbox(ps, vc, 0.0F, 0.56F * size, 0.0F, 0.045F * size, 0.025F * size, 0.045F * size, 0.85F, 0.66F, 0.22F, light);
      float hy = 0.64F * size;
      cbox(ps, vc, 0.0F, hy, 0.0F, 0.1F * size, 0.1F * size, 0.24F * size, 0.42F, 0.24F, 0.12F, light);
      cbox(ps, vc, 0.0F, hy, 0.0F, 0.115F * size, 0.115F * size, 0.035F * size, 0.85F, 0.66F, 0.22F, light);

      for (int side = -1; side <= 1; side += 2) {
         cbox(ps, vc, 0.0F, hy, (float)side * 0.2F * s, 0.112F * s, 0.112F * s, 0.02F * s, 0.85F, 0.66F, 0.22F, light);
         cbox(ps, vc, 0.0F, hy, (float)side * 0.255F * s, 0.085F * s, 0.085F * s, 0.02F * s, 0.36F, 0.2F, 0.1F, light);
      }
   }

   static void sword(PoseStack ps, VertexConsumer vc, VertexConsumer glow, float size, int light) {
      float s = size;
      cbox(ps, vc, 0.0F, -0.17F * size, 0.0F, 0.05F * size, 0.04F * size, 0.05F * size, 0.95F, 0.78F, 0.3F, light);
      cbox(ps, vc, 0.0F, -0.01F * size, 0.0F, 0.032F * size, 0.13F * size, 0.032F * size, 0.24F, 0.14F, 0.08F, light);

      for (int k = 0; k < 4; k++) {
         cbox(ps, vc, 0.0F, (-0.1F + (float)k * 0.065F) * s, 0.0F, 0.036F * s, 0.008F * s, 0.036F * s, 0.85F, 0.68F, 0.25F, light);
      }

      cbox(ps, vc, 0.0F, 0.145F * s, 0.0F, 0.045F * s, 0.03F * s, 0.29F * s, 0.95F, 0.8F, 0.3F, light);

      for (int side = -1; side <= 1; side += 2) {
         cbox(ps, vc, 0.0F, 0.175F * s, (float)side * 0.3F * s, 0.04F * s, 0.035F * s, 0.03F * s, 0.95F, 0.8F, 0.3F, light);
      }

      cbox(ps, vc, 0.0F, 0.19F * s, 0.0F, 0.03F * s, 0.025F * s, 0.07F * s, 1.0F, 0.88F, 0.42F, light);
      int segs = 6;

      for (int k = 0; k < segs; k++) {
         float y0 = (0.2F + (float)k * 0.22F) * s;
         float y1 = (0.2F + (float)(k + 1) * 0.22F) * s;
         float w = (0.085F - (float)k * (k < segs - 1 ? 0.004F : 0.05F)) * s;
         float yc = (y0 + y1) / 2.0F;
         float hy = (y1 - y0) / 2.0F;
         cbox(ps, vc, 0.0F, yc, 0.0F, 0.016F * s, hy, w, 0.6F, 0.63F, 0.68F, light);
         if (k < segs - 1) {
            cbox(ps, vc, 0.0F, yc, 0.0F, 0.019F * s, hy, 0.016F * s, 0.38F, 0.41F, 0.47F, light);
         }

         for (int side = -1; side <= 1; side += 2) {
            cbox(ps, vc, 0.0F, yc, (float)side * (w - 0.006F * s), 0.012F * s, hy, 0.007F * s, 0.86F, 0.88F, 0.93F, light);
         }
      }

      float ty = (0.2F + (float)segs * 0.22F) * s;
      cbox(ps, vc, 0.0F, ty + 0.04F * s, 0.0F, 0.012F * s, 0.04F * s, 0.022F * s, 0.66F, 0.69F, 0.74F, light);
      cbox(ps, vc, 0.0F, ty + 0.1F * s, 0.0F, 0.008F * s, 0.025F * s, 0.009F * s, 0.8F, 0.82F, 0.88F, light);
   }
}
