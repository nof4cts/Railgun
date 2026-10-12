package com.firestar.orbital.entity;

import com.firestar.orbital.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** The Inverted: what the blight makes of the dead. Head hanging upside down, arms to the knees, a chest that opens. */
public class Inverted extends BlightMob {
    public Inverted(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 2);
    }

    @Override
    protected void attackGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.INVERTED_IDLE.get();
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
