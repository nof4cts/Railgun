package com.shrine;

import com.mojang.logging.LogUtils;
import com.shrine.config.ShrineConfig;
import com.shrine.network.Net;
import com.shrine.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public class ShrineMod {
   public static final String MODID = "shrine";
   public static final Logger LOGGER = LogUtils.getLogger();

   public ShrineMod() {
      ModLoadingContext.get().registerConfig(Type.CLIENT, ShrineConfig.CLIENT_SPEC, "jjk-shrine-client.toml");
      ModLoadingContext.get().registerConfig(Type.SERVER, ShrineConfig.SERVER_SPEC, "jjk-shrine-server.toml");
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModSounds.SOUNDS.register(modBus);
      Net.register();
   }

   public static ResourceLocation id(String path) {
      return new ResourceLocation("shrine", path);
   }
}
