package com.yuta.mod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class YutaMod {
   public static final String MODID = "yuta";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "yuta");
   public static final RegistryObject<Item> KATANA = ITEMS.register(
      "katana", () -> new SwordItem(Tiers.NETHERITE, 4, -2.2F, new Properties().rarity(Rarity.EPIC).fireResistant())
   );
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "yuta");
   public static final RegistryObject<EntityType<RikaEntity>> RIKA = ENTITIES.register(
      "rika", () -> Builder.of(RikaEntity::new, MobCategory.MISC).sized(2.4F, 4.6F).clientTrackingRange(10).updateInterval(1).fireImmune().build("rika")
   );

   public YutaMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ITEMS.register(bus);
      ENTITIES.register(bus);
      bus.addListener(YutaMod::attributes);
      Net.register();
   }

   private static void attributes(EntityAttributeCreationEvent e) {
      e.put((EntityType)RIKA.get(), RikaEntity.createAttributes().build());
   }
}
