package com.firestar.santoryu;

import com.firestar.santoryu.config.ZConfig;
import com.firestar.santoryu.network.Net;
import com.firestar.santoryu.registry.ModItems;
import com.firestar.santoryu.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SantoryuMod {
    public static final String MODID = "santoryu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Santoryu");

    public SantoryuMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(bus);
        ModSounds.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZConfig.CLIENT_SPEC, "santoryu-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ZConfig.SERVER_SPEC, "santoryu-server.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
