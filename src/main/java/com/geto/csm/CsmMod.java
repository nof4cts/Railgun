package com.geto.csm;

import com.geto.csm.network.CsmNetwork;
import com.geto.csm.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Cursed Spirit Manipulation: exorcise, swallow, summon, combine.
 * All creatures are smooth cel-shaded procedural geometry; no vanilla particles anywhere.
 */
@Mod(CsmMod.MODID)
public class CsmMod {
    public static final String MODID = "cursemanip";

    public CsmMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModRegistry::onAttributes);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CsmNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
