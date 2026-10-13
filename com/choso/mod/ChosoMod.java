package com.choso.mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ChosoMod {
   public static final String MODID = "choso";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "choso");
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "choso");
   public static final RegistryObject<SoundEvent> SND_SHOCKWAVE = sound("shockwave");
   public static final RegistryObject<SoundEvent> SND_RUMBLE = sound("rumble");
   public static final RegistryObject<SoundEvent> SND_CRACK = sound("blast_crack");

   public ChosoMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ITEMS.register(bus);
      SOUNDS.register(bus);
      Net.register();
   }

   private static RegistryObject<SoundEvent> sound(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("choso", name)));
   }
}
