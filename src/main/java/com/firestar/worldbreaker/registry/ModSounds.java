package com.firestar.worldbreaker.registry;

import com.firestar.worldbreaker.WorldbreakerMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WorldbreakerMod.MODID);

    public static final RegistryObject<SoundEvent> FOOTFALL = reg("footfall");
    public static final RegistryObject<SoundEvent> RUMBLE = reg("rumble");
    public static final RegistryObject<SoundEvent> HORN = reg("horn");
    public static final RegistryObject<SoundEvent> STEAM = reg("steam");
    public static final RegistryObject<SoundEvent> CRUMBLE = reg("crumble");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> IMPACT = reg("impact");
    public static final RegistryObject<SoundEvent> RISE = reg("rise");
    public static final RegistryObject<SoundEvent> WIND = reg("wind");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(WorldbreakerMod.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
