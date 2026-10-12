package com.firestar.orbital.registry;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.item.Uplink;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OrbitalMod.MODID);
    public static final RegistryObject<Item> UPLINK = ITEMS.register("uplink", Uplink::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ModItems::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || e.getTabKey() == CreativeModeTabs.COMBAT) {
            e.accept(UPLINK);
        }
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            e.accept(ModEntities.INVERTED_EGG);
            e.accept(ModEntities.SKITTER_EGG);
            e.accept(ModEntities.BLOATER_EGG);
            e.accept(ModEntities.MAW_EGG);
        }
        if (e.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            for (var b : ModBlocks.BLOCKS.getEntries()) {
                e.accept(b.get());
            }
        }
    }
}
