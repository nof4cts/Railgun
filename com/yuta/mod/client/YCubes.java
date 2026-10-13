package com.yuta.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class YCubes {
   static final ResourceLocation WHITE = new ResourceLocation("avatar", "textures/entity/white.png");
   static final RenderType SOLID = RenderType.entityCutoutNoCull(WHITE);

   private YCubes() {
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
      float[] shade = new float[]{0.85F, 0.85F, 0.7F, 0.7F, 1.0F, 0.55F};
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

   static void px(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, int light) {
      float k = 0.0625F;
      box(ps, vc, x0 * k, y0 * k, z0 * k, x1 * k, y1 * k, z1 * k, r, g, b, light);
   }
}
