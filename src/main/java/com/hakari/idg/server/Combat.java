package com.hakari.idg.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Hit, stun, launch and targeting helpers. */
public final class Combat {
    private static final Map<LivingEntity, Integer> STUN = new WeakHashMap<>();

    public static void stun(LivingEntity e, int ticks) {
        STUN.merge(e, ticks, Math::max);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
    }

    public static boolean stunned(LivingEntity e) {
        return STUN.containsKey(e);
    }

    public static void tick() {
        Iterator<Map.Entry<LivingEntity, Integer>> it = STUN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Integer> en = it.next();
            LivingEntity e = en.getKey();
            int left = en.getValue() - 1;
            if (left <= 0 || e.isRemoved() || !e.isAlive()) {
                it.remove();
                continue;
            }
            en.setValue(left);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(0, Math.min(v.y, 0), 0);
            if (e instanceof Player) e.hurtMarked = true;
            if (e instanceof Mob mob) mob.getNavigation().stop();
        }
    }

    public static void launch(LivingEntity e, Vec3 v) {
        STUN.remove(e);
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    public static void damage(ServerPlayer src, LivingEntity target, float amount, boolean unblockable) {
        if (!target.isAlive() || target == src) return;
        if (unblockable && target instanceof Player p && p.isBlocking()) {
            p.disableShield(true);
        }
        target.invulnerableTime = 0;
        target.hurt(src.damageSources().playerAttack(src), amount);
    }

    public static Vec3 flatLook(Player p) {
        Vec3 l = p.getLookAngle();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** Living things in a cone in front of the player, nearest first. */
    public static List<LivingEntity> cone(ServerPlayer sp, double range, double minDot) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        AABB box = sp.getBoundingBox().inflate(range);
        return sp.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != sp && e.isAlive() && !e.isSpectator())
                .stream()
                .filter(e -> {
                    Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                    double d = to.length();
                    if (d > range) return false;
                    return d < 1.5 || to.normalize().dot(look) >= minDot;
                })
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(sp)))
                .toList();
    }

    public static LivingEntity nearest(ServerPlayer sp, double range, double minDot) {
        List<LivingEntity> list = cone(sp, range, minDot);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<LivingEntity> sphere(ServerLevel level, Vec3 c, double r, Entity exclude) {
        AABB box = new AABB(c, c).inflate(r);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != exclude && e.isAlive() && !e.isSpectator()
                        && e.getBoundingBox().getCenter().distanceTo(c) <= r + e.getBbWidth() * 0.5)
                .stream()
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(c)))
                .toList();
    }

    public static void sound(Entity at, SoundEvent s, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    private Combat() {}
}
