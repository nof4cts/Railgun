package com.ironlung.registry;

import com.ironlung.IronLungMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, IronLungMod.MODID);

    public static final RegistryObject<SoundEvent> AMBIENT_DRONE = reg("ambient_drone");
    public static final RegistryObject<SoundEvent> ENGINE_HUM = reg("engine_hum");
    public static final RegistryObject<SoundEvent> ELECTRIC_BUZZ = reg("electric_buzz");
    public static final RegistryObject<SoundEvent> HULL_GROAN = reg("hull_groan");
    public static final RegistryObject<SoundEvent> HULL_CREAK = reg("hull_creak");
    public static final RegistryObject<SoundEvent> HULL_KNOCK = reg("hull_knock");
    public static final RegistryObject<SoundEvent> HULL_IMPACT = reg("hull_impact");
    public static final RegistryObject<SoundEvent> HULL_SCRAPE = reg("hull_scrape");
    public static final RegistryObject<SoundEvent> PRESSURE_HISS = reg("pressure_hiss");
    public static final RegistryObject<SoundEvent> POWER_DOWN = reg("power_down");
    public static final RegistryObject<SoundEvent> POWER_UP = reg("power_up");
    public static final RegistryObject<SoundEvent> BREAKER_CLUNK = reg("breaker_clunk");
    public static final RegistryObject<SoundEvent> CAMERA_SHUTTER = reg("camera_shutter");
    public static final RegistryObject<SoundEvent> CAMERA_DEVELOP = reg("camera_develop");
    public static final RegistryObject<SoundEvent> CAMERA_SERVO = reg("camera_servo");
    public static final RegistryObject<SoundEvent> PROXIMITY_PING = reg("proximity_ping");
    public static final RegistryObject<SoundEvent> ALARM = reg("alarm");
    public static final RegistryObject<SoundEvent> DRIP = reg("drip");
    public static final RegistryObject<SoundEvent> WELD = reg("weld");
    public static final RegistryObject<SoundEvent> LEVIATHAN = reg("leviathan");
    public static final RegistryObject<SoundEvent> STATIC_BURST = reg("static_burst");
    public static final RegistryObject<SoundEvent> HELM_CLICK = reg("helm_click");
    public static final RegistryObject<SoundEvent> IMPLOSION = reg("implosion");
    public static final RegistryObject<SoundEvent> DISTANT_WAIL = reg("distant_wail");
    public static final RegistryObject<SoundEvent> HATCH_KNOCK = reg("hatch_knock");
    public static final RegistryObject<SoundEvent> HATCH_BREACH = reg("hatch_breach");
    public static final RegistryObject<SoundEvent> JUMPSCARE = reg("jumpscare");
    public static final RegistryObject<SoundEvent> BREATHING = reg("breathing");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(IronLungMod.id(name)));
    }
}
