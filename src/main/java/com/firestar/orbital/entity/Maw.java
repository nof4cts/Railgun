package com.firestar.orbital.entity;

import com.firestar.orbital.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The Maw: a headless brute with a mouth down the length of its chest and claws of bone. Its blows throw you. */
public class Maw extends BlightMob {
    public Maw(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 90).add(Attributes.ATTACK_DAMAGE, 13).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ATTACK_KNOCKBACK, 1.6);
    }

    @Override
    protected void attackGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            Vec3 d = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(d.scale(0.6)).add(0, 0.45, 0));
            target.hurtMarked = true;
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MAW_IDLE.get();
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
        return 1.6F;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.1F;
    }
}
