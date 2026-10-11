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

    public static final RegistryObject<SoundEvent> BASS = reg("bass");
    public static final RegistryObject<SoundEvent> DASH = reg("dash");
    public static final RegistryObject<SoundEvent> STAND_SUMMON = reg("stand_summon");
    public static final RegistryObject<SoundEvent> STAND_DISMISS = reg("stand_dismiss");
    public static final RegistryObject<SoundEvent> FIST = reg("fist");
    public static final RegistryObject<SoundEvent> BARRAGE_RUSH = reg("barrage_rush");
    public static final RegistryObject<SoundEvent> KNIFE_HANG = reg("knife_hang");
    public static final RegistryObject<SoundEvent> KNIFE_VOLLEY = reg("knife_volley");
    public static final RegistryObject<SoundEvent> KNIFE_FLESH = reg("knife_flesh");
    public static final RegistryObject<SoundEvent> JUDGE_CUT = reg("judge_cut");
    public static final RegistryObject<SoundEvent> INFINITE_START = reg("infinite_start");
    public static final RegistryObject<SoundEvent> DETONATE = reg("detonate");
    public static final RegistryObject<SoundEvent> RUSH = reg("rush");
    public static final RegistryObject<SoundEvent> LIGHT_HIT = reg("light_hit");
    public static final RegistryObject<SoundEvent> CRACK = reg("crack");
    public static final RegistryObject<SoundEvent> GALAXY = reg("galaxy");
    public static final RegistryObject<SoundEvent> PLUNGE = reg("plunge");
    public static final RegistryObject<SoundEvent> ACCEL_BASS = reg("accel_bass");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(MihMod.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
