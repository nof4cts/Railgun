package com.hakari.idg;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HakariMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HakariMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HakariMod.MODID);

    public static final RegistryObject<Item> RESERVE_BALL = ITEMS.register("scroll_reserve_ball", () -> new TechniqueScrollItem(Technique.RESERVE_BALL));
    public static final RegistryObject<Item> SHUTTER_DOORS = ITEMS.register("scroll_shutter_doors", () -> new TechniqueScrollItem(Technique.SHUTTER_DOORS));
    public static final RegistryObject<Item> ROUGH_ENERGY = ITEMS.register("scroll_rough_energy", () -> new TechniqueScrollItem(Technique.ROUGH_ENERGY));
    public static final RegistryObject<Item> FEVER_BREAKER = ITEMS.register("scroll_fever_breaker", () -> new TechniqueScrollItem(Technique.FEVER_BREAKER));
    public static final RegistryObject<Item> COUNTER = ITEMS.register("scroll_counter", () -> new TechniqueScrollItem(Technique.COUNTER));
    public static final RegistryObject<Item> DOMAIN = ITEMS.register("scroll_domain", () -> new TechniqueScrollItem(Technique.DOMAIN));

    public static final List<RegistryObject<Item>> MOVE_SCROLLS = List.of(RESERVE_BALL, SHUTTER_DOORS, ROUGH_ENERGY, FEVER_BREAKER, COUNTER);

    public static final RegistryObject<EntityType<ReserveBallEntity>> RESERVE_BALL_ENTITY = ENTITIES.register("reserve_ball",
            () -> EntityType.Builder.<ReserveBallEntity>of(ReserveBallEntity::new, MobCategory.MISC)
                    .sized(0.35f, 0.35f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("reserve_ball"));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("hakari", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hakari"))
            .icon(() -> new ItemStack(DOMAIN.get()))
            .displayItems((params, output) -> {
                for (RegistryObject<Item> item : ITEMS.getEntries()) output.accept(item.get());
            })
            .build());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
