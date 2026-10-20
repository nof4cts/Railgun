package com.sbr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gyro Zeppeli - the Spin. Two Steel Balls that come back to his hand: the plain throw, whose spin shakes whoever it
 * hits; the Wrecking Ball, whose little spheres leave a player blind on his left side; Spin Hardening; Spin
 * healing; the Golden Spin (best from a galloping horse), and with it Ball Breaker, which ages what it strikes.
 */
public final class Gyro {
    public static final String NAME = "GYRO";
    public static final String[] MOVES = {"Steel Ball", "Wrecking Ball", "Spin Hardening", "Spin Healing", "Golden Spin", "Ball Breaker"};
    static final int[] COOLDOWN = {20, 140, 400, 400, 500, 900};
    static final int BALLS = 2;
    /** balls in hand, Golden Spin until */
    private static final Map<UUID, long[]> STATE = new HashMap<>();

    private Gyro() {}

    static long[] st(ServerPlayer p) {
        return STATE.computeIfAbsent(p.getUUID(), k -> new long[]{BALLS, 0});
    }

    static void forget(UUID id) {
        STATE.remove(id);
    }

    static void sync(ServerPlayer p) {
        long[] s = st(p);
        SbrNet.to(p, SbrNet.Fx.of(SbrNet.AMMO, p.getId(), p.position(), Vec3.ZERO, s[0], BALLS));
        SbrNet.to(p, SbrNet.Fx.of(SbrNet.GOLDEN, p.getId(), p.position(), Vec3.ZERO, Math.max(0, s[1] - Sbr.now(p)), 1));
    }

    static boolean golden(ServerPlayer p) {
        return st(p)[1] > Sbr.now(p);
    }

    static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        return p.getEyePosition().add(look.scale(0.6)).add(new Vec3(-flat.z, 0, flat.x).scale(0.4)).add(0, -0.3, 0);
    }

    static void key(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= MOVES.length) {
            return;
        }
        if (!Sbr.ready(p, slot, MOVES[slot])) {
            return;
        }
        switch (slot) {
            case 0 -> throwBall(p, false);
            case 1 -> throwBall(p, true);
            case 2 -> harden(p);
            case 3 -> heal(p);
            case 4 -> goldenSpin(p);
            case 5 -> ballBreaker(p);
            default -> {
            }
        }
        sync(p);
    }

    static boolean takeBall(ServerPlayer p) {
        long[] s = st(p);
        if (s[0] <= 0 && !p.getAbilities().instabuild) {
            Sbr.say(p, "Both Steel Balls are out", ChatFormatting.GRAY);
            return false;
        }
        if (s[0] > 0) {
            s[0]--;
        }
        return true;
    }

    /** The ball comes back to his hand after its flight. */
    static void comeBack(ServerPlayer p, Vec3 from, int ticks) {
        ServerLevel l = p.serverLevel();
        long start = Sbr.now(p);
        Sbr.JOBS.add(new Sbr.Job(p, start, start + ticks) {
            @Override
            void tick(long t) {
            }

            @Override
            void end() {
                long[] s = st(p);
                s[0] = Math.min(BALLS, s[0] + 1);
                Sbr.play(l, p.position(), SoundEvents.CHAIN_PLACE, 0.6F, 1.6F);
                sync(p);
            }
        });
    }

    /** The Steel Ball (and the Wrecking Ball): thrown spinning; whatever it hits shakes with the Spin; it returns. */
    static void throwBall(ServerPlayer p, boolean wrecking) {
        if (!takeBall(p)) {
            return;
        }
        int slot = wrecking ? 1 : 0;
        Sbr.cooldown(p, slot, COOLDOWN[slot]);
        ServerLevel l = p.serverLevel();
        Vec3 from = hand(p), dir = p.getLookAngle().normalize();
        boolean gold = golden(p);
        float dmg = (wrecking ? 5.0F : 6.0F) * (gold ? 1.6F : 1.0F);
        double speed = 2.2, range = wrecking ? 20 : 18;
        Sbr.play(l, from, SoundEvents.TRIDENT_THROW, 0.9F, 1.5F);
        double[] at = {0};
        double reach = Sbr.shoot(p, from, dir, speed, range, 0.3, gold, e -> {
            Sbr.hurt(p, e, dmg);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 1, false, false, true));
            Sbr.push(e, dir.scale(0.4).add(0, 0.12, 0));
            Sbr.play(l, e.position(), SoundEvents.ANVIL_PLACE, 0.4F, 1.9F);
        }, stop -> {
            at[0] = stop.distanceTo(from);
            SbrNet.around(l, stop, SbrNet.Fx.of(SbrNet.HIT, p.getId(), stop, dir, 3, 0));
            if (wrecking) {
                wreck(p, stop);
            }
            comeBack(p, stop, (int) Math.ceil(stop.distanceTo(from) / speed) + 4);
        });
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.BALL, p.getId(), from, dir, (float) speed, (float) reach * (gold ? -1 : 1)));
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.SPIN_UP, p.getId(), from, dir, 2, 0));
    }

    /** The Wrecking Ball breaks into small spheres that strike from odd angles; their shock leaves a player blind on the left. */
    static void wreck(ServerPlayer p, Vec3 at) {
        ServerLevel l = p.serverLevel();
        List<LivingEntity> near = Sbr.around(p, at, 6.5);
        near.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
        int n = 0;
        for (LivingEntity e : near) {
            if (n++ >= 5) {
                break;
            }
            Vec3 to = e.getBoundingBox().getCenter();
            SbrNet.around(l, at, SbrNet.Fx.of(SbrNet.SPLIT, p.getId(), at, to.subtract(at), 0, 0));
            Sbr.hurt(p, e, 2.5F);
            if (e instanceof ServerPlayer sp) {
                SbrNet.to(sp, SbrNet.Fx.of(SbrNet.NEGLECT, p.getId(), at, Vec3.ZERO, 80, 0));
                Sbr.say(sp, "Hemispatial neglect - you can't see your left side", ChatFormatting.LIGHT_PURPLE);
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
            }
        }
        // some fly off at nothing - they still look the part
        for (int i = n; i < 5; i++) {
            double a = i * 1.257;
            SbrNet.around(l, at, SbrNet.Fx.of(SbrNet.SPLIT, p.getId(), at, new Vec3(Math.cos(a) * 3, 0.6, Math.sin(a) * 3), 0, 0));
        }
        Sbr.play(l, at, SoundEvents.ANVIL_LAND, 0.5F, 1.7F);
    }

    /** Spin Hardening: the Spin run through his own body - hard enough to stop a bullet, for a little while. */
    static void harden(ServerPlayer p) {
        Sbr.cooldown(p, 2, COOLDOWN[2]);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, false, false));
        Sbr.play(p.serverLevel(), p.position(), SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.8F);
        SbrNet.around(p.serverLevel(), p.position(), SbrNet.Fx.of(SbrNet.HARDEN, p.getId(), p.position(), Vec3.ZERO, 80, 0));
    }

    /** Spin Healing: the Zeppeli doctors' Spin set into a body - whoever he looks at (close), or himself. */
    static void heal(ServerPlayer p) {
        Sbr.cooldown(p, 3, COOLDOWN[3]);
        LivingEntity who = p;
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(5));
        EntityHitResult eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(p.getLookAngle().scale(5)).inflate(1), e -> e instanceof LivingEntity && e != p, 25);
        if (eh != null && eh.getEntity() instanceof LivingEntity le && (le instanceof Player || le.getHealth() < le.getMaxHealth())) {
            who = le;
        }
        who.heal(8.0F);
        who.removeEffect(MobEffects.POISON);
        who.removeEffect(MobEffects.WITHER);
        Sbr.play(p.serverLevel(), who.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.2F);
        SbrNet.around(p.serverLevel(), who.position(), SbrNet.Fx.of(SbrNet.HEAL, who.getId(), who.position(), Vec3.ZERO, 30, 0));
    }

    /** The Golden Spin: perfect on a galloping horse; on foot it takes him a while, standing still, to find it. */
    static void goldenSpin(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (Sbr.galloping(p) || p.getAbilities().instabuild) {
            reach(p);
            return;
        }
        long start = Sbr.now(p);
        int charge = 40;
        Sbr.BUSY.put(p.getUUID(), start + charge);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, charge, 4, false, false, false));
        Sbr.say(p, "Finding the Golden Rectangle... (a galloping horse makes it instant)", ChatFormatting.GOLD);
        SbrNet.around(l, p.position(), SbrNet.Fx.of(SbrNet.GOLDEN, p.getId(), p.position(), Vec3.ZERO, charge, 0));
        Sbr.JOBS.add(new Sbr.Job(p, start, start + charge) {
            @Override
            void tick(long t) {
            }

            @Override
            void end() {
                reach(p);
            }
        });
    }

    static void reach(ServerPlayer p) {
        Sbr.cooldown(p, 4, COOLDOWN[4]);
        st(p)[1] = Sbr.now(p) + 200;
        Sbr.play(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, 1.2F, 1.3F);
        Sbr.say(p, "GOLDEN SPIN - Steel Balls hit harder and pierce; Ball Breaker is ready", ChatFormatting.GOLD);
        SbrNet.around(p.serverLevel(), p.position(), SbrNet.Fx.of(SbrNet.GOLDEN, p.getId(), p.position(), Vec3.ZERO, 200, 2));
        sync(p);
    }

    /** Ball Breaker: the Zeppeli ultimate throw. Its Spin, made visible, ages whatever it strikes. Needs the Golden Spin. */
    static void ballBreaker(ServerPlayer p) {
        if (!golden(p) && !Sbr.galloping(p) && !p.getAbilities().instabuild) {
            Sbr.say(p, "Ball Breaker needs the Golden Spin", ChatFormatting.GRAY);
            return;
        }
        if (!takeBall(p)) {
            return;
        }
        Sbr.cooldown(p, 5, COOLDOWN[5]);
        ServerLevel l = p.serverLevel();
        Vec3 from = hand(p), dir = p.getLookAngle().normalize();
        double speed = 1.7;
        Sbr.play(l, from, SoundEvents.TRIDENT_RIPTIDE_3, 1.2F, 1.2F);
        double reach = Sbr.shoot(p, from, dir, speed, 26, 0.9, false, e -> {
            Sbr.hurt(p, e, 10.0F);
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1, false, true, true));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, true, true));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true, true));
            e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 2, false, true, true));
            if (e instanceof ServerPlayer sp) {
                Sbr.say(sp, "Ball Breaker - your body is ageing", ChatFormatting.DARK_GRAY);
            }
            SbrNet.around(l, e.position(), SbrNet.Fx.of(SbrNet.BREAKER_HIT, e.getId(), e.position(), dir, 100, 0));
        }, stop -> {
            SbrNet.around(l, stop, SbrNet.Fx.of(SbrNet.HIT, p.getId(), stop, dir, 3, 0));
            comeBack(p, stop, (int) Math.ceil(stop.distanceTo(from) / speed) + 4);
        });
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.BREAKER, p.getId(), from, dir, (float) speed, (float) reach));
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.SPIN_UP, p.getId(), from, dir, 2, 0));
    }
}
