package com.firestar.santoryu.registry;

import com.firestar.santoryu.SantoryuMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SantoryuMod.MODID);

    public static final RegistryObject<SoundEvent> UNSHEATHE = reg("unsheathe");
    public static final RegistryObject<SoundEvent> SHEATHE = reg("sheathe");
    public static final RegistryObject<SoundEvent> SLASH = reg("slash");
    public static final RegistryObject<SoundEvent> SLASH_HEAVY = reg("slash_heavy");
    public static final RegistryObject<SoundEvent> DASH = reg("dash");
    public static final RegistryObject<SoundEvent> IMPACT = reg("impact");
    public static final RegistryObject<SoundEvent> IMPACT_BIG = reg("impact_big");
    public static final RegistryObject<SoundEvent> TORNADO = reg("tornado");
    public static final RegistryObject<SoundEvent> HAKI = reg("haki");
    public static final RegistryObject<SoundEvent> ASURA = reg("asura");
    public static final RegistryObject<SoundEvent> DRAGON = reg("dragon");
    public static final RegistryObject<SoundEvent> CANNON = reg("cannon");
    public static final RegistryObject<SoundEvent> STING = reg("sting");
    public static final RegistryObject<SoundEvent> TING = reg("ting");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SantoryuMod.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
