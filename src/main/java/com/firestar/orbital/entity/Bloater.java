package com.firestar.orbital.entity;

import com.firestar.orbital.registry.ModSounds;
import com.firestar.orbital.server.Blight;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Bloater: a swollen sac on stubby legs. Near its prey it swells, and bursts: everything close is hurt and sickened,
 * and the ground round where it stood turns to blight.
 */
public class Bloater extends BlightMob {
    private static final EntityDataAccessor<Integer> SWELL = SynchedEntityData.defineId(Bloater.class, EntityDataSerializers.INT);
    public static final int FUSE = 36;
    private int swellO;

    public Bloater(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SWELL, 0);
    }

    @Override
    protected void attackGoals() {
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
    }

    public float swell(float partial) {
        return Math.min(1.0F, (swellO + (entityData.get(SWELL) - swellO) * partial) / FUSE);
    }

    @Override
    public void tick() {
        swellO = entityData.get(SWELL);
        super.tick();
        if (level().isClientSide || !isAlive()) {
            return;
        }
        LivingEntity t = getTarget();
        int sw = entityData.get(SWELL);
        if (t != null && t.isAlive() && distanceToSqr(t) < 9.0) {
            if (sw == 0) {
                level().playSound(null, blockPosition(), ModSounds.BLOATER_SWELL.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
            }
            sw++;
        } else if (sw > 0) {
            sw--;
        }
        entityData.set(SWELL, sw);
        if (sw >= FUSE) {
            burst();
        }
    }

    private void burst() {
        ServerLevel l = (ServerLevel) level();
        l.playSound(null, blockPosition(), ModSounds.BLOATER_BURST.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, getX(), getY() + 0.8, getZ(), 80, 1.6, 0.8, 1.6, 0.05);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 4, 0.6, 0.4, 0.6, 0.0);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(4.5), e -> !(e instanceof BlightMob))) {
            float k = (float) (1.0 - Math.sqrt(e.distanceToSqr(this)) / 5.0);
            if (k > 0) {
                e.hurt(damageSources().mobAttack(this), 12.0F * k + 2.0F);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), this);
            }
        }
        // the ground round it is the other world's now, and spreads from here
        BlockPos c = blockPosition();
        java.util.Random r = new java.util.Random();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz <= 16 && r.nextFloat() < 0.85F) {
                    Blight.infect(l, c.getX() + dx, c.getZ() + dz, r);
                }
            }
        }
        Blight.seed(l, c, 4.0F);
        discard();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BLOATER_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return ModSounds.BLIGHT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLIGHT_DEATH.get();
    }
}
