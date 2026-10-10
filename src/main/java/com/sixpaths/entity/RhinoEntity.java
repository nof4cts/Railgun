package com.sixpaths.entity;

import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Horned Behemoth — a walking battering ram with a spiral drill horn. Charges through everything. */
public class RhinoEntity extends BeastEntity {
    private int charging;
    private Vec3 chargeDir = Vec3.ZERO;

    public RhinoEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setMaxUpStep(1.5f);
    }

    @Override
    protected void ai(ServerPlayer o) {
        if (charging > 0) {
            charging--;
            setDeltaMovement(chargeDir.x * 1.3, getDeltaMovement().y, chargeDir.z * 1.3);
            hurtMarked = true;
            for (LivingEntity e : enemiesAround(position().add(chargeDir.scale(1.6)).add(0, 1, 0), 2.2)) {
                strike(e, 14f);
                launch(e, chargeDir.scale(2.2).add(0, 0.9, 0));
                PathsNetwork.fx(this, FxType.BEAST_HIT, this, e, e.position().add(0, 1, 0), 1, 0);
                sound(SoundEvents.GENERIC_EXPLODE, 1f, 1.2f);
            }
            return;
        }
        LivingEntity t = findTarget(o, 32);
        if (t == null) {
            followOwner(o, 6, 1.0);
            return;
        }
        double d = distanceTo(t);
        if (d > 5 && d < 22 && specialCd <= 0) {
            specialCd = 140;
            charging = 22;
            chargeDir = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ()).normalize();
            faceToward(t.position());
            action(1);
            sound(SoundEvents.RAVAGER_ROAR, 2f, 0.6f);
            getNavigation().stop();
            return;
        }
        getNavigation().moveTo(t, 1.2);
        faceToward(t.position());
        if (d < 3.6 && attackCd <= 0) {
            attackCd = 26;
            action(2);
            strike(t, 9f);
            launch(t, t.position().subtract(position()).normalize().scale(1.2).add(0, 0.7, 0));
            PathsNetwork.fx(this, FxType.BEAST_HIT, this, t, t.position().add(0, 1, 0), 0, 0);
            sound(SoundEvents.RAVAGER_ATTACK, 1.5f, 0.7f);
        }
    }
}
