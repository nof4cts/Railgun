package com.firestar.silver;

import com.firestar.silver.config.ZConfig;
import com.firestar.silver.network.Net;
import com.firestar.silver.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SilverMod {
    public static final String MODID = "silverinstinct";
    public static final Logger LOGGER = LoggerFactory.getLogger("SilverInstinct");

    public SilverMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZConfig.CLIENT_SPEC, "silverinstinct-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ZConfig.SERVER_SPEC, "silverinstinct-server.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
