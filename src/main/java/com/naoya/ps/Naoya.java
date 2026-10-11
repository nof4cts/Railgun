package com.naoya.ps;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Naoya Zen'in: Projection Sorcery. Created by the JJK core like every other sorcerer. */
public final class Naoya {
    public static final String MODID = "naoya";
    public static final Logger LOGGER = LoggerFactory.getLogger("Naoya");

    public Naoya() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Sounds.register(bus);
        Net.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
