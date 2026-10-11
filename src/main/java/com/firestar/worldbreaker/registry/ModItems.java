package com.firestar.worldbreaker.registry;

import com.firestar.worldbreaker.WorldbreakerMod;
import com.firestar.worldbreaker.item.HeraldsHorn;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, WorldbreakerMod.MODID);
    public static final RegistryObject<Item> HERALDS_HORN = ITEMS.register("heralds_horn", HeraldsHorn::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ModItems::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || e.getTabKey() == CreativeModeTabs.COMBAT) {
            e.accept(HERALDS_HORN);
        }
    }
}
