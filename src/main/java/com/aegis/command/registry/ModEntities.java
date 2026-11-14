package com.aegis.command.registry;

import com.aegis.command.AegisCommand;
import com.aegis.command.entity.AerialBomb;
import com.aegis.command.entity.CinematicCamera;
import com.aegis.command.entity.EnergyBolt;
import com.aegis.command.entity.StrikeJet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AegisCommand.MODID);

    public static final RegistryObject<EntityType<StrikeJet>> STRIKE_JET = ENTITIES.register("strike_jet",
            () -> EntityType.Builder.<StrikeJet>of(StrikeJet::new, MobCategory.MISC)
                    .sized(6f, 2f).clientTrackingRange(16).updateInterval(1).fireImmune().noSummon()
                    .build("strike_jet"));

    public static final RegistryObject<EntityType<AerialBomb>> AERIAL_BOMB = ENTITIES.register("aerial_bomb",
            () -> EntityType.Builder.<AerialBomb>of(AerialBomb::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(16).updateInterval(1).fireImmune().noSummon()
                    .build("aerial_bomb"));

    public static final RegistryObject<EntityType<EnergyBolt>> ENERGY_BOLT = ENTITIES.register("energy_bolt",
            () -> EntityType.Builder.<EnergyBolt>of(EnergyBolt::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).fireImmune().noSummon()
                    .build("energy_bolt"));

    public static final RegistryObject<EntityType<CinematicCamera>> CINEMATIC_CAMERA = ENTITIES.register("cinematic_camera",
            () -> EntityType.Builder.<CinematicCamera>of(CinematicCamera::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f).clientTrackingRange(0).noSave().noSummon()
                    .build("cinematic_camera"));

    private ModEntities() {}
}
