package com.firestar.sage;

import com.firestar.sage.net.SageNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Celestial Sage: a mortal who takes up the heavens. Nine null orbs, a black staff ringed in gold, a halo, and the
 * sky itself. Its own rites, renderer, camera, animations and sound.
 */
public final class SageMod {
    public static final String NS = "celestial";
    public static final Logger LOGGER = LoggerFactory.getLogger("CelestialSage");
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NS);

    public static final RegistryObject<SoundEvent> MANTLE_GATHER = sound("mantle_gather");
    public static final RegistryObject<SoundEvent> MANTLE_IGNITE = sound("mantle_ignite");
    public static final RegistryObject<SoundEvent> MANTLE_RELEASE = sound("mantle_release");
    public static final RegistryObject<SoundEvent> HUM = sound("hum");
    public static final RegistryObject<SoundEvent> STAFF_SWING = sound("staff_swing");
    public static final RegistryObject<SoundEvent> STAFF_HIT = sound("staff_hit");
    public static final RegistryObject<SoundEvent> ORB_FIRE = sound("orb_fire");
    public static final RegistryObject<SoundEvent> ORB_ERASE = sound("orb_erase");
    public static final RegistryObject<SoundEvent> PULL_BIND = sound("pull_bind");
    public static final RegistryObject<SoundEvent> PULL_BURST = sound("pull_burst");
    public static final RegistryObject<SoundEvent> MIRROR_VANISH = sound("mirror_vanish");
    public static final RegistryObject<SoundEvent> MIRROR_STRIKE = sound("mirror_strike");
    public static final RegistryObject<SoundEvent> STAR_CALL = sound("star_call");
    public static final RegistryObject<SoundEvent> STAR_ROAR = sound("star_roar");
    public static final RegistryObject<SoundEvent> STAR_IMPACT = sound("star_impact");
    public static final RegistryObject<SoundEvent> THORN_ERUPT = sound("thorn_erupt");
    public static final RegistryObject<SoundEvent> COLOSSUS_RISE = sound("colossus_rise");
    public static final RegistryObject<SoundEvent> COLOSSUS_CUT = sound("colossus_cut");
    public static final RegistryObject<SoundEvent> ECLIPSE_COVER = sound("eclipse_cover");
    public static final RegistryObject<SoundEvent> ECLIPSE_RING = sound("eclipse_ring");
    public static final RegistryObject<SoundEvent> BELL = sound("bell");

    public SageMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUNDS.register(bus);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SageConfig.SERVER_SPEC, "celestial-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SageConfig.CLIENT_SPEC, "celestial-client.toml");
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(SageNet::register);
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NS, path);
    }
}
