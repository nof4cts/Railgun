package com.cataclysm.spells;

import com.cataclysm.spells.network.SpellNetwork;
import com.cataclysm.spells.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Cataclysm Spells: Falling Star, Event Horizon, Skyfall Lance. */
@Mod(SpellsMod.MODID)
public class SpellsMod {
    public static final String MODID = "cataclysmspells";

    public SpellsMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(bus);
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SpellNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
