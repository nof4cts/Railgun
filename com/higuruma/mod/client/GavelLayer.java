package com.higuruma.mod.client;

import com.higuruma.mod.Higuruma;
import com.higuruma.mod.HigurumaMod;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class GavelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   GavelLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
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
      if (ClientSorcerers.of(p) == Sorcerer.HIGURUMA && !p.isInvisible()) {
         VertexConsumer vc = buf.getBuffer(Cubes.SOLID);
         ps.pushPose();
         ((PlayerModel)this.getParentModel()).body.translateAndRotate(ps);
         Cubes.cbox(ps, vc, 0.12F, 0.18F, -0.135F, 0.035F, 0.035F, 0.01F, 0.95F, 0.78F, 0.25F, light);
         Cubes.cbox(ps, vc, 0.12F, 0.18F, -0.142F, 0.015F, 0.015F, 0.006F, 0.55F, 0.35F, 0.1F, light);
         ps.popPose();
         float size = HFx.gavelSize(p, partial);
         boolean sword = HFx.on(HFx.SWORD, p);
         if (sword || !(size < 1.05F) && Higuruma.hasGavel(p)) {
            ps.pushPose();
            ((PlayerModel)this.getParentModel()).rightArm.translateAndRotate(ps);
            ps.translate(-0.06, 0.62, -0.05);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            ps.translate(0.0, -0.12, 0.0);
            if (sword) {
               Cubes.sword(ps, vc, buf.getBuffer(Cubes.GLOW), 1.15F, light);
            } else {
               Cubes.gavel(ps, vc, 0.8F * size, light);
            }

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
      public static void onSetup(FMLClientSetupEvent e) {
         e.enqueueWork(() -> ItemProperties.register((Item)HigurumaMod.GAVEL.get(), new ResourceLocation("higuruma", "hidden"), (stack, level, ent, seed) -> {
               if (ent == null) {
                  return 0.0F;
               } else {
                  float sz = HFx.gavelSize(ent, 1.0F);
                  return !(sz < 0.01F) && !(sz >= 1.05F) && !HFx.on(HFx.SWORD, ent) ? 0.0F : 1.0F;
               }
            }));
      }

      @SubscribeEvent
      public static void onLayers(AddLayers e) {
         for (String skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer pr) {
               pr.addLayer(new GavelLayer(pr));
            }
         }
      }
   }
}
