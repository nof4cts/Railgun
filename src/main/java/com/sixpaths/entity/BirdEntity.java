package com.sixpaths.entity;

import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Sky Roc — a huge four-winged bird. Carries two riders and dive-bombs when riderless. */
public class BirdEntity extends BeastEntity {
    private int diving;

    public BirdEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override public boolean rideable() { return true; }
    @Override protected int maxRiders() { return 2; }
    @Override protected double rideSpeed() { return 1.1; }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int idx = getPassengers().indexOf(passenger);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 seat = fwd.scale(idx == 0 ? 0.4 : -0.7);
        move.accept(passenger, getX() + seat.x, getY() + 1.1, getZ() + seat.z);
    }

    @Override
    protected void ai(ServerPlayer o) {
        LivingEntity t = findTarget(o, 32);
        if (diving > 0 && t != null) {
            diving--;
            flyToward(t.position().add(0, 0.5, 0), 1.6);
            if (distanceTo(t) < 2.5) {
                diving = 0;
                strike(t, 10f);
                launch(t, new Vec3(0, 1.2, 0).add(getDeltaMovement().scale(0.5)));
                PathsNetwork.fx(this, FxType.BEAST_HIT, this, t, t.position().add(0, 1, 0), 1, 0);
                sound(SoundEvents.PHANTOM_BITE, 2f, 0.6f);
                setDeltaMovement(getDeltaMovement().add(0, 1.0, 0));
            }
            return;
        }
        if (t != null && specialCd <= 0 && distanceTo(t) < 24) {
            specialCd = 120;
            diving = 30;
            action(1);
            sound(SoundEvents.PHANTOM_SWOOP, 2f, 0.5f);
            return;
        }
        double a = tickCount * 0.03;
        Vec3 anchor = t != null ? t.position().add(0, 8, 0) : o.position().add(0, 5, 0);
        flyToward(anchor.add(Math.cos(a) * 7, 0, Math.sin(a) * 7), 0.7);
        if (distanceTo(o) > 48) teleportTo(o.getX(), o.getY() + 4, o.getZ());
    }
}
