package com.hakari.idg.client;

import com.hakari.idg.ReserveBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class ReserveBallRenderer extends EntityRenderer<ReserveBallEntity> {
   public ReserveBallRenderer(Context ctx) {
      super(ctx);
   }

   public void render(ReserveBallEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = ps.last().pose();
      double cy = 0.175;
      Vec3 v = e.getDeltaMovement();

      for (int i = 9; i >= 1; i--) {
         float k = 1.0F - (float)i / 10.0F;
         Vec3 o = v.scale((double)(-i) * 0.3);
         Draw.sphere(vc, m, o.x, o.y + cy, o.z, 0.16 * (double)k, 6, 8, Draw.alpha(-45128, 0.45F * k));
      }

      Draw.sphere(vc, m, 0.0, cy, 0.0, 0.14, 8, 12, (nx, ny, nz) -> Draw.lerp(-6380880, -1, (float)(ny * 0.5 + 0.5)));
      Draw.sphere(vc, m, 0.0, cy, 0.0, 0.26, 8, 12, Draw.alpha(-45128, 0.35F));
      Draw.sphere(vc, m, 0.0, cy, 0.0, 0.5, 8, 12, Draw.alpha(-53894, 0.1F));
      super.render(e, yaw, partial, ps, buffers, light);
   }

   public ResourceLocation getTextureLocation(ReserveBallEntity e) {
      return TextureAtlas.LOCATION_BLOCKS;
   }
}
