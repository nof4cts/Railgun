package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Gloomscale Wyrm — an armoured serpent-dragon. The hardest hide in the roster. Rideable, flies. */
public class WyrmEntity extends CurseEntity {

    public WyrmEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.WYRM;
    }

    @Override
    protected float damageTaken() {
        return 0.45f;
    }

    @Override
    public boolean rideable() {
        return true;
    }

    @Override
    protected double rideSpeed() {
        return 1.25;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(1.7);
        move.accept(passenger, getX() + back.x, getY() + 1.55, getZ() + back.z);
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        LivingEntity t = findTarget(o, 40);
        if (t == null) {
            followOwner(o, 7, 0.7, true);
            return;
        }
        double d = distanceTo(t);
        if (d > 4.5) {
            flyToward(t.position().add(0, 1.5, 0), 0.95);
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            faceToward(t.position());
        }
        if (d < 5.5 && attackCd <= 0) bite(t);
        else if (d < 7 && specialCd <= 0) tailSweep();
        else if (d > 9 && d < 24 && signatureCd <= 0) roar(t.position().add(0, 1, 0).subtract(getEyePosition()));
    }

    private void bite(LivingEntity t) {
        attackCd = 28;
        action(1);
        sound(SoundEvents.RAVAGER_ATTACK, 1.6f, 0.6f);
        Scheduler.after(6, () -> {
            if (!isAlive() || !t.isAlive() || distanceTo(t) > 6.5) return;
            strike(t, 14f);
            launch(t, t.position().subtract(position()).normalize().scale(1.4).add(0, 0.7, 0));
            CsmNetwork.fx(this, FxType.BITE, this, t, t.position().add(0, t.getBbHeight() / 2, 0), getYRot(), 1);
            sound(SoundEvents.GENERIC_EXPLODE, 0.8f, 1.3f);
        });
    }

    private void tailSweep() {
        specialCd = 140;
        action(2);
        sound(SoundEvents.ENDER_DRAGON_FLAP, 2f, 0.6f);
        Scheduler.after(8, () -> {
            if (!isAlive()) return;
            CsmNetwork.fx(this, FxType.TAIL_SWEEP, this, null, position(), getYRot(), 9);
            for (LivingEntity e : enemiesAround(position(), 9)) {
                strike(e, 8f);
                launch(e, e.position().subtract(position()).normalize().scale(1.6).add(0, 0.6, 0));
            }
        });
    }

    /** Breath of grave-dark fire along a direction. Used by the AI and by a rider. */
    public void roar(Vec3 dir) {
        specialCd = 160;
        signatureCd = 1200;
        action(3);
        Vec3 d = dir.normalize();
        Vec3 from = getEyePosition().add(d.scale(1.5));
        sound(SoundEvents.ENDER_DRAGON_GROWL, 2f, 0.7f);
        CsmNetwork.fx(this, FxType.ROAR_BEAM, this, null, from, (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f,
                (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG)));
        Scheduler.after(6, () -> {
            if (!isAlive()) return;
            for (LivingEntity e : enemiesAround(from.add(d.scale(11)), 12)) {
                Vec3 to = e.getBoundingBox().getCenter().subtract(from);
                double along = to.dot(d);
                if (along < 0 || along > 24) continue;
                double off = to.subtract(d.scale(along)).length();
                if (off > 2.2 + along * 0.12) continue;
                strike(e, 12f);
                e.setSecondsOnFire(4);
                launch(e, d.scale(1.2).add(0, 0.4, 0));
            }
        });
    }

    public boolean riderAttack(Player rider) {
        if (specialCd > 0 || !hasPassenger(rider)) return false;
        roar(rider.getLookAngle());
        return true;
    }
}
