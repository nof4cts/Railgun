package com.abomination.horror.registry;

import com.abomination.horror.AbominationMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AbominationMod.MODID);

    /** Heavy, falling, splatting blood. */
    public static final RegistryObject<SimpleParticleType> BLOOD = PARTICLES.register("blood", () -> new SimpleParticleType(true));
    /** Black organic matter that drifts and dissolves. */
    public static final RegistryObject<SimpleParticleType> ICHOR = PARTICLES.register("ichor", () -> new SimpleParticleType(true));

    private ModParticles() {}
}
