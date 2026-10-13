package com.firestar.rose;

import com.firestar.rose.net.RoseNet;
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
 * Requiem Rose: the divine reaper. A rose-and-black aura (ported, with its MIT notice, from Dragon Block Rebirth by
 * Medrado26), an energy scythe, and its own techniques, cutscenes, renderer and sound.
 */
public final class RoseMod {
    public static final String NS = "requiem";
    public static final Logger LOGGER = LoggerFactory.getLogger("RequiemRose");
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NS);

    public static final RegistryObject<SoundEvent> AURA_LOOP = sound("aura_loop");
    public static final RegistryObject<SoundEvent> AURA_IGNITE = sound("aura_ignite");
    public static final RegistryObject<SoundEvent> AURA_RELEASE = sound("aura_release");
    public static final RegistryObject<SoundEvent> RAY_CHARGE = sound("ray_charge");
    public static final RegistryObject<SoundEvent> RAY_FIRE = sound("ray_fire");
    public static final RegistryObject<SoundEvent> RAY_END = sound("ray_end");
    public static final RegistryObject<SoundEvent> SWING = sound("swing");
    public static final RegistryObject<SoundEvent> SWING_HEAVY = sound("swing_heavy");
    public static final RegistryObject<SoundEvent> CUT = sound("cut");
    public static final RegistryObject<SoundEvent> CUT_HEAVY = sound("cut_heavy");
    public static final RegistryObject<SoundEvent> CRESCENT_THROW = sound("crescent_throw");
    public static final RegistryObject<SoundEvent> CRESCENT_SPIN = sound("crescent_spin");
    public static final RegistryObject<SoundEvent> CRESCENT_CATCH = sound("crescent_catch");
    public static final RegistryObject<SoundEvent> TETHER_FIRE = sound("tether_fire");
    public static final RegistryObject<SoundEvent> TETHER_BIND = sound("tether_bind");
    public static final RegistryObject<SoundEvent> TETHER_YANK = sound("tether_yank");
    public static final RegistryObject<SoundEvent> VANISH = sound("vanish");
    public static final RegistryObject<SoundEvent> REQUIEM_BURST = sound("requiem_burst");
    public static final RegistryObject<SoundEvent> REQUIEM_GATHER = sound("requiem_gather");
    public static final RegistryObject<SoundEvent> RIFT_TEAR = sound("rift_tear");
    public static final RegistryObject<SoundEvent> RIFT_HUM = sound("rift_hum");
    public static final RegistryObject<SoundEvent> RIFT_SNAP = sound("rift_snap");
    public static final RegistryObject<SoundEvent> BLOOM_RISE = sound("bloom_rise");
    public static final RegistryObject<SoundEvent> BLOOM_SKY = sound("bloom_sky");
    public static final RegistryObject<SoundEvent> BLOOM_GROW = sound("bloom_grow");
    public static final RegistryObject<SoundEvent> BLOOM_CUT = sound("bloom_cut");
    public static final RegistryObject<SoundEvent> BLOOM_IMPACT = sound("bloom_impact");
    public static final RegistryObject<SoundEvent> SUB = sound("sub");
    public static final RegistryObject<SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final RegistryObject<SoundEvent> SUMMON = sound("summon");

    public RoseMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUNDS.register(bus);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RoseConfig.CLIENT_SPEC, "requiem-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RoseConfig.SERVER_SPEC, "requiem-server.toml");
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(RoseNet::register);
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NS, path);
    }
}
