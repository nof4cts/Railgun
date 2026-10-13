package com.geto.csm;

import com.geto.csm.entity.AtroxiiaEntity;
import com.geto.csm.entity.CindervaneEntity;
import com.geto.csm.entity.CurseEntity;
import com.geto.csm.entity.KuchisakeEntity;
import com.geto.csm.entity.PyreEntity;
import com.geto.csm.entity.RaevyxEntity;
import com.geto.csm.entity.TamamoEntity;
import com.geto.csm.entity.WyrmEntity;
import com.geto.csm.item.CurseOrbItem;
import com.geto.csm.item.ManipulationItem;
import com.geto.csm.item.UzumakiItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.EntityType.EntityFactory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "cursemanip");
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "cursemanip");
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "cursemanip");
   public static final RegistryObject<Item> MANIPULATION = ITEMS.register("cursed_spirit_manipulation", ManipulationItem::new);
   public static final RegistryObject<Item> UZUMAKI = ITEMS.register("maximum_uzumaki", UzumakiItem::new);
   public static final RegistryObject<Item> CURSE_ORB = ITEMS.register("curse_orb", CurseOrbItem::new);
   public static final RegistryObject<EntityType<WyrmEntity>> WYRM = mob("gloomscale_wyrm", WyrmEntity::new, 3.6F, 3.0F, true);
   public static final RegistryObject<EntityType<PyreEntity>> PYRE = mob("pyre_wraith", PyreEntity::new, 1.0F, 2.6F, true);
   public static final RegistryObject<EntityType<KuchisakeEntity>> KUCHISAKE = mob("kuchisake_onna", KuchisakeEntity::new, 0.8F, 3.1F, false);
   public static final RegistryObject<EntityType<TamamoEntity>> TAMAMO = mob("tamamo_no_mae", TamamoEntity::new, 2.8F, 2.4F, true);
   public static final RegistryObject<EntityType<CindervaneEntity>> CINDERVANE = mob("cindervane", CindervaneEntity::new, 2.5F, 2.5F, true);
   public static final RegistryObject<EntityType<RaevyxEntity>> RAEVYX = mob("raevyx", RaevyxEntity::new, 4.0F, 2.75F, false);
   public static final RegistryObject<EntityType<AtroxiiaEntity>> ATROXIIA = mob("atroxiia", AtroxiiaEntity::new, 3.2F, 4.75F, false);
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
      "cursemanip",
      () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cursemanip"))
            .icon(() -> new ItemStack((ItemLike)MANIPULATION.get()))
            .displayItems((params, out) -> {
               out.accept((ItemLike)MANIPULATION.get());
               out.accept((ItemLike)UZUMAKI.get());
               out.accept((ItemLike)CURSE_ORB.get());
            })
            .build()
   );

   private static <T extends PathfinderMob> RegistryObject<EntityType<T>> mob(String name, EntityFactory<T> f, float w, float h, boolean fireImmune) {
      return ENTITIES.register(name, () -> {
         Builder<T> b = Builder.of(f, MobCategory.MISC).sized(w, h).clientTrackingRange(12).updateInterval(1);
         if (fireImmune) {
            b = b.fireImmune();
         }

         return b.build(name);
      });
   }

   public static EntityType<? extends CurseEntity> typeOf(CurseKind k) {
      return switch (k) {
         case WYRM -> (EntityType)WYRM.get();
         case PYRE -> (EntityType)PYRE.get();
         case KUCHISAKE -> (EntityType)KUCHISAKE.get();
         case TAMAMO -> (EntityType)TAMAMO.get();
         case ATROXIIA -> (EntityType)ATROXIIA.get();
         case CINDERVANE -> (EntityType)CINDERVANE.get();
         case RAEVYX -> (EntityType)RAEVYX.get();
      };
   }

   public static void onAttributes(EntityAttributeCreationEvent e) {
      e.put((EntityType)WYRM.get(), CurseEntity.attributes(100.0, 0.3, 16.0).build());
      e.put((EntityType)PYRE.get(), CurseEntity.attributes(90.0, 0.22, 6.0).build());
      e.put((EntityType)KUCHISAKE.get(), CurseEntity.attributes(120.0, 0.3, 8.0).build());
      e.put((EntityType)TAMAMO.get(), CurseEntity.attributes(200.0, 0.3, 10.0).build());
      e.put((EntityType)ATROXIIA.get(), CurseEntity.attributes(100.0, 0.28, 14.0).build());
      e.put((EntityType)CINDERVANE.get(), CurseEntity.attributes(45.0, 0.3, 12.0).build());
      e.put((EntityType)RAEVYX.get(), CurseEntity.attributes(100.0, 0.3, 15.0).build());
   }

   public static void register(IEventBus bus) {
      ITEMS.register(bus);
      ENTITIES.register(bus);
   }

   private ModRegistry() {
   }
}
