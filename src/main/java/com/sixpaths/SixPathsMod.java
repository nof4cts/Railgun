package com.sixpaths;

import com.sixpaths.network.PathsNetwork;
import com.sixpaths.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Six Paths — the Rinnegan's techniques as a Forge mod, with shader-driven cinematics. */
@Mod(SixPathsMod.MODID)
public class SixPathsMod {
    public static final String MODID = "sixpaths";

    public SixPathsMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModRegistry::onAttributes);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PathsNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
