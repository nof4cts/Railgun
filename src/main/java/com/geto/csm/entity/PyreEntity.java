package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pyre Wraith — a charcoal husk born from wildfire. Its body temperature climbs from 120°C
 * to 3000°C the longer it burns, and its scorching aura grows with it.
 */
public class PyreEntity extends CurseEntity {
    public static final int HEAT_RAMP_TICKS = 20 * 40;

    public PyreEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.PYRE;
    }

    /** Temperature in °C, derived from age so client and server agree. */
    public static float heat(int ticks) {
        float k = Mth.clamp(ticks / (float) HEAT_RAMP_TICKS, 0f, 1f);
        return 120f + (3000f - 120f) * k * k;
    }

    public float heat() {
        return heat(tickCount);
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        float h = heat();
        double aura = 2.0 + h / 600.0;
        if (tickCount % 20 == 0) {
            CsmNetwork.fx(this, FxType.PYRE_PULSE, this, null, position(), h, (float) aura);
            for (LivingEntity e : enemiesAround(position().add(0, 1, 0), aura)) {
                e.setSecondsOnFire(3 + (int) (h / 600));
                strike(e, h / 420f);
            }
        }
        LivingEntity t = findTarget(o, 30);
        if (t == null) {
            followOwner(o, 5, 1.0, false);
            return;
        }
        walkToward(t, 1.1);
        if (distanceTo(t) < 9 && specialCd <= 0) flare(t, h);
    }

    private void flare(LivingEntity t, float h) {
        specialCd = 110;
        action(1);
        sound(SoundEvents.BLAZE_SHOOT, 2f, 0.5f);
        Vec3 d = t.position().subtract(position()).normalize();
        CsmNetwork.fx(this, FxType.PYRE_FLARE, this, t, position().add(0, 1.4, 0), getYRot(), h);
        Scheduler.after(8, () -> {
            if (!isAlive()) return;
            for (LivingEntity e : enemiesAround(position().add(d.scale(5)).add(0, 1, 0), 5.5)) {
                strike(e, 6f + h / 400f);
                e.setSecondsOnFire(6);
                launch(e, d.scale(1.0).add(0, 0.5, 0));
            }
            sound(SoundEvents.GENERIC_EXPLODE, 1.2f, 0.8f);
        });
    }
}
