package com.firestar.mih.server;

import com.firestar.mih.common.Beats;
import com.firestar.mih.config.ZConfig;
import com.firestar.mih.network.Net;
import com.firestar.mih.network.S2CFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Infinite Speed: for a few seconds he moves in a different time from everyone round him. They crawl; he blinks to
 * whoever he punches. Every blow he lands is remembered, and when time snaps back, they all land again at once.
 */
public final class InfiniteSpeed {
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private InfiniteSpeed() {}

    static final class Run {
        final ServerPlayer user;
        final long until;
        final List<LivingEntity> slowed = new ArrayList<>();
        final Map<LivingEntity, Float> marks = new LinkedHashMap<>();

        Run(ServerPlayer user, long until) {
            this.user = user;
            this.until = until;
        }
    }

    public static boolean active(ServerPlayer p) {
        return RUNS.containsKey(p.getUUID());
    }

    public static long until(ServerPlayer p) {
        Run r = RUNS.get(p.getUUID());
        return r == null ? 0 : r.until;
    }

    static void begin(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Run r = new Run(p, level.getGameTime() + Beats.INF_DURATION);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(Beats.INF_RADIUS),
                e -> e != p && e.isAlive() && !(e instanceof ArmorStand))) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Beats.INF_DURATION, 5, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, Beats.INF_DURATION, 3, false, false));
            r.slowed.add(e);
        }
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Beats.INF_DURATION, 4, false, false, true));
        RUNS.put(p.getUUID(), r);
        StandUsers.sync(p);
    }

    /** Remember a blow landed in the stopped moment (called from the hurt event). */
    public static void mark(ServerPlayer p, LivingEntity victim, float amount) {
        Run r = RUNS.get(p.getUUID());
        if (r != null && victim != p) {
            r.marks.merge(victim, amount, Float::sum);
        }
    }

    /** Time snaps back: every remembered blow lands again, all at once. */
    public static void release(ServerPlayer p) {
        Run r = RUNS.remove(p.getUUID());
        if (r == null) {
            return;
        }
        for (LivingEntity e : r.slowed) {
            if (e.isAlive()) {
                e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                e.removeEffect(MobEffects.DIG_SLOWDOWN);
            }
        }
        p.removeEffect(MobEffects.MOVEMENT_SPEED);
        List<Vec3> where = new ArrayList<>();
        for (Map.Entry<LivingEntity, Float> m : r.marks.entrySet()) {
            LivingEntity e = m.getKey();
            if (!e.isAlive()) {
                continue;
            }
            where.add(e.getBoundingBox().getCenter());
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), (5.0F + m.getValue() * 0.45F) * ZConfig.SERVER.damage.get().floatValue());
            e.invulnerableTime = 0;
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
            e.setDeltaMovement(away.scale(0.9).add(0, 0.5, 0));
            e.hurtMarked = true;
        }
        Net.toTrackingAndSelf(p, new S2CFx(S2CFx.DETONATE, p.getId(), where));
        StandUsers.sync(p);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Run> it = new ArrayList<>(RUNS.values()).iterator();
        while (it.hasNext()) {
            Run r = it.next();
            if (r.user.isRemoved() || !r.user.isAlive()) {
                RUNS.remove(r.user.getUUID());
                continue;
            }
            if (r.user.serverLevel().getGameTime() >= r.until) {
                release(r.user);
            }
        }
    }

    public static void clearAll() {
        RUNS.clear();
    }

    @SuppressWarnings("unused")
    private static AABB unused() {
        return null;
    }
}
