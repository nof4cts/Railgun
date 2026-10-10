package com.geto.csm;

import com.geto.csm.entity.*;
import com.geto.csm.item.CurseOrbItem;
import com.geto.csm.item.ManipulationItem;
import com.geto.csm.item.UzumakiItem;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CsmMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CsmMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CsmMod.MODID);

    public static final RegistryObject<Item> MANIPULATION = ITEMS.register("cursed_spirit_manipulation", ManipulationItem::new);
    public static final RegistryObject<Item> UZUMAKI = ITEMS.register("maximum_uzumaki", UzumakiItem::new);
    public static final RegistryObject<Item> CURSE_ORB = ITEMS.register("curse_orb", CurseOrbItem::new);

    public static final RegistryObject<EntityType<WyrmEntity>> WYRM = mob("gloomscale_wyrm", WyrmEntity::new, 2.4f, 2.0f, true);
    public static final RegistryObject<EntityType<WormEntity>> WORM = mob("maw_burrower", WormEntity::new, 2.6f, 5.0f, false);
    public static final RegistryObject<EntityType<RayEntity>> RAY = mob("veil_ray", RayEntity::new, 4.0f, 0.9f, false);
    public static final RegistryObject<EntityType<CentipedeEntity>> CENTIPEDE = mob("cinder_centipede", CentipedeEntity::new, 0.7f, 0.45f, false);
    public static final RegistryObject<EntityType<PyreEntity>> PYRE = mob("pyre_wraith", PyreEntity::new, 1.0f, 2.6f, true);
    public static final RegistryObject<EntityType<KuchisakeEntity>> KUCHISAKE = mob("kuchisake_onna", KuchisakeEntity::new, 0.8f, 3.1f, false);
    public static final RegistryObject<EntityType<TamamoEntity>> TAMAMO = mob("tamamo_no_mae", TamamoEntity::new, 2.8f, 2.4f, true);
    public static final RegistryObject<EntityType<NamazuEntity>> NAMAZU = mob("onamazu", NamazuEntity::new, 3.0f, 1.6f, false);

    private static <T extends PathfinderMob> RegistryObject<EntityType<T>> mob(String name, EntityType.EntityFactory<T> f, float w, float h, boolean fireImmune) {
        return ENTITIES.register(name, () -> {
            EntityType.Builder<T> b = EntityType.Builder.of(f, MobCategory.MISC).sized(w, h).clientTrackingRange(12).updateInterval(1);
            if (fireImmune) b = b.fireImmune();
            return b.build(name);
        });
    }

    public static EntityType<? extends CurseEntity> typeOf(CurseKind k) {
        return switch (k) {
            case WYRM -> WYRM.get();
            case WORM -> WORM.get();
            case RAY -> RAY.get();
            case CENTIPEDE -> CENTIPEDE.get();
            case PYRE -> PYRE.get();
            case KUCHISAKE -> KUCHISAKE.get();
            case TAMAMO -> TAMAMO.get();
            case NAMAZU -> NAMAZU.get();
        };
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("cursemanip", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cursemanip"))
            .icon(() -> new ItemStack(MANIPULATION.get()))
            .displayItems((params, out) -> {
                out.accept(MANIPULATION.get());
                out.accept(UZUMAKI.get());
                out.accept(new ItemStack(CURSE_ORB.get(), 16));
            })
            .build());

    public static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(WYRM.get(), CurseEntity.attributes(160, 0.3, 14).build());
        e.put(WORM.get(), CurseEntity.attributes(80, 0.0, 8).build());
        e.put(RAY.get(), CurseEntity.attributes(60, 0.3, 3).build());
        e.put(CENTIPEDE.get(), CurseEntity.attributes(20, 0.38, 3).build());
        e.put(PYRE.get(), CurseEntity.attributes(90, 0.22, 6).build());
        e.put(KUCHISAKE.get(), CurseEntity.attributes(120, 0.3, 8).build());
        e.put(TAMAMO.get(), CurseEntity.attributes(200, 0.3, 10).build());
        e.put(NAMAZU.get(), CurseEntity.attributes(140, 0.2, 9).build());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
