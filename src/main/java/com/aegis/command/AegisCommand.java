package com.aegis.command;

import com.aegis.command.network.AegisNetwork;
import com.aegis.command.registry.ModBlocks;
import com.aegis.command.registry.ModEntities;
import com.aegis.command.registry.ModItems;
import com.aegis.command.registry.ModTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AegisCommand.MODID)
public class AegisCommand {
    public static final String MODID = "aegiscommand";

    public AegisCommand() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModTabs.TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AegisConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AegisConfig.CLIENT_SPEC);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(AegisNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
