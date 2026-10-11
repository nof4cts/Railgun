package com.firestar.santoryu.registry;

import com.firestar.santoryu.SantoryuMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SantoryuMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SantoryuMod.MODID);

    public static final RegistryObject<KatanaItem> WADO = ITEMS.register("wado_ichimonji", () -> new KatanaItem(
            Tiers.DIAMOND, 4, -2.2F, new Item.Properties().rarity(Rarity.EPIC).fireResistant(),
            "wado", 1.0F, 0xF2F2F2, "A white blade, sworn on a promise to a friend."));
    public static final RegistryObject<KatanaItem> KITETSU = ITEMS.register("sandai_kitetsu", () -> new KatanaItem(
            Tiers.DIAMOND, 5, -2.2F, new Item.Properties().rarity(Rarity.EPIC).fireResistant(),
            "kitetsu", 1.1F, 0xB0142B, "A cursed blade with a crimson hamon. It is said to bring ruin to its wielder."));
    public static final RegistryObject<KatanaItem> ENMA = ITEMS.register("enma", () -> new KatanaItem(
            Tiers.NETHERITE, 6, -2.2F, new Item.Properties().rarity(Rarity.EPIC).fireResistant(),
            "enma", 1.25F, 0x6A2BC2, "A blade that devours the haki of whoever holds it. Only a king can tame it."));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("blades", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.santoryu"))
            .icon(() -> new ItemStack(ENMA.get()))
            .displayItems((params, out) -> {
                out.accept(WADO.get());
                out.accept(KITETSU.get());
                out.accept(ENMA.get());
            })
            .build());

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
