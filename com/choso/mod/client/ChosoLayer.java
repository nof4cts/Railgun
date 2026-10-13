package com.choso.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.client.event.RenderPlayerEvent.Pre;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class ChosoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final ResourceLocation WHITE = new ResourceLocation("choso", "textures/entity/white.png");
   private static ArmPose[] poses;

   ChosoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   static float progress(LivingEntity e, long[] p) {
      float t = (float)(CFx.gameTime() - p[1]) + Minecraft.getInstance().getFrameTime();
      return p[2] <= 0L ? -1.0F : t / (float)p[2];
   }

   private static ArmPose[] poses() {
      if (poses == null) {
         poses = new ArmPose[6];

         for (int i = 1; i <= 5; i++) {
            int id = i;
            poses[i] = ArmPose.create("CHOSO_POSE_" + i, id != 2 && id != 3, (model, entity, arm) -> apply(id, model, entity, arm));
         }
      }

      return poses;
   }

   private static void apply(int id, HumanoidModel<?> m, LivingEntity e, HumanoidArm arm) {
      long[] p = CFx.POSES.get(e.getId());
      float k = p == null ? 1.0F : Mth.clamp(progress(e, p), 0.0F, 1.0F);
      boolean right = arm == HumanoidArm.RIGHT;
      ModelPart part = right ? m.rightArm : m.leftArm;
      float side = right ? 1.0F : -1.0F;
      float in = Mth.clamp(k * 5.0F, 0.0F, 1.0F);
      switch (id) {
         case 1:
            part.xRot = Mth.lerp(in, part.xRot, m.head.xRot - (float) (Math.PI / 2));
            part.yRot = m.head.yRot - side * 0.48F;
            part.zRot = 0.0F;
            break;
         case 2:
            float x = k < 0.35F ? Mth.lerp(k / 0.35F, -0.4F, -2.9F) : Mth.lerp(Mth.clamp((k - 0.35F) / 0.3F, 0.0F, 1.0F), -2.9F, -1.2F);
            part.xRot = x;
            part.yRot = m.head.yRot * 0.5F;
            break;
         case 3:
            part.xRot = -1.45F;
            part.yRot = m.head.yRot + Mth.lerp(Math.min(1.0F, k * 2.2F), 1.0F, -1.3F);
            break;
         case 4:
            part.xRot = Mth.lerp(in, part.xRot, -0.08F);
            part.yRot = 0.0F;
            part.zRot = side * Mth.lerp(in, 0.1F, 1.52F);
            break;
         default:
            part.xRot = Mth.lerp(in, part.xRot, -2.85F);
            part.yRot = 0.0F;
            part.zRot = side * Mth.lerp(in, 0.0F, 0.35F);
      }
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
      if (!p.isInvisible() && CFx.isChoso(p)) {
         int lvl = CFx.scaleLevel(p);
         ps.pushPose();
         ((PlayerModel)this.getParentModel()).head.translateAndRotate(ps);
         VertexConsumer solid = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
         box(ps, solid, -4.02F, -4.6F, -4.06F, 4.02F, -3.55F, -4.0F, 0.13F, 0.06F, 0.08F, 1.0F, light);
         box(ps, solid, -4.06F, -4.6F, -4.0F, -4.0F, -3.55F, -1.5F, 0.13F, 0.06F, 0.08F, 1.0F, light);
         box(ps, solid, 4.0F, -4.6F, -4.0F, 4.06F, -3.55F, -1.5F, 0.13F, 0.06F, 0.08F, 1.0F, light);
         if (lvl > 0) {
            VertexConsumer glow = buf.getBuffer(RenderType.eyes(WHITE));
            float pulse = 0.75F + 0.25F * Mth.sin(age * (lvl >= 2 ? 0.9F : 0.45F));
            float g = 0.08F * pulse;
            float b = 0.08F * pulse;
            box(ps, glow, -3.4F, -3.5F, -4.07F, -3.0F, -1.2F, -4.0F, pulse, g, b, 1.0F, 15728880);
            box(ps, glow, 3.0F, -3.5F, -4.07F, 3.4F, -1.2F, -4.0F, pulse, g, b, 1.0F, 15728880);
            box(ps, glow, -3.0F, -1.6F, -4.07F, -2.0F, -1.2F, -4.0F, pulse, g, b, 1.0F, 15728880);
            box(ps, glow, 2.0F, -1.6F, -4.07F, 3.0F, -1.2F, -4.0F, pulse, g, b, 1.0F, 15728880);
            if (lvl >= 2) {
               box(ps, glow, -0.4F, -7.6F, -4.07F, 0.4F, -5.2F, -4.0F, pulse, g, b, 1.0F, 15728880);
               box(ps, glow, -2.6F, -6.4F, -4.07F, -1.0F, -6.0F, -4.0F, pulse, g, b, 1.0F, 15728880);
               box(ps, glow, 1.0F, -6.4F, -4.07F, 2.6F, -6.0F, -4.0F, pulse, g, b, 1.0F, 15728880);
            }
         }

         ps.popPose();
         if (lvl > 0) {
            VertexConsumer glow = buf.getBuffer(RenderType.eyes(WHITE));
            float pulse = 0.7F + 0.3F * Mth.sin(age * 0.6F);

            for (int side = 0; side < 2; side++) {
               ps.pushPose();
               (side == 0 ? ((PlayerModel)this.getParentModel()).rightArm : ((PlayerModel)this.getParentModel()).leftArm).translateAndRotate(ps);
               float x0 = side == 0 ? -3.08F : 3.0F;
               float x1 = x0 + 0.08F;
               box(ps, glow, x0, 1.0F, -1.2F, x1, 9.5F, -0.6F, pulse, 0.06F, 0.06F, 1.0F, 15728880);
               box(ps, glow, x0, 4.0F, 0.6F, x1, 10.5F, 1.2F, pulse, 0.06F, 0.06F, 1.0F, 15728880);
               ps.popPose();
            }
         }
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
      value = {Dist.CLIENT}
   )
   public static final class PoseHook {
      @SubscribeEvent
      public static void onRenderPlayer(Pre e) {
         long[] p = CFx.POSES.get(e.getEntity().getId());
         if (p != null) {
            float k = ChosoLayer.progress(e.getEntity(), p);
            if (!(k < 0.0F) && !(k > 1.0F)) {
               ArmPose pose = ChosoLayer.poses()[(int)p[0]];
               PlayerModel<AbstractClientPlayer> m = (PlayerModel<AbstractClientPlayer>)e.getRenderer().getModel();
               boolean rightHanded = e.getEntity().getMainArm() == HumanoidArm.RIGHT;
               if (p[0] != 2L && p[0] != 3L) {
                  m.rightArmPose = pose;
                  m.leftArmPose = pose;
               } else if (rightHanded) {
                  m.rightArmPose = pose;
               } else {
                  m.leftArmPose = pose;
               }
            }
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
      public static void onSetup(FMLClientSetupEvent e) {
         e.enqueueWork(ChosoLayer::poses);
      }

      @SubscribeEvent
      public static void onLayers(AddLayers e) {
         for (String skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer pr) {
               pr.addLayer(new ChosoLayer(pr));
            }
         }
      }
   }
}
