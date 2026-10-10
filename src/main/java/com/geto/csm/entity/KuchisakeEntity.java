package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.KuchisakeGame;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Kuchisake-onna, built from the folk legend: a tall woman in a long coat and a mask,
 * carrying a giant pair of shears, who traps her victim in a binding vow and asks one question.
 */
public class KuchisakeEntity extends CurseEntity {
    private static final EntityDataAccessor<Boolean> UNMASKED = SynchedEntityData.defineId(KuchisakeEntity.class, EntityDataSerializers.BOOLEAN);

    public int askCooldown = 20;

    public KuchisakeEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(UNMASKED, false);
    }

    public boolean unmasked() {
        return entityData.get(UNMASKED);
    }

    public void setUnmasked(boolean b) {
        entityData.set(UNMASKED, b);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.KUCHISAKE;
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        if (askCooldown > 0) askCooldown--;
        LivingEntity t = findTarget(o, 30);
        if (t == null) {
            followOwner(o, 4, 1.0, false);
            return;
        }
        double d = distanceTo(t);
        if (askCooldown <= 0 && d < 7 && !KuchisakeGame.busy(t)) {
            getNavigation().stop();
            KuchisakeGame.start(this, t);
            return;
        }
        walkToward(t, 1.25);
        if (d < 3.2 && attackCd <= 0) {
            attackCd = 26;
            action(1);
            sound(SoundEvents.SHEEP_SHEAR, 2f, 0.5f);
            strike(t, 8f);
            CsmNetwork.fx(this, FxType.SHEARS, this, t, t.position().add(0, t.getBbHeight() * 0.6, 0), getYRot(), 0);
        }
    }
}
