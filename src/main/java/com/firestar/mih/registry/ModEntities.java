package com.firestar.mih.registry;

import com.firestar.mih.MihMod;
import com.firestar.mih.server.KnifeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MihMod.MODID);

    public static final RegistryObject<EntityType<KnifeEntity>> KNIFE = ENTITIES.register("knife",
            () -> EntityType.Builder.<KnifeEntity>of(KnifeEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(8).updateInterval(1).build("knife"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
