package com.aegis.command.registry;

import com.aegis.command.AegisCommand;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AegisCommand.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aegiscommand"))
            .icon(() -> new ItemStack(ModItems.LAPTOP.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.LAPTOP.get());
                out.accept(ModItems.PHANTOM_9.get());
                out.accept(ModItems.SPECTRE_7.get());
                out.accept(ModItems.TITAN_X.get());
            })
            .build());

    private ModTabs() {}
}
