package com.gojo.mod;

import com.gojo.mod.cine.Cine;
import com.gojo.mod.fx.Fx;
import com.gojo.mod.fx.FxConfig;
import com.gojo.mod.fx.ModParticles;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class GojoMod {
   public static final String MODID = "gojo";

   public GojoMod() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModItems.ITEMS.register(modBus);
      ModParticles.PARTICLES.register(modBus);
      Fx.register();
      Cine.register();
      Cine.SOUNDS.register(modBus);
      ModLoadingContext.get().registerConfig(Type.CLIENT, FxConfig.SPEC, "jjk-gojo-client.toml");
   }
}
