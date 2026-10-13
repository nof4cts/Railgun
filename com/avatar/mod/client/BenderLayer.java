package com.avatar.mod.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class BenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final ResourceLocation WHITE = new ResourceLocation("avatar", "textures/entity/white.png");
   private static final ResourceLocation GLIDER = new ResourceLocation("avatar", "textures/entity/glider.png");

   BenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
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
      Sorcerer el = ClientSorcerers.of(p);
      if (el.isAvatar() && !p.isInvisible()) {
         if (el != Sorcerer.FIRE) {
            this.element(ps, buf, light, p, el, partial);
         } else {
            ps.pushPose();
            ((PlayerModel)this.getParentModel()).head.translateAndRotate(ps);
            if (p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
               VertexConsumer solid = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
               float hr = 0.13F;
               float hg = 0.09F;
               float hb = 0.07F;
               box(ps, solid, -1.3F, -10.2F, -0.4F, 1.3F, -8.0F, 2.2F, hr, hg, hb, light);
               box(ps, solid, -1.0F, -10.9F, -0.1F, 1.0F, -10.2F, 1.9F, hr * 1.2F, hg * 1.2F, hb * 1.2F, light);
               box(ps, solid, -1.4F, -8.7F, -0.5F, 1.4F, -8.2F, 2.3F, 0.62F, 0.08F, 0.06F, light);
               float gr = 0.96F;
               float gg = 0.74F;
               float gb = 0.22F;
               box(ps, solid, -1.3F, -9.3F, -1.15F, 1.3F, -8.4F, -0.55F, gr * 0.85F, gg * 0.85F, gb * 0.85F, light);
               box(ps, solid, -0.35F, -12.0F, -1.05F, 0.35F, -9.3F, -0.65F, gr, gg, gb, light);
               box(ps, solid, -0.2F, -12.7F, -1.0F, 0.2F, -12.0F, -0.7F, gr, Math.min(1.0F, gg * 1.1F), gb, light);

               for (int side = -1; side <= 1; side += 2) {
                  float x = (float)side * 0.85F;
                  box(ps, solid, x - 0.25F, -10.6F, -1.05F, x + 0.25F, -9.3F, -0.65F, gr * 0.95F, gg * 0.95F, gb, light);
                  float x2 = x + (float)side * 0.15F;
                  box(ps, solid, x2 - 0.15F, -11.2F, -1.0F, x2 + 0.15F, -10.6F, -0.7F, gr, gg, gb, light);
               }
            }

            boolean blue = Arts.isBlue(p);
            float k = blue ? 0.8F : FireFx.breath(p, partial);
            if (k > 0.01F) {
               VertexConsumer eyes = buf.getBuffer(RenderType.eyes(WHITE));
               float pulse = (0.6F + 0.4F * k) * (0.85F + 0.15F * Mth.sin(age * 0.5F));

               for (int side = -1; side <= 1; side += 2) {
                  float x0 = side < 0 ? -3.0F : 1.0F;
                  if (blue) {
                     box(ps, eyes, x0, -4.2F, -4.06F, x0 + 2.0F, -3.2F, -4.01F, 0.25F * pulse, 0.6F * pulse, pulse, 15728880);
                  } else {
                     box(ps, eyes, x0, -4.2F, -4.06F, x0 + 2.0F, -3.2F, -4.01F, pulse, 0.55F * pulse, 0.12F * pulse, 15728880);
                  }
               }
            }

            ps.popPose();
         }
      }
   }

   private void element(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, Sorcerer el, float partial) {
      VertexConsumer solid = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
      boolean helmet = !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
      ps.pushPose();
      ((PlayerModel)this.getParentModel()).head.translateAndRotate(ps);
      if (!helmet) {
         if (el == Sorcerer.WATER) {
            for (int side = -1; side <= 1; side += 2) {
               float x = (float)side * 4.05F;
               box(ps, solid, x - 0.4F, -5.5F, -3.6F, x + 0.4F, -2.0F, -2.8F, 0.25F, 0.15F, 0.08F, light);
               box(ps, solid, x - 0.4F, -2.0F, -3.6F, x + 0.4F, -1.4F, -1.6F, 0.25F, 0.15F, 0.08F, light);
            }
         } else if (el == Sorcerer.EARTH) {
            box(ps, solid, -4.15F, -6.9F, -4.15F, 4.15F, -6.0F, 4.15F, 0.3F, 0.5F, 0.18F, light);
         } else {
            float r = 0.22F;
            float g = 0.52F;
            float b = 0.95F;
            box(ps, solid, -0.6F, -8.06F, -3.0F, 0.6F, -7.98F, 4.02F, r, g, b, light);
            box(ps, solid, -0.6F, -8.0F, 4.0F, 0.6F, -4.0F, 4.06F, r, g, b, light);
            box(ps, solid, -0.6F, -8.0F, -4.06F, 0.6F, -6.3F, -4.0F, r, g, b, light);
            box(ps, solid, -1.6F, -6.4F, -4.06F, 1.6F, -5.9F, -4.0F, r, g, b, light);
            box(ps, solid, -0.9F, -5.9F, -4.06F, 0.9F, -5.5F, -4.0F, r, g, b, light);
         }
      }

      ps.popPose();
      if (el == Sorcerer.EARTH && Elems.on(Elems.GAUNT, p)) {
         for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            (side > 0 ? ((PlayerModel)this.getParentModel()).rightArm : ((PlayerModel)this.getParentModel()).leftArm).translateAndRotate(ps);
            box(ps, solid, -2.6F, 5.5F, -2.6F, 2.6F, 11.2F, 2.6F, 0.45F, 0.42F, 0.4F, light);
            box(ps, solid, -2.2F, 2.5F, -2.2F, 2.2F, 5.5F, 2.2F, 0.38F, 0.35F, 0.33F, light);
            ps.popPose();
         }
      }

      if (el == Sorcerer.AIR && Elems.on(Elems.GLIDE, p)) {
         this.glider(ps, buf, light);
      }
   }

   private static double[] grip(ModelPart a) {
      double x = 0.0;
      double y = 11.0;
      double z = 0.0;
      double c = Math.cos((double)a.xRot);
      double s = Math.sin((double)a.xRot);
      double y1 = y * c - z * s;
      double z1 = y * s + z * c;
      c = Math.cos((double)a.yRot);
      s = Math.sin((double)a.yRot);
      double x2 = x * c + z1 * s;
      double z2 = -x * s + z1 * c;
      c = Math.cos((double)a.zRot);
      s = Math.sin((double)a.zRot);
      return new double[]{(double)a.x + x2 * c - y1 * s, (double)a.y + x2 * s + y1 * c, (double)a.z + z2};
   }

   private void glider(PoseStack ps, MultiBufferSource buf, int light) {
      double[] r = grip(((PlayerModel)this.getParentModel()).rightArm);
      double[] l = grip(((PlayerModel)this.getParentModel()).leftArm);
      float cx = (float)((r[0] + l[0]) / 2.0);
      float cy = (float)((r[1] + l[1]) / 2.0) - 0.6F;
      float cz = (float)((r[2] + l[2]) / 2.0);
      VertexConsumer wood = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
      box(ps, wood, cx - 0.7F, cy - 0.2F, cz - 26.0F, cx + 0.7F, cy + 1.2F, cz + 30.0F, 0.5F, 0.34F, 0.18F, light);
      box(ps, wood, cx - 0.9F, cy - 0.4F, cz - 27.0F, cx + 0.9F, cy + 1.4F, cz - 25.5F, 0.85F, 0.18F, 0.12F, light);
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(GLIDER));
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float u = 0.0625F;
      float x0 = (cx - 28.0F) * u;
      float x1 = (cx + 28.0F) * u;
      float y = (cy - 1.6F) * u;
      float z0 = (cz - 22.0F) * u;
      float z1 = (cz + 26.0F) * u;
      float[][] q = new float[][]{{x0, z0, 0.0F, 0.0F}, {x1, z0, 1.0F, 0.0F}, {x1, z1, 1.0F, 1.0F}, {x0, z1, 0.0F, 1.0F}};

      for (float[] c : q) {
         vc.vertex(m, c[0], y, c[1])
            .color(1.0F, 1.0F, 1.0F, 1.0F)
            .uv(c[2], c[3])
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(n, 0.0F, -1.0F, 0.0F)
            .endVertex();
      }
   }

   private static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, int light) {
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
               .color(r, g, b, 1.0F)
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
               pr.addLayer(new BenderLayer(pr));
            }
         }
      }
   }
}
