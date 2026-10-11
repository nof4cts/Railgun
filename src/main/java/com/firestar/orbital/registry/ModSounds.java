package com.firestar.orbital.registry;

import com.firestar.orbital.OrbitalMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, OrbitalMod.MODID);

    public static final RegistryObject<SoundEvent> SELECT = reg("select");
    public static final RegistryObject<SoundEvent> ARM = reg("arm");
    public static final RegistryObject<SoundEvent> COUNTDOWN = reg("countdown");
    public static final RegistryObject<SoundEvent> ASCENT = reg("ascent");
    public static final RegistryObject<SoundEvent> HELIOS_UNFOLD = reg("helios_unfold");
    public static final RegistryObject<SoundEvent> HELIOS_CHARGE = reg("helios_charge");
    public static final RegistryObject<SoundEvent> HELIOS_FIRE = reg("helios_fire");
    public static final RegistryObject<SoundEvent> HELIOS_BEAM = reg("helios_beam");
    public static final RegistryObject<SoundEvent> HELIOS_IMPACT = reg("helios_impact");
    public static final RegistryObject<SoundEvent> ANTIPODE_CRACK = reg("antipode_crack");
    public static final RegistryObject<SoundEvent> ANTIPODE_BREACH = reg("antipode_breach");
    public static final RegistryObject<SoundEvent> ANTIPODE_APPROACH = reg("antipode_approach");
    public static final RegistryObject<SoundEvent> ANTIPODE_CONTACT = reg("antipode_contact");
    public static final RegistryObject<SoundEvent> ANTIPODE_AFTER = reg("antipode_after");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(OrbitalMod.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
