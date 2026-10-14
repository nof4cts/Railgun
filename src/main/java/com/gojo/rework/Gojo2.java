package com.gojo.rework;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Gojo, reworked: Reversal Red that craters the land, Maximum Output Blue and Red, the four Purples (Hanami's, the
 * imaginary mass aimed at Toji, the 200% Purple and the Hollow Nuke), Unlimited Void with its banner and the void
 * itself, the 0.2-second domain, and a new look for Infinity. Brought up from GojoMod's constructor.
 */
public final class Gojo2 {
    public static final String NS = "gojo";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NS);

    public static final RegistryObject<SoundEvent> RED_CHARGE = sound("red_charge");
    public static final RegistryObject<SoundEvent> RED_FIRE = sound("red_fire");
    public static final RegistryObject<SoundEvent> RED_IMPACT = sound("red_impact");
    public static final RegistryObject<SoundEvent> BLUE_CHARGE = sound("blue_charge");
    public static final RegistryObject<SoundEvent> BLUE_DRAG = sound("blue_drag");
    public static final RegistryObject<SoundEvent> BLUE_CRUSH = sound("blue_crush");
    public static final RegistryObject<SoundEvent> MAXRED_FIRE = sound("maxred_fire");
    public static final RegistryObject<SoundEvent> PURPLE_MERGE = sound("purple_merge");
    public static final RegistryObject<SoundEvent> PURPLE_FIRE = sound("purple_fire");
    public static final RegistryObject<SoundEvent> PURPLE_BIG = sound("purple_big");
    public static final RegistryObject<SoundEvent> CHANT = sound("chant");
    public static final RegistryObject<SoundEvent> NUKE_CHARGE = sound("nuke_charge");
    public static final RegistryObject<SoundEvent> NUKE_BLAST = sound("nuke_blast");
    public static final RegistryObject<SoundEvent> DOMAIN_SIGN = sound("domain_sign");
    public static final RegistryObject<SoundEvent> DOMAIN_OPEN = sound("domain_open");
    public static final RegistryObject<SoundEvent> DOMAIN_VOID = sound("domain_void");
    public static final RegistryObject<SoundEvent> INFINITY_ON = sound("infinity_on");
    public static final RegistryObject<SoundEvent> INFINITY_RIPPLE = sound("infinity_ripple");

    private Gojo2() {}

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        com.gojo.rework.net.GNet.register();
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NS, path);
    }
}
