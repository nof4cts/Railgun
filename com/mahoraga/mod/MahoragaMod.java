package com.mahoraga.mod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MahoragaMod {
   public static final String MODID = "mahoraga";
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "mahoraga");
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "mahoraga");
   public static final RegistryObject<EntityType<MahoragaEntity>> MAHORAGA = ENTITIES.register(
      "mahoraga", () -> Builder.of(MahoragaEntity::new, MobCategory.MONSTER).sized(1.6F, 4.2F).clientTrackingRange(12).build("mahoraga")
   );
   public static final RegistryObject<Item> EGG = ITEMS.register(
      "mahoraga_spawn_egg", () -> new ForgeSpawnEggItem(MAHORAGA, 1710622, 15260072, new Properties())
   );

   public MahoragaMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ENTITIES.register(bus);
      ITEMS.register(bus);
      bus.addListener(MahoragaMod::attributes);
      Net.register();
   }

   private static void attributes(EntityAttributeCreationEvent e) {
      e.put((EntityType)MAHORAGA.get(), MahoragaEntity.createAttributes().build());
   }
}
