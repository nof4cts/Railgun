package com.kashimo.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class AmberLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final ResourceLocation WHITE = new ResourceLocation("kashimo", "textures/entity/white.png");

   AmberLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   public void render(
      PoseStack ps,
      MultiBufferSource buf,
      int light,
      AbstractClientPlayer p,
      float limb,
      float limbAmt,
      float partial,
      float age,
      float headYaw,
      float headPitch
   ) {
      if (KFx.amber(p) && !p.isInvisible()) {
         ps.pushPose();
         ((PlayerModel)this.getParentModel()).head.translateAndRotate(ps);
         VertexConsumer solid = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));

         for (int side = -1; side <= 1; side += 2) {
            float x = (float)side * 2.6F;
            float y = -8.0F;
            float z = -1.0F;

            for (int seg = 0; seg < 5; seg++) {
               float s = 1.3F - (float)seg * 0.22F;
               float k = (float)seg / 4.0F;
               float r = Mth.lerp(k, 0.55F, 1.0F);
               float g = Mth.lerp(k, 0.33F, 0.85F);
               float b = Mth.lerp(k, 0.1F, 0.45F);
               box(ps, solid, x - s / 2.0F, y - 1.4F, z - s / 2.0F, x + s / 2.0F, y, z + s / 2.0F, r, g, b, 1.0F, light);
               x += (float)side * 0.55F;
               y--;
               z += 0.9F;
            }
         }

         VertexConsumer eyes = buf.getBuffer(RenderType.eyes(WHITE));
         float pulse = 0.8F + 0.2F * Mth.sin(age * 0.4F);

         for (int side = -1; side <= 1; side += 2) {
            float cx = (float)side * 2.0F;
            box(ps, eyes, cx - 1.0F, -6.6F, -4.06F, cx + 1.0F, -5.6F, -4.0F, pulse, 0.75F * pulse, 0.25F * pulse, 1.0F, 15728880);
            box(ps, eyes, cx - 1.0F, -4.0F, -4.06F, cx + 1.0F, -3.0F, -4.0F, 0.5F * pulse, 0.9F * pulse, pulse, 1.0F, 15728880);
         }

         box(ps, eyes, -0.3F, -8.0F, -4.05F, 0.3F, -6.8F, -4.0F, 0.6F, 0.9F, 1.0F, 1.0F, 15728880);
         ps.popPose();
      }
   }

   private static void box(
      PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a, int light
   ) {
      float u = 0.0625F;
      x0 *= u;
      y0 *= u;
      z0 *= u;
      x1 *= u;
      y1 *= u;
      z1 *= u;
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
      float[][] uv = new float[][]{{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};

      for (float[] f : faces) {
         for (int i = 0; i < 4; i++) {
            vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2])
               .color(r, g, b, a)
               .uv(uv[i][0], uv[i][1])
               .overlayCoords(OverlayTexture.NO_OVERLAY)
               .uv2(light)
               .normal(n, f[12], f[13], f[14])
               .endVertex();
         }
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Reg {
      @SubscribeEvent
      public static void onLayers(AddLayers e) {
         for (String skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer pr) {
               pr.addLayer(new AmberLayer(pr));
            }
         }
      }
   }
}
