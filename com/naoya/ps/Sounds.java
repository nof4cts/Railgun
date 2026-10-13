package com.naoya.ps;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Sounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "naoya");
   public static final RegistryObject<SoundEvent> SHUTTER = reg("shutter");
   public static final RegistryObject<SoundEvent> DASH = reg("dash");
   public static final RegistryObject<SoundEvent> BOOM = reg("boom");
   public static final RegistryObject<SoundEvent> FREEZE = reg("freeze");
   public static final RegistryObject<SoundEvent> SHATTER = reg("shatter");
   public static final RegistryObject<SoundEvent> HIT = reg("hit");
   public static final RegistryObject<SoundEvent> HEAVY = reg("heavy");
   public static final RegistryObject<SoundEvent> FILM = reg("film");
   public static final RegistryObject<SoundEvent> ULT_HIT = reg("ult_hit");
   public static final RegistryObject<SoundEvent> BLINK = reg("blink");

   private Sounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Naoya.id(name)));
   }

   static void register(IEventBus bus) {
      SOUNDS.register(bus);
   }
}
