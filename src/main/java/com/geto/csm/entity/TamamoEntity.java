package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tamamo-no-Mae, from the legend of the nine-tailed fox who became the Killing Stone.
 * Hovers above the fight hurling foxfire, sweeps with nine tails, and drops the Killing Stone.
 */
public class TamamoEntity extends CurseEntity {

    public TamamoEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.TAMAMO;
    }

    @Override
    protected float damageTaken() {
        return 0.6f;
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        LivingEntity t = findTarget(o, 40);
        if (t == null) {
            followOwner(o, 6, 0.6, true);
            return;
        }
        double a = tickCount * 0.025 + getId();
        double d = distanceTo(t);
        if (d < 4.5 && attackCd <= 0) {
            tailSweep();
        } else {
            flyToward(t.position().add(Math.cos(a) * 7, 4, Math.sin(a) * 7), 0.7);
            faceToward(t.position());
        }
        if (specialCd <= 0 && d < 30) killingStone(t);
        else if (tickCount % 40 == 0 && d < 30) foxfire(t);
    }

    private void foxfire(LivingEntity t) {
        action(1);
        sound(SoundEvents.FIRECHARGE_USE, 1.4f, 1.4f);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            CsmNetwork.fx(this, FxType.FOXFIRE, this, t, getEyePosition(), k, 0);
            Scheduler.after(14 + k * 3, () -> {
                if (!isAlive() || !t.isAlive() || distanceTo(t) > 34) return;
                strike(t, 5f);
                t.setSecondsOnFire(3);
            });
        }
    }

    private void tailSweep() {
        attackCd = 40;
        action(2);
        sound(SoundEvents.ENDER_DRAGON_FLAP, 1.8f, 1.2f);
        CsmNetwork.fx(this, FxType.TAIL_SWEEP, this, null, position(), getYRot(), 6);
        for (LivingEntity e : enemiesAround(position(), 6)) {
            strike(e, 10f);
            launch(e, e.position().subtract(position()).normalize().scale(1.8).add(0, 0.7, 0));
        }
    }

    private void killingStone(LivingEntity t) {
        specialCd = 240;
        action(3);
        Vec3 at = t.position();
        CsmNetwork.fx(this, FxType.KILLING_STONE, this, t, at, 0, 0);
        sound(SoundEvents.WITHER_SPAWN, 0.8f, 1.6f);
        Scheduler.after(20, () -> {
            if (!isAlive()) return;
            sound(SoundEvents.GENERIC_EXPLODE, 2f, 0.6f);
            for (LivingEntity e : enemiesAround(at.add(0, 1, 0), 4.5)) {
                strike(e, 16f);
                launch(e, e.position().subtract(at).normalize().scale(1.2).add(0, 0.8, 0));
            }
            CsmNetwork.fx(this, FxType.MIASMA, this, null, at, 6, 0);
            for (int i = 1; i <= 12; i++) {
                Scheduler.after(i * 10, () -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : enemiesAround(at.add(0, 1, 0), 5.5)) {
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                        strike(e, 2f);
                    }
                });
            }
        });
    }
}
