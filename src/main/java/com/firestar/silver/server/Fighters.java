package com.firestar.silver.server;

import com.firestar.silver.config.ZConfig;
import com.firestar.silver.network.Net;
import com.firestar.silver.network.S2CFx;
import com.firestar.silver.network.S2CState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Every fighter's awakening (none, Omen, Zenith), its standing gifts, and the instinct itself: a share of attacks are
 * simply not there when they land. In Zenith, the one who swung from close by gets answered before they recover.
 */
public final class Fighters {
    public static final int NONE = 0, OMEN = 1, ZENITH = 2;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private Fighters() {}

    public static final class State {
        public int form;
        public long since;
        int combo;
        long lastStrike;
        long dodgeReady;
    }

    public static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static int form(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? NONE : s.form;
    }

    public static void setForm(ServerPlayer p, int form) {
        State s = state(p);
        s.form = form;
        s.since = p.serverLevel().getGameTime();
        if (form == NONE) {
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
            p.removeEffect(MobEffects.DAMAGE_BOOST);
            p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
        sync(p);
    }

    /** The next strike of a combo: 0, 1, 2 then round again; a pause resets it. */
    static int nextCombo(ServerPlayer p) {
        State s = state(p);
        long now = p.serverLevel().getGameTime();
        s.combo = now - s.lastStrike > 16 ? 0 : (s.combo + 1) % 3;
        s.lastStrike = now;
        return s.combo;
    }

    public static void sync(ServerPlayer p) {
        State s = state(p);
        Net.toTrackingAndSelf(p, new S2CState(p.getId(), s.form, s.since));
    }

    public static S2CState packet(ServerPlayer p) {
        State s = state(p);
        return new S2CState(p.getId(), s.form, s.since);
    }

    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.form == NONE || p.serverLevel().getGameTime() % 40 != 0) {
            return;
        }
        int amp = s.form == ZENITH ? 1 : 0;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, amp, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, amp, false, false, true));
        if (s.form == ZENITH) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false, true));
        }
    }

    /**
     * The instinct: called when something tries to hurt an awakened fighter. Returns true when the blow simply misses -
     * he is a step to the side - and in Zenith answers a close attacker at once.
     */
    public static boolean dodge(ServerPlayer p, DamageSource source) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.form == NONE || source.getEntity() == null && source.getDirectEntity() == null) {
            return false;
        }
        long now = p.serverLevel().getGameTime();
        if (now < s.dodgeReady) {
            return false;
        }
        double chance = s.form == ZENITH ? ZConfig.SERVER.zenithDodge.get() : ZConfig.SERVER.omenDodge.get();
        if (p.getRandom().nextDouble() >= chance) {
            return false;
        }
        s.dodgeReady = now + 8;
        Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        Vec3 away = p.position().subtract(from.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? p.getLookAngle().multiply(-1, 0, -1) : away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x).scale(p.getRandom().nextBoolean() ? 1 : -1);
        Vec3 start = p.position();
        Vec3 to = start.add(side.scale(1.6)).add(away.scale(0.5));
        if (!p.level().noCollision(p, p.getBoundingBox().move(to.subtract(start)))) {
            to = start.subtract(side.scale(1.6)).add(away.scale(0.5));
            if (!p.level().noCollision(p, p.getBoundingBox().move(to.subtract(start)))) {
                to = start;
            }
        }
        if (to != start) {
            p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
        }
        Net.toTrackingAndSelf(p, new S2CFx(S2CFx.DODGE, p.getId(), List.of(start, to)));
        if (s.form == ZENITH && ZConfig.SERVER.counters.get() && source.getEntity() instanceof LivingEntity attacker
                && attacker.distanceTo(p) < 5.0 && attacker != p) {
            attacker.invulnerableTime = 0;
            attacker.hurt(p.damageSources().playerAttack(p), 6.0F * ZConfig.SERVER.damage.get().floatValue());
            Vec3 push = attacker.position().subtract(p.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 1.0E-4 ? away.scale(-1) : push.normalize();
            attacker.setDeltaMovement(push.scale(1.1).add(0, 0.35, 0));
            attacker.hurtMarked = true;
            Net.toTrackingAndSelf(p, new S2CFx(S2CFx.COUNTER, p.getId(), List.of(attacker.getBoundingBox().getCenter())));
        }
        return true;
    }

    public static void forget(Player p) {
        STATES.remove(p.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }
}
