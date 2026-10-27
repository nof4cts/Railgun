package com.abomination.horror.registry;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.item.LoreItem;
import com.abomination.horror.item.ThrobbingHeartItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AbominationMod.MODID);

    public static final RegistryObject<Item> HEART_FRAGMENT = ITEMS.register("heart_fragment",
            () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), false));
    public static final RegistryObject<Item> THROBBING_HEART = ITEMS.register("throbbing_heart",
            () -> new ThrobbingHeartItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ABOMINATION_SPINE = ITEMS.register("abomination_spine",
            () -> new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true));
    public static final RegistryObject<Item> FLESH_GROWTH = ITEMS.register("flesh_growth",
            () -> new BlockItem(ModBlocks.FLESH_GROWTH.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPAWN_EGG = ITEMS.register("abomination_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ABOMINATION, 0x1A0505, 0x8B0000, new Item.Properties()));

    private ModItems() {}

    public static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPAWN_EGG.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(HEART_FRAGMENT.get());
            event.accept(THROBBING_HEART.get());
            event.accept(ABOMINATION_SPINE.get());
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(FLESH_GROWTH.get());
        }
    }
}
