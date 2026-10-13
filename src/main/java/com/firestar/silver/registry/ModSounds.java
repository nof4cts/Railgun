package com.firestar.silver.registry;

import com.firestar.silver.SilverMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SilverMod.MODID);

    public static final RegistryObject<SoundEvent> AURA_HUM = reg("aura_hum");
    public static final RegistryObject<SoundEvent> AWAKEN_OMEN = reg("awaken_omen");
    public static final RegistryObject<SoundEvent> AWAKEN_ZENITH = reg("awaken_zenith");
    public static final RegistryObject<SoundEvent> BASS = reg("bass");
    public static final RegistryObject<SoundEvent> BLAST = reg("blast");
    public static final RegistryObject<SoundEvent> BLAST_HIT = reg("blast_hit");
    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> COUNTER = reg("counter");
    public static final RegistryObject<SoundEvent> DODGE = reg("dodge");
    public static final RegistryObject<SoundEvent> FINAL_BOOM = reg("final_boom");
    public static final RegistryObject<SoundEvent> FINAL_CHARGE = reg("final_charge");
    public static final RegistryObject<SoundEvent> FINAL_FIRE = reg("final_fire");
    public static final RegistryObject<SoundEvent> FLURRY = reg("flurry");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> HEAVY = reg("heavy");
    public static final RegistryObject<SoundEvent> LAUNCH = reg("launch");
    public static final RegistryObject<SoundEvent> REVERT = reg("revert");
    public static final RegistryObject<SoundEvent> SLAM = reg("slam");
    public static final RegistryObject<SoundEvent> SPHERE_BOOM = reg("sphere_boom");
    public static final RegistryObject<SoundEvent> SPHERE_GATHER = reg("sphere_gather");
    public static final RegistryObject<SoundEvent> SPHERE_THROW = reg("sphere_throw");
    public static final RegistryObject<SoundEvent> STRIKE = reg("strike");
    public static final RegistryObject<SoundEvent> SWING = reg("swing");
    public static final RegistryObject<SoundEvent> VANISH = reg("vanish");
    public static final RegistryObject<SoundEvent> WAVE_FIRE = reg("wave_fire");
    /** The author's own track, played while the player is in Silver Zenith. */
    public static final RegistryObject<SoundEvent> ZENITH_THEME = reg("zenith_theme");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SilverMod.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
