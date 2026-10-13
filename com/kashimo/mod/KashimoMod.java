package com.kashimo.mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class KashimoMod {
   public static final String MODID = "kashimo";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "kashimo");
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "kashimo");
   public static final RegistryObject<Item> NYOI_STAFF = ITEMS.register(
      "nyoi_staff", () -> new SwordItem(Tiers.NETHERITE, 4, -2.2F, new Properties().rarity(Rarity.EPIC).fireResistant())
   );
   public static final RegistryObject<SoundEvent> SND_SHOCKWAVE = sound("shockwave");
   public static final RegistryObject<SoundEvent> SND_RUMBLE = sound("rumble");
   public static final RegistryObject<SoundEvent> SND_CRACK = sound("blast_crack");

   public KashimoMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ITEMS.register(bus);
      SOUNDS.register(bus);
      Net.register();
   }

   private static RegistryObject<SoundEvent> sound(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("kashimo", name)));
   }

   private static void tabs(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() == CreativeModeTabs.COMBAT) {
         e.accept(NYOI_STAFF);
      }
   }
}
