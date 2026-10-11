package com.firestar.mih;

import com.firestar.mih.config.ZConfig;
import com.firestar.mih.network.Net;
import com.firestar.mih.registry.ModEntities;
import com.firestar.mih.registry.ModItems;
import com.firestar.mih.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MihMod.MODID)
public final class MihMod {
    public static final String MODID = "madeinheaven";
    public static final Logger LOGGER = LoggerFactory.getLogger("MadeInHeaven");

    public MihMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(bus);
        ModEntities.register(bus);
        ModSounds.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::tabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ZConfig.SERVER_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    private void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.DISC);
            event.accept(ModItems.KNIFE);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
