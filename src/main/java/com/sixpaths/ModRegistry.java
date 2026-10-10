package com.sixpaths;

import com.sixpaths.entity.*;
import com.sixpaths.item.PathFocusItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SixPathsMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SixPathsMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SixPathsMod.MODID);

    public static final RegistryObject<Item> FOCUS = ITEMS.register("path_focus", PathFocusItem::new);

    public static final RegistryObject<EntityType<RhinoEntity>> RHINO = mob("horned_behemoth", RhinoEntity::new, 2.6f, 2.4f);
    public static final RegistryObject<EntityType<CentipedeEntity>> CENTIPEDE = mob("great_centipede", CentipedeEntity::new, 1.6f, 1.2f);
    public static final RegistryObject<EntityType<HoundEntity>> HOUND = mob("three_headed_hound", HoundEntity::new, 1.6f, 1.8f);
    public static final RegistryObject<EntityType<BirdEntity>> BIRD = mob("sky_roc", BirdEntity::new, 3.0f, 1.6f);

    public static final RegistryObject<EntityType<RodEntity>> ROD = ENTITIES.register("chakra_rod",
            () -> EntityType.Builder.<RodEntity>of(RodEntity::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(1).build("chakra_rod"));
    public static final RegistryObject<EntityType<MissileEntity>> MISSILE = ENTITIES.register("asura_missile",
            () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("asura_missile"));

    private static <T extends PathfinderMob> RegistryObject<EntityType<T>> mob(String name, EntityType.EntityFactory<T> f, float w, float h) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(f, MobCategory.MISC).sized(w, h).clientTrackingRange(12).updateInterval(1).fireImmune().build(name));
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("sixpaths", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sixpaths"))
            .icon(() -> new ItemStack(FOCUS.get()))
            .displayItems((params, out) -> out.accept(FOCUS.get()))
            .build());

    public static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(RHINO.get(), BeastEntity.attributes(160, 0.3, 14).build());
        e.put(CENTIPEDE.get(), BeastEntity.attributes(120, 0.34, 9).build());
        e.put(HOUND.get(), BeastEntity.attributes(80, 0.36, 7).build());
        e.put(BIRD.get(), BeastEntity.attributes(90, 0.3, 10).build());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
