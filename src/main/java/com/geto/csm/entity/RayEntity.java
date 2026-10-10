package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Veil Ray — a broad gliding curse that carries its summoner and one passenger through the sky. */
public class RayEntity extends CurseEntity {

    public RayEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.RAY;
    }

    @Override
    public boolean rideable() {
        return true;
    }

    @Override
    protected int maxRiders() {
        return 2;
    }

    @Override
    protected double rideSpeed() {
        return 0.9;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int idx = getPassengers().indexOf(passenger);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 seat = fwd.scale(idx == 0 ? 0.5 : -0.8);
        move.accept(passenger, getX() + seat.x, getY() + 0.55, getZ() + seat.z);
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        LivingEntity t = findTarget(o, 24);
        if (t != null && distanceTo(t) < 9) {
            flyToward(t.position().add(0, 2.5, 0), 0.6);
            if (distanceTo(t) < 5 && attackCd <= 0) {
                attackCd = 60;
                action(1);
                sound(SoundEvents.PHANTOM_FLAP, 2f, 0.5f);
                CsmNetwork.fx(this, FxType.GUST, this, t, position(), getYRot(), 0);
                for (LivingEntity e : enemiesAround(position(), 6)) {
                    strike(e, 3f);
                    launch(e, e.position().subtract(position()).normalize().scale(1.8).add(0, 0.8, 0));
                }
            }
            return;
        }
        double a = tickCount * 0.03;
        flyToward(o.position().add(Math.cos(a) * 4, 2.2, Math.sin(a) * 4), 0.5);
        if (distanceTo(o) > 40) teleportTo(o.getX(), o.getY() + 2, o.getZ());
    }
}
