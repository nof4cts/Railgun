package com.ryu.mod;

import com.ryu.mod.fx.Fx;
import com.ryu.mod.fx.FxConfig;
import com.ryu.mod.fx.ModParticles;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class RyuMod {
   public static final String MODID = "ryu";

   public RyuMod() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModItems.ITEMS.register(modBus);
      ModParticles.PARTICLES.register(modBus);
      Fx.register();
      ModLoadingContext.get().registerConfig(Type.CLIENT, FxConfig.SPEC, "jjk-ryu-client.toml");
   }
}
