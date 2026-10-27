package com.abomination.horror.registry;

import com.abomination.horror.AbominationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ModDamage {
    /** Defined in data/abomination/damage_type/spine_rip.json and tagged to bypass armor, totems and invulnerability. */
    public static final ResourceKey<DamageType> SPINE_RIP = ResourceKey.create(Registries.DAMAGE_TYPE, AbominationMod.id("spine_rip"));

    private ModDamage() {}

    public static DamageSource spineRip(Level level, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SPINE_RIP), attacker);
    }
}
