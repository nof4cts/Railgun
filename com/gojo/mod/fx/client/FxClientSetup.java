package com.gojo.mod.fx.client;

import com.gojo.mod.fx.ModParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "jjk",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class FxClientSetup {
   private FxClientSetup() {
   }

   @SubscribeEvent
   public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
      event.registerSpriteSet((ParticleType)ModParticles.ENERGY.get(), sprites -> {
         ClientFx.energySprites = sprites;
         return (type, level, x, y, z, dx, dy, dz) -> new EnergyParticle(level, x, y, z, dx, dy, dz, sprites, 20).colors(10475775, 3042303).size(0.25F, 0.0F);
      });
      event.registerSpriteSet((ParticleType)ModParticles.SPARK.get(), sprites -> {
         ClientFx.sparkSprites = sprites;
         return (type, level, x, y, z, dx, dy, dz) -> new EnergyParticle(level, x, y, z, dx, dy, dz, sprites, 12).colors(16777215, 4033535).size(0.3F, 0.0F);
      });
   }
}
