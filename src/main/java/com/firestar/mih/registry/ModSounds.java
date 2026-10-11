package com.firestar.mih.registry;

import com.firestar.mih.MihMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MihMod.MODID);

    public static final RegistryObject<SoundEvent> SUMMON = reg("summon");
    public static final RegistryObject<SoundEvent> PUNCH = reg("punch");
    public static final RegistryObject<SoundEvent> JAB = reg("jab");
    public static final RegistryObject<SoundEvent> FINISHER = reg("finisher");
    public static final RegistryObject<SoundEvent> KNIFE_THROW = reg("knife_throw");
    public static final RegistryObject<SoundEvent> KNIFE_HIT = reg("knife_hit");
    public static final RegistryObject<SoundEvent> SLICE = reg("slice");
    public static final RegistryObject<SoundEvent> VANISH = reg("vanish");
    public static final RegistryObject<SoundEvent> TICK = reg("tick");
    public static final RegistryObject<SoundEvent> TOCK = reg("tock");
    public static final RegistryObject<SoundEvent> DOUBLE_ACCEL = reg("double_accel");
    public static final RegistryObject<SoundEvent> JUDGEMENT = reg("judgement");
    public static final RegistryObject<SoundEvent> TIME_ACCEL = reg("time_accel");
    public static final RegistryObject<SoundEvent> RESET_RISE = reg("reset_rise");
    public static final RegistryObject<SoundEvent> SINGULARITY = reg("singularity");
    public static final RegistryObject<SoundEvent> NEW_UNIVERSE = reg("new_universe");
    public static final RegistryObject<SoundEvent> MENACING = reg("menacing");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(MihMod.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
