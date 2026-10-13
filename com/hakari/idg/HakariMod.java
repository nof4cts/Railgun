package com.hakari.idg;

import com.hakari.idg.network.HakariNetwork;
import com.hakari.idg.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class HakariMod {
   public static final String MODID = "hakari";

   public HakariMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ModRegistry.register(bus);
      bus.addListener(this::commonSetup);
      MinecraftForge.EVENT_BUS.register(ServerEvents.class);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(HakariNetwork::register);
   }

   public static ResourceLocation id(String path) {
      return new ResourceLocation("hakari", path);
   }
}
