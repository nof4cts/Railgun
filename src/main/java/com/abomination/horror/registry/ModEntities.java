package com.abomination.horror.registry;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.entity.AbominationEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AbominationMod.MODID);

    public static final RegistryObject<EntityType<AbominationEntity>> ABOMINATION = ENTITIES.register("abomination",
            () -> EntityType.Builder.<AbominationEntity>of(AbominationEntity::new, MobCategory.MONSTER)
                    .sized(1.1F, 3.6F)
                    .clientTrackingRange(12)
                    .build(AbominationMod.id("abomination").toString()));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ABOMINATION.get(), AbominationEntity.createAttributes().build());
    }

    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ABOMINATION.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbominationEntity::checkAbominationSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
