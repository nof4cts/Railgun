package com.yuta.mod.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yuta.mod.Yuta;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

public final class YutaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final float[] WHITE = new float[]{0.93F, 0.93F, 0.9F};
   private static final float[] SEAM = new float[]{0.62F, 0.63F, 0.62F};
   private static final float[] BLACK = new float[]{0.07F, 0.07F, 0.08F};
   private static final float[] HAIR = new float[]{0.05F, 0.05F, 0.07F};
   private static final float[] SHOE = new float[]{0.92F, 0.92F, 0.94F};
   private static final float[] SOLE = new float[]{0.55F, 0.55F, 0.58F};
   private static final float[] CASE = new float[]{0.09F, 0.08F, 0.1F};
   private static final float[] CORD = new float[]{0.88F, 0.88F, 0.84F};
   private static final float[] RED = new float[]{0.62F, 0.07F, 0.11F};
   private static final float[] GOLD = new float[]{0.83F, 0.65F, 0.27F};
   private static final float[] SILVER = new float[]{0.86F, 0.88F, 0.92F};

   YutaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   static void katana(PoseStack ps, VertexConsumer vc, int light) {
      float o = -0.15F;
      YCubes.box(ps, vc, -0.035F, o - 0.02F, -0.035F, 0.035F, o + 0.01F, 0.035F, GOLD[0], GOLD[1], GOLD[2], light);
      YCubes.box(ps, vc, -0.03F, o, -0.03F, 0.03F, o + 0.3F, 0.03F, RED[0], RED[1], RED[2], light);

      for (int i = 0; i < 5; i++) {
         float y = o + 0.03F + (float)i * 0.055F;
         YCubes.box(ps, vc, -0.033F, y, -0.033F, 0.033F, y + 0.012F, 0.033F, 0.3F, 0.03F, 0.06F, light);
      }

      YCubes.box(ps, vc, -0.085F, o + 0.3F, -0.085F, 0.085F, o + 0.33F, 0.085F, GOLD[0], GOLD[1], GOLD[2], light);
      YCubes.box(ps, vc, -0.026F, o + 0.33F, -0.03F, 0.026F, o + 0.37F, 0.034F, GOLD[0], GOLD[1], GOLD[2], light);
      float[][] seg = new float[][]{{0.37F, 0.7F, 0.0F}, {0.7F, 1.02F, 0.008F}, {1.02F, 1.28F, 0.02F}};

      for (float[] s : seg) {
         YCubes.box(ps, vc, -0.011F, o + s[0], -0.026F + s[2], 0.011F, o + s[1], 0.022F + s[2], 0.74F, 0.77F, 0.82F, light);
         YCubes.box(ps, vc, -0.008F, o + s[0], 0.022F + s[2], 0.008F, o + s[1], 0.032F + s[2], 0.95F, 0.96F, 0.98F, light);
      }

      YCubes.box(ps, vc, -0.008F, o + 1.28F, 0.017F, 0.008F, o + 1.33F, 0.055F, 0.9F, 0.92F, 0.95F, light);
   }

   private static void b(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] c, int light) {
      YCubes.px(ps, vc, x0, y0, z0, x1, y1, z1, c[0], c[1], c[2], light);
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
      if (!p.isInvisible()) {
         VertexConsumer vc = buf.getBuffer(YCubes.SOLID);
         PlayerModel<AbstractClientPlayer> m = (PlayerModel<AbstractClientPlayer>)this.getParentModel();
         if (Yuta.hasKatana(p)) {
            ps.pushPose();
            m.rightArm.translateAndRotate(ps);
            ps.translate(-0.06, 0.62, -0.05);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            katana(ps, vc, light);
            ps.popPose();
         }

         if (ClientSorcerers.of(p) == Sorcerer.YUTA) {
            boolean slim = "slim".equals(p.getModelName());
            float aw = slim ? 3.0F : 4.0F;
            ps.pushPose();
            m.body.translateAndRotate(ps);
            b(ps, vc, -4.45F, -0.2F, -2.45F, 4.45F, 13.6F, 2.45F, WHITE, light);
            b(ps, vc, -4.6F, -1.4F, -2.6F, 4.6F, 0.9F, 2.6F, WHITE, light);
            b(ps, vc, -4.62F, 0.6F, -2.62F, 4.62F, 0.9F, 2.62F, SEAM, light);
            b(ps, vc, -0.25F, 0.9F, -2.56F, 0.25F, 13.6F, -2.45F, SEAM, light);

            for (int i = 0; i < 4; i++) {
               float y = 2.2F + (float)i * 2.6F;
               b(ps, vc, 0.45F, y, -2.6F, 1.15F, y + 0.7F, -2.45F, GOLD, light);
            }

            b(ps, vc, -4.5F, 11.2F, -2.5F, 4.5F, 11.8F, 2.5F, SEAM, light);
            ps.pushPose();
            ps.translate(0.0F, 0.40625F, 0.18125F);
            ps.mulPose(Axis.ZP.rotationDegrees(-32.0F));
            b(ps, vc, -0.85F, -9.5F, -0.5F, 0.85F, 8.5F, 0.6F, CASE, light);
            b(ps, vc, -0.95F, -4.0F, -0.6F, 0.95F, -3.2F, 0.7F, CORD, light);
            b(ps, vc, -0.95F, 4.0F, -0.6F, 0.95F, 4.8F, 0.7F, CORD, light);
            if (!Yuta.hasKatana(p)) {
               b(ps, vc, -1.4F, -10.2F, -1.0F, 1.4F, -9.5F, 1.1F, GOLD, light);
               b(ps, vc, -0.55F, -15.5F, -0.4F, 0.55F, -10.2F, 0.6F, RED, light);
               b(ps, vc, -0.6F, -16.1F, -0.45F, 0.6F, -15.5F, 0.65F, GOLD, light);
            }

            ps.popPose();
            ps.popPose();
            ps.pushPose();
            m.rightArm.translateAndRotate(ps);
            float rx = slim ? -2.0F : -3.0F;
            b(ps, vc, rx - 0.45F, -2.45F, -2.45F, rx + aw + 0.45F, 8.2F, 2.45F, WHITE, light);
            b(ps, vc, rx - 0.5F, 7.4F, -2.5F, rx + aw + 0.5F, 8.2F, 2.5F, SEAM, light);
            ps.popPose();
            ps.pushPose();
            m.leftArm.translateAndRotate(ps);
            float lx = -1.0F;
            b(ps, vc, lx - 0.45F, -2.45F, -2.45F, lx + aw + 0.45F, 8.2F, 2.45F, WHITE, light);
            b(ps, vc, lx - 0.5F, 7.4F, -2.5F, lx + aw + 0.5F, 8.2F, 2.5F, SEAM, light);
            b(ps, vc, lx + aw - 0.6F, 10.4F, -2.25F, lx + aw + 0.15F, 11.0F, -1.2F, SILVER, light);
            ps.popPose();

            for (int side = 0; side < 2; side++) {
               ps.pushPose();
               (side == 0 ? m.rightLeg : m.leftLeg).translateAndRotate(ps);
               b(ps, vc, -2.4F, -0.3F, -2.4F, 2.4F, 10.4F, 2.4F, BLACK, light);
               b(ps, vc, -2.45F, 10.0F, -2.75F, 2.45F, 12.05F, 2.45F, SHOE, light);
               b(ps, vc, -2.5F, 11.5F, -2.8F, 2.5F, 12.1F, 2.5F, SOLE, light);
               ps.popPose();
            }

            ps.pushPose();
            m.head.translateAndRotate(ps);
            b(ps, vc, -4.4F, -8.9F, -4.4F, 4.4F, -7.4F, 4.4F, HAIR, light);
            b(ps, vc, -4.45F, -7.6F, 3.2F, 4.45F, -1.2F, 4.5F, HAIR, light);
            b(ps, vc, -4.5F, -7.6F, -4.2F, -3.7F, -3.6F, 3.3F, HAIR, light);
            b(ps, vc, 3.7F, -7.6F, -4.2F, 4.5F, -3.6F, 3.3F, HAIR, light);
            b(ps, vc, -4.3F, -7.6F, -4.55F, 4.3F, -6.4F, -4.05F, HAIR, light);
            float[][] strands = new float[][]{{-3.8F, -2.3F, -5.0F}, {-2.0F, -0.6F, -5.6F}, {-0.2F, 1.3F, -5.2F}, {1.6F, 3.0F, -5.8F}, {3.0F, 3.9F, -5.3F}};

            for (float[] s : strands) {
               b(ps, vc, s[0], -6.5F, -4.6F, s[1], s[2], -4.1F, HAIR, light);
            }

            b(ps, vc, -1.0F, -9.6F, -1.5F, 0.8F, -8.8F, 1.2F, HAIR, light);
            ps.popPose();
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
               pr.addLayer(new YutaLayer(pr));
            }
         }
      }
   }
}
