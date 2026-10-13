package com.yuki.mod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class YukiMod {
   public static final String MODID = "yuki";
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "yuki");
   public static final RegistryObject<EntityType<GarudaEntity>> GARUDA = ENTITIES.register(
      "garuda", () -> Builder.of(GarudaEntity::new, MobCategory.MISC).sized(1.1F, 1.1F).clientTrackingRange(10).updateInterval(1).fireImmune().build("garuda")
   );

   public YukiMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ENTITIES.register(bus);
      bus.addListener(YukiMod::attributes);
      Net.register();
   }

   private static void attributes(EntityAttributeCreationEvent e) {
      e.put((EntityType)GARUDA.get(), GarudaEntity.createAttributes().build());
   }
}
