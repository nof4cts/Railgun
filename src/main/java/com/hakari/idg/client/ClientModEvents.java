package com.hakari.idg.client;

import com.hakari.idg.ModRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
   modid = "jjk",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class ClientModEvents {
   @SubscribeEvent
   public static void onRenderers(RegisterRenderers e) {
      e.registerEntityRenderer((EntityType)ModRegistry.RESERVE_BALL_ENTITY.get(), ReserveBallRenderer::new);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent e) {
      e.enqueueWork(() -> {
         Object warm = ArmPoses.SIGN;
      });
   }

   private ClientModEvents() {
   }
}
