package com.firestar.orbital.entity;

import com.firestar.orbital.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** The Skitter: low, fast, six-legged, a glowing sac on its back. It leaps. */
public class Skitter extends BlightMob {
    public Skitter(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void attackGoals() {
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SKITTER_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return ModSounds.BLIGHT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLIGHT_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }
}
