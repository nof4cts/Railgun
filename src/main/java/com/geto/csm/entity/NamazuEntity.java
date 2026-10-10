package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ōnamazu, the great earthquake catfish of folklore. Swims half-sunk through solid ground
 * and thrashes the earth into rolling quake rings.
 */
public class NamazuEntity extends CurseEntity {

    public NamazuEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.NAMAZU;
    }

    @Override
    protected float damageTaken() {
        return 0.75f;
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        LivingEntity t = findTarget(o, 30);
        if (t == null) {
            followOwner(o, 6, 0.9, false);
            return;
        }
        walkToward(t, 1.2);
        double d = distanceTo(t);
        if (d < 3.5 && attackCd <= 0) {
            attackCd = 30;
            action(1);
            strike(t, 9f);
            launch(t, t.position().subtract(position()).normalize().scale(1.0).add(0, 0.6, 0));
            CsmNetwork.fx(this, FxType.BITE, this, t, t.position().add(0, 0.8, 0), getYRot(), 0);
            sound(SoundEvents.RAVAGER_ATTACK, 1.4f, 0.8f);
        }
        if (d < 12 && specialCd <= 0) quake();
    }

    private void quake() {
        specialCd = 100;
        action(2);
        Vec3 c = position();
        sound(SoundEvents.RAVAGER_ROAR, 2f, 0.5f);
        for (int w = 0; w < 3; w++) {
            final double r0 = 1 + w * 3.5, r1 = r0 + 3.5;
            Scheduler.after(6 + w * 8, () -> {
                if (!isAlive()) return;
                CsmNetwork.fx(this, FxType.QUAKE_WAVE, this, null, c, (float) r1, 0);
                sound(SoundEvents.GENERIC_EXPLODE, 1.2f, 0.5f);
                for (LivingEntity e : enemiesAround(c, r1)) {
                    if (e.position().distanceTo(c) < r0 || !e.onGround()) continue;
                    strike(e, 7f);
                    launch(e, new Vec3(0, 0.9, 0));
                }
            });
        }
    }
}
