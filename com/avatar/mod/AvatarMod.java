package com.avatar.mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AvatarMod {
   public static final String MODID = "avatar";
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "avatar");
   public static final RegistryObject<SoundEvent> FIRE_PUNCH = sound("fire.punch");
   public static final RegistryObject<SoundEvent> FIRE_WHOOSH = sound("fire.whoosh");
   public static final RegistryObject<SoundEvent> FIRE_BURST = sound("fire.burst");
   public static final RegistryObject<SoundEvent> FIRE_ROAR = sound("fire.roar");
   public static final RegistryObject<SoundEvent> FIRE_RUMBLE = sound("fire.rumble");
   public static final RegistryObject<SoundEvent> FIREBALL = sound("fire.fireball");
   public static final RegistryObject<SoundEvent> THUNDER = sound("lightning.thunder");
   public static final RegistryObject<SoundEvent> ZAP = sound("lightning.zap");
   public static final RegistryObject<SoundEvent> CRACK = sound("lightning.crack");
   public static final RegistryObject<SoundEvent> DRAGON_ROAR = sound("dragon.roar");

   public AvatarMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      SOUNDS.register(bus);
      Net.register();
   }

   private static RegistryObject<SoundEvent> sound(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("avatar", name)));
   }
}
