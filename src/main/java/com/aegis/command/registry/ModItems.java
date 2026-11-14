package com.aegis.command.registry;

import com.aegis.command.AegisCommand;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AegisCommand.MODID);

    public static final RegistryObject<Item> LAPTOP = ITEMS.register("laptop",
            () -> new BlockItem(ModBlocks.LAPTOP.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> PHANTOM_9 = ITEMS.register("phantom_9",
            () -> new AegisWeaponItem(WeaponType.PHANTOM_9, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TITAN_X = ITEMS.register("titan_x",
            () -> new AegisWeaponItem(WeaponType.TITAN_X, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SPECTRE_7 = ITEMS.register("spectre_7",
            () -> new AegisWeaponItem(WeaponType.SPECTRE_7, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static Item weapon(WeaponType type) {
        return switch (type) {
            case PHANTOM_9 -> PHANTOM_9.get();
            case TITAN_X -> TITAN_X.get();
            case SPECTRE_7 -> SPECTRE_7.get();
        };
    }

    private ModItems() {}
}
