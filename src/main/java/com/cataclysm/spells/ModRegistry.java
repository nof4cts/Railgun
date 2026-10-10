package com.cataclysm.spells;

import com.cataclysm.spells.item.SpellItem;
import com.cataclysm.spells.server.Spells;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SpellsMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpellsMod.MODID);

    public static final RegistryObject<Item> FALLING_STAR = ITEMS.register("spell_falling_star", () -> new SpellItem(Spells.Spell.FALLING_STAR));
    public static final RegistryObject<Item> EVENT_HORIZON = ITEMS.register("spell_event_horizon", () -> new SpellItem(Spells.Spell.EVENT_HORIZON));
    public static final RegistryObject<Item> SKYFALL_LANCE = ITEMS.register("spell_skyfall_lance", () -> new SpellItem(Spells.Spell.SKYFALL_LANCE));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("cataclysmspells", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cataclysmspells"))
            .icon(() -> new ItemStack(FALLING_STAR.get()))
            .displayItems((params, out) -> {
                out.accept(FALLING_STAR.get());
                out.accept(EVENT_HORIZON.get());
                out.accept(SKYFALL_LANCE.get());
            })
            .build());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
