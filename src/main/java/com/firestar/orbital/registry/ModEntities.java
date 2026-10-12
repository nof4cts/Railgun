package com.firestar.orbital.registry;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.entity.Bloater;
import com.firestar.orbital.entity.Inverted;
import com.firestar.orbital.entity.Maw;
import com.firestar.orbital.entity.Skitter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** What crawls out of Antipode's blight. */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OrbitalMod.MODID);

    public static final RegistryObject<EntityType<Inverted>> INVERTED = ENTITIES.register("inverted",
            () -> EntityType.Builder.of(Inverted::new, MobCategory.MONSTER).sized(0.7F, 2.1F).clientTrackingRange(10).build("inverted"));
    public static final RegistryObject<EntityType<Skitter>> SKITTER = ENTITIES.register("skitter",
            () -> EntityType.Builder.of(Skitter::new, MobCategory.MONSTER).sized(1.3F, 0.7F).clientTrackingRange(10).build("skitter"));
    public static final RegistryObject<EntityType<Bloater>> BLOATER = ENTITIES.register("bloater",
            () -> EntityType.Builder.of(Bloater::new, MobCategory.MONSTER).sized(1.3F, 1.5F).clientTrackingRange(10).build("bloater"));
    public static final RegistryObject<EntityType<Maw>> MAW = ENTITIES.register("maw",
            () -> EntityType.Builder.of(Maw::new, MobCategory.MONSTER).sized(1.7F, 3.0F).clientTrackingRange(12).build("maw"));

    public static final RegistryObject<Item> INVERTED_EGG = egg("inverted", INVERTED, 0x5A1832, 0xFF4FB0);
    public static final RegistryObject<Item> SKITTER_EGG = egg("skitter", SKITTER, 0x2A0E22, 0x3FD6C8);
    public static final RegistryObject<Item> BLOATER_EGG = egg("bloater", BLOATER, 0x7A2048, 0xE8B0D0);
    public static final RegistryObject<Item> MAW_EGG = egg("maw", MAW, 0x30101C, 0xF0E0C8);

    private static RegistryObject<Item> egg(String name, RegistryObject<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type, int a, int b) {
        return ModBlocks.ITEMS.register(name + "_spawn_egg", () -> new ForgeSpawnEggItem(type, a, b, new Item.Properties()));
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(INVERTED.get(), Inverted.attributes().build());
        e.put(SKITTER.get(), Skitter.attributes().build());
        e.put(BLOATER.get(), Bloater.attributes().build());
        e.put(MAW.get(), Maw.attributes().build());
    }
}
