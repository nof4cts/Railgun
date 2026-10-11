package com.firestar.orbital;

import com.firestar.orbital.config.OConfig;
import com.firestar.orbital.network.Net;
import com.firestar.orbital.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(OrbitalMod.MODID)
public final class OrbitalMod {
    public static final String MODID = "orbital_arsenal";
    public static final Logger LOGGER = LoggerFactory.getLogger("OrbitalArsenal");

    public OrbitalMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.register(bus);
        com.firestar.orbital.registry.ModItems.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, OConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, OConfig.SERVER_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
