package com.geto.csm.client;

import com.geto.csm.ModRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "jjk",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class ClientModEvents {
   @SubscribeEvent
   public static void onRenderers(RegisterRenderers e) {
      e.registerEntityRenderer((EntityType)ModRegistry.WYRM.get(), DragonRenderers.Wyrm::new);
      e.registerEntityRenderer((EntityType)ModRegistry.PYRE.get(), NoopRenderer::new);
      e.registerEntityRenderer((EntityType)ModRegistry.KUCHISAKE.get(), NoopRenderer::new);
      e.registerEntityRenderer((EntityType)ModRegistry.TAMAMO.get(), NoopRenderer::new);
      e.registerEntityRenderer((EntityType)ModRegistry.ATROXIIA.get(), DragonRenderers.Atroxiia::new);
      e.registerEntityRenderer((EntityType)ModRegistry.CINDERVANE.get(), DragonRenderers.Cindervane::new);
      e.registerEntityRenderer((EntityType)ModRegistry.RAEVYX.get(), DragonRenderers.Raevyx::new);
   }

   private ClientModEvents() {
   }
}
