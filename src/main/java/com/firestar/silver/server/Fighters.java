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
        long counterReady;
        /** Flight was given by the awakening (not by creative mode), so it is ours to take back. */
        boolean flight;
    }

    public static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static int form(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? NONE : s.form;
    }

    /** When this fighter's Silver Zenith runs out (0 = no limit: creative mode, or the limit is switched off). */
    static long zenithUntil(ServerPlayer p, State s) {
        int secs = com.firestar.silver.config.ZConfig.SERVER.zenithSeconds.get();
        if (s.form != ZENITH || secs <= 0 || p.isCreative()) {
            return 0;
        }
        return s.since + secs * 20L;
    }

    public static void setForm(ServerPlayer p, int form) {
        State s = state(p);
        if (s.form == ZENITH && form != ZENITH) {
            // the summit is spent: it takes a long rest before it can be reached again
            Techniques.zenithSpent(p);
        }
        s.form = form;
        s.since = p.serverLevel().getGameTime();
        flight(p, s);
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
        Net.toTrackingAndSelf(p, new S2CState(p.getId(), s.form, s.since, zenithUntil(p, s)));
    }

    public static S2CState packet(ServerPlayer p) {
        State s = state(p);
        return new S2CState(p.getId(), s.form, s.since, zenithUntil(p, s));
    }

    /**
     * Awakened, he flies: the vanilla double-jump to take off and land, faster in Zenith. Taken back the moment the
     * awakening ends (unless creative mode gave it).
     */
    static void flight(ServerPlayer p, State s) {
        var ab = p.getAbilities();
        if (p.isCreative() || p.isSpectator()) {
            s.flight = false;
            return;
        }
        boolean awake = s.form != NONE;
        boolean changed = false;
        if (awake && !ab.mayfly) {
            ab.mayfly = true;
            s.flight = true;
            p.getPersistentData().putBoolean("si_flight", true);
            changed = true;
        }
        float speed = s.form == ZENITH ? 0.11F : 0.075F;
        if (awake && s.flight && Math.abs(ab.getFlyingSpeed() - speed) > 1.0E-4) {
            ab.setFlyingSpeed(speed);
            changed = true;
        }
        if (!awake && s.flight) {
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            s.flight = false;
            p.getPersistentData().remove("si_flight");
            changed = true;
        }
        if (changed) {
            p.onUpdateAbilities();
        }
    }

    public static void tick(ServerPlayer p) {
        State s0 = STATES.get(p.getUUID());
        if (s0 != null) {
            flight(p, s0);
        }
        State s = STATES.get(p.getUUID());
        if (s != null && s.form == ZENITH) {
            long until = zenithUntil(p, s);
            long now = p.serverLevel().getGameTime();
            if (until > 0 && now >= until && Techniques.busy(p) == null) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("Silver Zenith fades. Its power returns in "
                        + Math.round(com.firestar.silver.config.ZConfig.SERVER.zenithCooldownSeconds.get() / 60.0F) + " min.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                setForm(p, OMEN);
                return;
            }
        }
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
     * he is a step to the side. Omen slips some blows. Zenith slips every one, from any direction (blades, arrows,
     * blasts, spells), and answers each: a close attacker is struck where he stands, a distant one is reached in a
     * single step and struck from behind.
     */
    public static boolean dodge(ServerPlayer p, DamageSource source) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.form == NONE) {
            return false;
        }
        boolean zenith = s.form == ZENITH;
        // what can be dodged: anything thrown, swung, cast or blown up at him; not the void, starving or /kill
        Vec3 origin = source.getDirectEntity() != null ? source.getDirectEntity().position()
                : source.getEntity() != null ? source.getEntity().position() : source.getSourcePosition();
        if (origin == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        long now = p.serverLevel().getGameTime();
        if (!zenith) {
            if (now < s.dodgeReady || p.getRandom().nextDouble() >= ZConfig.SERVER.omenDodge.get()) {
                return false;
            }
        }
        // Zenith never fails; it only moves once every few ticks, and in between the blows pass straight through him
        boolean move = now >= s.dodgeReady;
        if (move) {
            s.dodgeReady = now + (zenith ? 4 : 8);
            Vec3 away = p.position().subtract(origin).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? p.getLookAngle().multiply(-1, 0, -1) : away.normalize();
            Vec3 side = new Vec3(-away.z, 0, away.x).scale(p.getRandom().nextBoolean() ? 1 : -1);
            Vec3 start = p.position();
            Vec3 to = start;
            // try a step to one side, then the other, then straight back from the blow, then up
            for (Vec3 step : new Vec3[]{side.scale(1.6).add(away.scale(0.5)), side.scale(-1.6).add(away.scale(0.5)), away.scale(2.0), new Vec3(0, 1.6, 0)}) {
                Vec3 c = start.add(step);
                if (p.level().noCollision(p, p.getBoundingBox().move(step))) {
                    to = c;
                    break;
                }
            }
            if (to != start) {
                p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
            }
            Net.toTrackingAndSelf(p, new S2CFx(S2CFx.DODGE, p.getId(), List.of(start, to)));
        }
        if (zenith && ZConfig.SERVER.counters.get() && source.getEntity() instanceof LivingEntity attacker && attacker != p && attacker.isAlive()
                && now >= s.counterReady) {
            s.counterReady = now + 1;
            double dist = attacker.distanceTo(p);
            if (dist > 5.0 && dist < 48.0) {
                // a distant attacker: one step and he is behind them
                Vec3 behind = attacker.position().add(attacker.getLookAngle().multiply(1, 0, 1).normalize().scale(-1.4));
                if (p.level().noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) {
                    Vec3 from = p.position();
                    Vec3 d = attacker.getEyePosition().subtract(behind.add(0, p.getEyeHeight(), 0));
                    float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                    p.connection.teleport(behind.x, behind.y, behind.z, yaw, p.getXRot());
                    Net.toTrackingAndSelf(p, new S2CFx(S2CFx.DODGE, p.getId(), List.of(from, behind)));
                    dist = attacker.distanceTo(p);
                }
            }
            if (dist <= 6.0) {
                attacker.invulnerableTime = 0;
                attacker.hurt(p.damageSources().playerAttack(p), 6.0F * ZConfig.SERVER.damage.get().floatValue());
                Vec3 push = attacker.position().subtract(p.position()).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? p.getLookAngle().multiply(1, 0, 1) : push.normalize();
                attacker.setDeltaMovement(push.scale(1.1).add(0, 0.35, 0));
                attacker.hurtMarked = true;
                Net.toTrackingAndSelf(p, new S2CFx(S2CFx.COUNTER, p.getId(), List.of(attacker.getBoundingBox().getCenter())));
            }
        }
        return true;
    }

    /** On login: flight left over from an awakening that ended with the session is taken back. */
    public static void cleanFlight(ServerPlayer p) {
        if (p.getPersistentData().getBoolean("si_flight")) {
            p.getPersistentData().remove("si_flight");
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.getAbilities().setFlyingSpeed(0.05F);
                p.onUpdateAbilities();
            }
        }
    }

    public static void forget(Player p) {
        STATES.remove(p.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }
}
