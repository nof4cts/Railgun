package com.sixpaths.entity;

import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Three-Headed Hound — every heavy hit it takes splits off another hound (up to four). */
public class HoundEntity extends BeastEntity {
    private static final EntityDataAccessor<Integer> GEN = SynchedEntityData.defineId(HoundEntity.class, EntityDataSerializers.INT);
    private int splitCd;

    public HoundEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setMaxUpStep(1.2f);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(GEN, 0);
    }

    public int generation() {
        return entityData.get(GEN);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && amount >= 3 && splitCd <= 0 && generation() < 3 && isAlive()) {
            splitCd = 30;
            ServerPlayer o = owner();
            if (o != null && getType().create(level()) instanceof HoundEntity pup) {
                pup.moveTo(getX() + (random.nextDouble() - 0.5) * 2, getY(), getZ() + (random.nextDouble() - 0.5) * 2, getYRot(), 0);
                pup.bind(o, life);
                pup.entityData.set(GEN, generation() + 1);
                pup.setHealth(Math.max(10, getHealth() * 0.6f));
                level().addFreshEntity(pup);
                PathsNetwork.fx(this, FxType.HOUND_SPLIT, this, pup, pup.position().add(0, 1, 0), 0, 0);
                sound(SoundEvents.WOLF_GROWL, 1.5f, 0.6f);
            }
        }
        return r;
    }

    @Override
    protected void ai(ServerPlayer o) {
        if (splitCd > 0) splitCd--;
        LivingEntity t = findTarget(o, 32);
        if (t == null) {
            followOwner(o, 4, 1.2);
            return;
        }
        getNavigation().moveTo(t, 1.5);
        faceToward(t.position());
        if (distanceTo(t) < 2.6 && attackCd <= 0) {
            attackCd = 18;
            action(1);
            strike(t, 7f);
            launch(t, t.position().subtract(position()).normalize().scale(0.6).add(0, 0.4, 0));
            PathsNetwork.fx(this, FxType.BEAST_HIT, this, t, t.position().add(0, 1, 0), 0, 0);
            sound(SoundEvents.WOLF_GROWL, 1.2f, 0.8f);
        }
    }
}
