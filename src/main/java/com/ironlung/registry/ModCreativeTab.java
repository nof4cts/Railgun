package com.ironlung.registry;

import com.ironlung.IronLungMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    private ModCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IronLungMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ironlung"))
            .icon(() -> new ItemStack(ModItems.DIVE_ORDERS.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(e -> output.accept(e.get())))
            .build());
}
