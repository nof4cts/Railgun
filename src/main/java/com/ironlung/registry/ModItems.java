package com.ironlung.registry;

import com.ironlung.IronLungMod;
import com.ironlung.item.DiveOrdersItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IronLungMod.MODID);

    public static final RegistryObject<Item> DIVE_ORDERS = ITEMS.register("dive_orders",
            () -> new DiveOrdersItem(new Item.Properties().stacksTo(1)));
}
