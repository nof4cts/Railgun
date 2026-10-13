package com.geto.csm;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CsmSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "cursemanip");
   public static final RegistryObject<SoundEvent> NULLJAW_BITE = reg("nulljaw_bite");
   public static final RegistryObject<SoundEvent> NULLJAW_HURT = reg("nulljaw_hurt");
   public static final RegistryObject<SoundEvent> NULLJAW_DIE = reg("nulljaw_die");
   public static final RegistryObject<SoundEvent> NULLJAW_GRUMBLE1 = reg("nulljaw_grumble1");
   public static final RegistryObject<SoundEvent> NULLJAW_GRUMBLE2 = reg("nulljaw_grumble2");
   public static final RegistryObject<SoundEvent> NULLJAW_GRUMBLE3 = reg("nulljaw_grumble3");
   public static final RegistryObject<SoundEvent> ATROXIIA_SWIPE = reg("atroxiia_swipe");
   public static final RegistryObject<SoundEvent> ATROXIIA_SLAM = reg("atroxiia_slam");
   public static final RegistryObject<SoundEvent> ATROXIIA_DEVASTATING_SWEEP = reg("atroxiia_devastating_sweep");
   public static final RegistryObject<SoundEvent> ATROXIIA_GUNGNIR_STAB = reg("atroxiia_gungnir_stab");
   public static final RegistryObject<SoundEvent> ATROXIIA_PRECISE_STRIKE = reg("atroxiia_precise_strike");
   public static final RegistryObject<SoundEvent> ATROXIIA_HELHEIM_QUAKE1 = reg("atroxiia_helheim_quake1");
   public static final RegistryObject<SoundEvent> ATROXIIA_HELHEIM_QUAKE2 = reg("atroxiia_helheim_quake2");
   public static final RegistryObject<SoundEvent> ATROXIIA_HURT = reg("atroxiia_hurt");
   public static final RegistryObject<SoundEvent> ATROXIIA_DIE = reg("atroxiia_die");
   public static final RegistryObject<SoundEvent> ATROXIIA_STEP = reg("atroxiia_step");
   public static final RegistryObject<SoundEvent> ATROXIIA_BITE = reg("atroxiia_underwater_bite");
   public static final RegistryObject<SoundEvent> ATROXIIA_FLEX = reg("atroxiia_flex");
   public static final RegistryObject<SoundEvent> ATROXIIA_GRUMBLE1 = reg("atroxiia_grumble1");
   public static final RegistryObject<SoundEvent> ATROXIIA_GRUMBLE2 = reg("atroxiia_grumble2");
   public static final RegistryObject<SoundEvent> ATROXIIA_GRUMBLE3 = reg("atroxiia_grumble3");
   public static final RegistryObject<SoundEvent> CINDERVANE_BITE = reg("cindervane_bite");
   public static final RegistryObject<SoundEvent> CINDERVANE_DOUBLE_BITE = reg("cindervane_double_bite");
   public static final RegistryObject<SoundEvent> CINDERVANE_SLASH = reg("cindervane_slash");
   public static final RegistryObject<SoundEvent> CINDERVANE_MAGMA_VOLLEY = reg("cindervane_magma_volley");
   public static final RegistryObject<SoundEvent> CINDERVANE_HURT = reg("cindervane_hurt");
   public static final RegistryObject<SoundEvent> CINDERVANE_DIE = reg("cindervane_die");
   public static final RegistryObject<SoundEvent> CINDERVANE_FLAP = reg("cindervane_flap");
   public static final RegistryObject<SoundEvent> RAEVYX_BITE = reg("raevyx_bite");
   public static final RegistryObject<SoundEvent> RAEVYX_HORNGORE = reg("raevyx_horngore");
   public static final RegistryObject<SoundEvent> RAEVYX_BEAM_START = reg("raevyx_lightning_beam_start");
   public static final RegistryObject<SoundEvent> RAEVYX_BEAMING = reg("raevyx_lightning_beaming");
   public static final RegistryObject<SoundEvent> RAEVYX_BEAM_STOP = reg("raevyx_lightning_beam_stop");
   public static final RegistryObject<SoundEvent> RAEVYX_SUMMON_STORM = reg("raevyx_summon_storm");
   public static final RegistryObject<SoundEvent> RAEVYX_HURT = reg("raevyx_hurt");
   public static final RegistryObject<SoundEvent> RAEVYX_DIE = reg("raevyx_die");
   public static final RegistryObject<SoundEvent> RAEVYX_ROAR = reg("raevyx_roar");

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("cursemanip", name)));
   }

   public static void register(IEventBus bus) {
      SOUNDS.register(bus);
   }

   private CsmSounds() {
   }
}
