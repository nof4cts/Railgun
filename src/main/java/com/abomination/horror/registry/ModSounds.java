package com.abomination.horror.registry;

import com.abomination.horror.AbominationMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AbominationMod.MODID);

    public static final RegistryObject<SoundEvent> AMBIENT = reg("ambient");
    public static final RegistryObject<SoundEvent> STEP = reg("step");
    public static final RegistryObject<SoundEvent> SCRAPE = reg("scrape");
    public static final RegistryObject<SoundEvent> WHISPER = reg("whisper");
    public static final RegistryObject<SoundEvent> SUB_BASS = reg("sub_bass");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> SCREAM = reg("scream");
    public static final RegistryObject<SoundEvent> ROAR = reg("roar");
    public static final RegistryObject<SoundEvent> IMPACT = reg("impact");
    public static final RegistryObject<SoundEvent> CRACK = reg("crack");
    public static final RegistryObject<SoundEvent> GRAB = reg("grab");
    public static final RegistryObject<SoundEvent> VANISH = reg("vanish");
    public static final RegistryObject<SoundEvent> DEATH = reg("death");
    public static final RegistryObject<SoundEvent> HURT = reg("hurt");
    public static final RegistryObject<SoundEvent> GORE = reg("gore");
    public static final RegistryObject<SoundEvent> RIP = reg("rip");
    public static final RegistryObject<SoundEvent> GUSH = reg("gush");
    public static final RegistryObject<SoundEvent> FINISHER_STING = reg("finisher_sting");
    public static final RegistryObject<SoundEvent> EMERGE_STING = reg("emerge_sting");
    public static final RegistryObject<SoundEvent> JUMPSCARE_SCREAM = reg("jumpscare_scream");
    public static final RegistryObject<SoundEvent> JUMPSCARE_SCREAM_LONG = reg("jumpscare_scream_long");
    public static final RegistryObject<SoundEvent> PAIN = reg("pain");
    public static final RegistryObject<SoundEvent> MUSIC_STALK = reg("music_stalk");
    public static final RegistryObject<SoundEvent> MUSIC_HUNT = reg("music_hunt");
    public static final RegistryObject<SoundEvent> MUSIC_NIGHTMARE = reg("music_nightmare");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AbominationMod.id(name)));
    }
}
