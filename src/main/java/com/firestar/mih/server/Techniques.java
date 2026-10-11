package com.firestar.mih.server;

import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import com.firestar.mih.config.ZConfig;
import com.firestar.mih.network.Net;
import com.firestar.mih.network.S2CCast;
import com.firestar.mih.network.S2CCooldowns;
import com.firestar.mih.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The server half of every move: who may use what, where it goes, and the tick-by-tick script that holds the user for
 * a cinematic, moves him through the target at infinite speed, and lands every blow on its beat in {@link Beats}.
 */
public final class Techniques {
    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static final List<Bleed> BLEEDS = new ArrayList<>();

    private Techniques() {}

    static final class Cast {
        final Move move;
        final ServerPlayer player;
        final ServerLevel level;
        final long start;
        final Vec3 anchor;
        final float yaw;
        final float pitch;
        final Vec3 origin;
        final Vec3 dir;
        final Vec3 flat;
        final Vec3 point;
        final Vec3 exit;
        final LivingEntity target;
        final boolean da;

        Cast(Move move, ServerPlayer player, long start, Vec3 origin, Vec3 dir, Vec3 point, Vec3 exit, LivingEntity target) {
            this.move = move;
            this.player = player;
            this.level = player.serverLevel();
            this.start = start;
            this.anchor = player.position();
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.origin = origin;
            this.dir = dir;
            Vec3 f = new Vec3(dir.x, 0.0, dir.z);
            this.flat = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
            this.point = point;
            this.exit = exit;
            this.target = target;
            this.da = StandUsers.doubleAccel(player);
        }

        int age() {
            return (int) (level.getGameTime() - start);
        }
    }

    private record Bleed(LivingEntity victim, ServerPlayer source, long until, float perHit) {}

    // ------------------------------------------------------------------------------------------------ requests

    public static boolean inCinematic(Entity e) {
        for (Cast c : ACTIVE) {
            if (c.player == e && c.move.cinematic && c.age() <= Math.max(c.move.anchorUntil(), Math.min(c.move.length, c.move.anchorUntil() + 14))) {
                return true;
            }
        }
        return false;
    }

    private static Cast busy(ServerPlayer p) {
        for (Cast c : ACTIVE) {
            if (c.player == p) {
                return c;
            }
        }
        return null;
    }

    private static void deny(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true);
    }

    private static float dmg(float base) {
        return base * ZConfig.SERVER.damage.get().floatValue();
    }

    public static void request(ServerPlayer p, Move m) {
        if (!p.isAlive() || p.isSpectator() || m == Move.UNIVERSE_RESET) {
            return;
        }
        if (!StandUsers.has(p)) {
            if (m == Move.SUMMON) {
                deny(p, "You have no Stand. (Use a Made in Heaven DISC.)");
            }
            return;
        }
        if (UniverseReset.holding(p)) {
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        StandUsers.State st = StandUsers.state(p);
        if (m == Move.SUMMON) {
            if (busy(p) != null) {
                return;
            }
            if (st.summoned && p.isShiftKeyDown()) {
                StandUsers.relinquish(p);
                p.displayClientMessage(Component.literal("Made in Heaven's DISC slides out of your head.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            st.summoned = !st.summoned;
            StandUsers.sync(p);
            if (st.summoned) {
                broadcast(p, new Cast(Move.SUMMON, p, now, p.getEyePosition(), p.getLookAngle(), p.getEyePosition(), p.position(), null));
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SUMMON.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
            }
            return;
        }
        if (m == Move.TIME_ACCEL && TimeAccel.of(p) != null) {
            TimeAccel.cancel(p);
            p.displayClientMessage(Component.literal("Time slows back to its course.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!st.summoned) {
            if (m != Move.PUNCH) {
                deny(p, "Summon Made in Heaven first (Q).");
            }
            return;
        }
        if (busy(p) != null) {
            return;
        }
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        if (now < ready[m.ordinal()] && !p.isCreative()) {
            if (m != Move.PUNCH) {
                deny(p, m.title + " is ready in " + Mth.ceil((ready[m.ordinal()] - now) / 20.0F) + "s");
            }
            return;
        }
        if (m == Move.TIME_ACCEL && TimeAccel.anyIn(level)) {
            deny(p, "Time is already accelerating.");
            return;
        }

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize();
        LivingEntity target = null;
        Vec3 point = ray(p, eye, look, 6.0);
        Vec3 exit = p.position();
        switch (m) {
            case PUNCH, BARRAGE, FINISHER -> {
                target = aim(p, 4.5, 40.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                }
            }
            case KNIVES -> {
                target = aim(p, 40.0, 14.0);
                point = target != null ? target.getBoundingBox().getCenter() : ray(p, eye, look, 40.0);
            }
            case SPEED_SLICE, INFINITE_SPEED -> {
                double range = m == Move.SPEED_SLICE ? Beats.SLICE_RANGE : Beats.INF_RANGE;
                target = aim(p, range, 28.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                    Vec3 tf = target.position().subtract(p.position());
                    Vec3 across = new Vec3(tf.x, 0, tf.z).lengthSqr() < 1.0E-4 ? flat : new Vec3(tf.x, 0, tf.z).normalize();
                    exit = safe(p, target.position().add(across.scale(target.getBbWidth() * 0.5 + 1.1)), target.position().subtract(across.scale(target.getBbWidth() * 0.5 + 1.1)));
                } else {
                    point = ray(p, eye, look, 12.0);
                    exit = safe(p, ground(p, point.subtract(flat.scale(0.6))), p.position());
                }
            }
            case JUDGEMENT -> {
                target = aim(p, Beats.JUDGE_RANGE, 30.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                    Vec3 tf = target.position().subtract(p.position());
                    Vec3 across = new Vec3(tf.x, 0, tf.z).lengthSqr() < 1.0E-4 ? flat : new Vec3(tf.x, 0, tf.z).normalize();
                    exit = safe(p, target.position().subtract(across.scale(target.getBbWidth() * 0.5 + 1.25)), p.position());
                } else {
                    point = ray(p, eye, look, 9.0);
                    exit = safe(p, ground(p, point.subtract(flat.scale(1.3))), p.position());
                }
            }
            default -> {
            }
        }
        Cast c = new Cast(m, p, now, eye, look, point, exit, target);
        ACTIVE.add(c);
        int cd = (int) (m.cooldown * ZConfig.SERVER.cooldowns.get());
        if (m == Move.PUNCH && c.da) {
            cd = cd / 2;
        }
        ready[m.ordinal()] = now + cd;
        syncCooldowns(p);
        broadcast(p, c);
    }

    private static void broadcast(ServerPlayer p, Cast c) {
        Net.toTrackingAndSelf(p, new S2CCast(c.move, p.getId(), c.origin, c.dir, c.point, c.exit, c.start, p.getRandom().nextInt(),
                c.target == null ? -1 : c.target.getId()));
    }

    // ------------------------------------------------------------------------------------------------ geometry

    private static Vec3 ray(ServerPlayer p, Vec3 eye, Vec3 look, double range) {
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.3)) : eye.add(look.scale(range));
    }

    /** Drop a point onto the ground below it (up to 6 blocks). */
    private static Vec3 ground(ServerPlayer p, Vec3 at) {
        BlockHitResult hit = p.level().clip(new ClipContext(at.add(0, 1.0, 0), at.subtract(0, 6.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
    }

    /** {@code want} if the user fits there, else {@code fallback}. */
    private static Vec3 safe(ServerPlayer p, Vec3 want, Vec3 fallback) {
        AABB box = p.getBoundingBox().move(want.subtract(p.position()));
        if (p.level().noCollision(p, box)) {
            return want;
        }
        AABB up = box.move(0, 1.0, 0);
        if (p.level().noCollision(p, up)) {
            return want.add(0, 1.0, 0);
        }
        return fallback;
    }

    /** The living thing he is looking at: within range, inside the cone, in sight. Nearest to the crosshair wins. */
    static LivingEntity aim(ServerPlayer p, double range, double coneDeg) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.4 + 1.0);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double cone = Math.toRadians(coneDeg);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth()) {
                continue;
            }
            double ang = Math.acos(Mth.clamp(to.normalize().dot(look), -1.0, 1.0));
            if (ang > cone && dist > 1.8) {
                continue;
            }
            if (!p.hasLineOfSight(e)) {
                continue;
            }
            double score = dist * (1.0 + ang * 4.0);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** Everything in front of him within reach: what the Stand's fists find. */
    private static List<LivingEntity> front(ServerPlayer p, double reach, double radius) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 centre = eye.add(look.scale(reach * 0.6));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius + reach * 0.4),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() <= reach + e.getBbWidth() && to.normalize().dot(look) > 0.25) {
                out.add(e);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ the scripts

    public static void tick(MinecraftServer server) {
        Iterator<Cast> it = ACTIVE.iterator();
        List<Cast> run = new ArrayList<>();
        while (it.hasNext()) {
            Cast c = it.next();
            if (c.player.isRemoved() || !c.player.isAlive() || c.player.level() != c.level || c.age() > c.move.length) {
                it.remove();
                continue;
            }
            run.add(c);
        }
        for (Cast c : run) {
            int age = c.age();
            hold(c, age);
            script(c, age);
        }
        bleed(server);
    }

    private static void hold(Cast c, int age) {
        ServerPlayer p = c.player;
        if (age > c.move.anchorUntil()) {
            return;
        }
        if (p.position().distanceToSqr(c.anchor) > 0.0025) {
            p.connection.teleport(c.anchor.x, c.anchor.y, c.anchor.z, p.getYRot(), p.getXRot());
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    private static void sound(Cast c, Vec3 at, SoundEvent s, float vol, float pitch) {
        c.level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void hit(Cast c, LivingEntity e, float amount, Vec3 knock, int stun, boolean guardBreak) {
        if (e == null || !e.isAlive()) {
            return;
        }
        if (guardBreak && e instanceof Player victim && victim.isBlocking()) {
            victim.getCooldowns().addCooldown(Items.SHIELD, 100);
            victim.stopUsingItem();
            c.level.broadcastEntityEvent(victim, (byte) 30);
        }
        e.invulnerableTime = 0;
        e.hurt(c.player.damageSources().playerAttack(c.player), dmg(amount));
        e.invulnerableTime = 0;
        if (knock != null) {
            e.setDeltaMovement(knock);
            e.hurtMarked = true;
            e.hasImpulse = true;
        }
        if (stun > 0) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 5, false, false));
        }
    }

    private static void teleport(Cast c, Vec3 to, Vec3 face) {
        ServerPlayer p = c.player;
        Vec3 d = face.subtract(to);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.connection.teleport(to.x, to.y, to.z, yaw, Mth.clamp(pitch, -30.0F, 30.0F));
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    private static void script(Cast c, int age) {
        ServerPlayer p = c.player;
        switch (c.move) {
            case PUNCH -> {
                if (age == Beats.PUNCH_HIT) {
                    boolean any = false;
                    for (LivingEntity e : front(p, 4.0, 1.4)) {
                        hit(c, e, 4.0F, p.getLookAngle().scale(0.35).add(0, 0.12, 0), 0, false);
                        any = true;
                    }
                    if (any) {
                        sound(c, p.getEyePosition().add(p.getLookAngle().scale(2.5)), ModSounds.PUNCH.get(), 0.9F, 1.0F + p.getRandom().nextFloat() * 0.2F);
                    }
                }
            }
            case BARRAGE -> {
                if (age < 6) {
                    Vec3 push = p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.28);
                    p.setDeltaMovement(push.x, p.getDeltaMovement().y, push.z);
                    p.hurtMarked = true;
                }
                int step = c.da ? 1 : 2;
                if (age >= Beats.BARRAGE_START && age <= Beats.BARRAGE_END && (age - Beats.BARRAGE_START) % step == 0) {
                    List<LivingEntity> hits = front(p, 4.2, 1.6);
                    for (LivingEntity e : hits) {
                        hit(c, e, c.da ? 0.6F : 0.9F, p.getLookAngle().scale(0.06), 0, false);
                    }
                    if (!hits.isEmpty() && age % 2 == 0) {
                        sound(c, hits.get(0).getEyePosition(), ModSounds.JAB.get(), 0.75F, 0.9F + p.getRandom().nextFloat() * 0.3F);
                    }
                }
            }
            case FINISHER -> {
                if (age == Beats.FINISH_HIT) {
                    Vec3 look = p.getLookAngle();
                    List<LivingEntity> hits = front(p, 4.6, 1.6);
                    for (LivingEntity e : hits) {
                        hit(c, e, 8.0F, look.multiply(1, 0, 1).normalize().scale(2.1).add(0, 0.55, 0), 0, false);
                    }
                    sound(c, p.getEyePosition().add(look.scale(2.5)), ModSounds.FINISHER.get(), 1.0F, hits.isEmpty() ? 1.3F : 1.0F);
                }
            }
            case KNIVES -> {
                if (age == Beats.KNIFE_THROW) {
                    Vec3 look = p.getLookAngle();
                    Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                    Vec3 right = new Vec3(-flat.z, 0, flat.x);
                    Vec3 hand = p.getEyePosition().add(right.scale(0.55)).add(flat.scale(0.9)).subtract(0, 0.25, 0);
                    Vec3 aim = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
                    Vec3 base = aim.subtract(hand).normalize();
                    for (int i = -1; i <= 1; i++) {
                        Vec3 d = rotateY(base, i * 6.5).add(0, i == 0 ? 0 : 0.03, 0).normalize();
                        c.level.addFreshEntity(KnifeEntity.thrown(c.level, p, hand.add(right.scale(i * 0.18)), d.scale(Beats.KNIFE_SPEED)));
                    }
                    sound(c, hand, ModSounds.KNIFE_THROW.get(), 1.0F, 1.0F);
                }
            }
            case SPEED_SLICE -> {
                if (age == 2) {
                    sound(c, p.getEyePosition(), ModSounds.TOCK.get(), 1.0F, 0.6F);
                }
                if (age == Beats.SLICE_VANISH) {
                    sound(c, p.getEyePosition(), ModSounds.VANISH.get(), 1.0F, 1.1F);
                    teleport(c, c.exit, c.target != null ? c.target.getEyePosition() : c.exit.add(c.flat.scale(-3)));
                }
                if (age == Beats.SLICE_HIT) {
                    sound(c, c.point, ModSounds.SLICE.get(), 1.2F, 1.0F);
                    if (c.target != null && c.target.isAlive()) {
                        hit(c, c.target, 10.0F, new Vec3(0, 0.15, 0), 20, true);
                        BLEEDS.add(new Bleed(c.target, p, c.level.getGameTime() + 60, dmg(0.7F)));
                        Vec3 neck = c.target.getEyePosition().subtract(0, 0.15, 0);
                        c.level.sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.02F, 0.04F), 1.6F), neck.x, neck.y, neck.z, 36, 0.3, 0.1, 0.3, 0.2);
                    }
                }
            }
            case INFINITE_SPEED -> {
                if (age == Beats.INF_VANISH) {
                    sound(c, p.getEyePosition(), ModSounds.VANISH.get(), 1.0F, 0.85F);
                    teleport(c, c.exit, c.target != null ? c.target.getEyePosition() : c.exit.add(c.flat.scale(-3)));
                }
                if (age == Beats.INF_HIT) {
                    sound(c, c.point, ModSounds.FINISHER.get(), 1.1F, 0.85F);
                    if (c.target != null && c.target.isAlive()) {
                        Vec3 away = c.target.position().subtract(p.position()).multiply(1, 0, 1);
                        away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
                        hit(c, c.target, 12.0F, away.scale(0.6).add(0, -0.4, 0), 34, false);
                        c.target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
                    }
                }
            }
            case JUDGEMENT -> {
                if (age == 4) {
                    sound(c, p.getEyePosition(), ModSounds.TOCK.get(), 1.0F, 0.5F);
                }
                if (age >= Beats.JUDGE_LUNGE && age <= Beats.JUDGE_ARRIVE) {
                    float k = (age - Beats.JUDGE_LUNGE) / (float) (Beats.JUDGE_ARRIVE - Beats.JUDGE_LUNGE);
                    Vec3 at = c.anchor.lerp(c.exit, k);
                    Vec3 face = c.target != null ? c.target.getEyePosition() : c.point;
                    teleport(c, at, face);
                    if (age == Beats.JUDGE_LUNGE) {
                        sound(c, at, ModSounds.VANISH.get(), 1.0F, 0.75F);
                    }
                }
                if (age > Beats.JUDGE_ARRIVE && age < Beats.JUDGE_FINAL + 2) {
                    p.setDeltaMovement(Vec3.ZERO);
                    p.fallDistance = 0.0F;
                }
                for (int h : Beats.JUDGE_HITS) {
                    if (age == h) {
                        Vec3 where = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
                        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(where, where).inflate(2.2),
                                e -> e != p && e.isAlive() && !(e instanceof ArmorStand))) {
                            hit(c, e, 3.5F, Vec3.ZERO, 8, false);
                        }
                        sound(c, where, ModSounds.PUNCH.get(), 1.0F, 0.85F + h * 0.006F);
                    }
                }
                if (age == Beats.JUDGE_FINAL) {
                    Vec3 where = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
                    Vec3 away = where.subtract(p.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
                    for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(where, where).inflate(2.6),
                            e -> e != p && e.isAlive() && !(e instanceof ArmorStand))) {
                        hit(c, e, 8.0F, away.scale(2.6).add(0, 0.95, 0), 0, true);
                    }
                    sound(c, where, ModSounds.JUDGEMENT.get(), 1.4F, 1.0F);
                    c.level.sendParticles(ParticleTypes.EXPLOSION, where.x, where.y, where.z, 3, 0.4, 0.4, 0.4, 0.0);
                }
            }
            case DOUBLE_ACCEL -> {
                if (age == 1) {
                    sound(c, p.getEyePosition(), ModSounds.DOUBLE_ACCEL.get(), 1.1F, 1.0F);
                }
                if (age == Beats.DA_BURST) {
                    StandUsers.state(p).daUntil = c.level.getGameTime() + Beats.DA_DURATION;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Beats.DA_DURATION, 2, false, false, true));
                    StandUsers.sync(p);
                }
            }
            case TIME_ACCEL -> {
                if (age == 1) {
                    sound(c, p.getEyePosition(), ModSounds.TIME_ACCEL.get(), 1.4F, 1.0F);
                    sound(c, p.getEyePosition(), ModSounds.MENACING.get(), 1.0F, 1.0F);
                }
                if (age == Beats.TA_START) {
                    TimeAccel.start(p);
                    // every cooldown but the accelerations themselves comes back at once
                    long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
                    for (Move m : Move.values()) {
                        if (m != Move.DOUBLE_ACCEL && m != Move.TIME_ACCEL) {
                            ready[m.ordinal()] = Math.min(ready[m.ordinal()], c.level.getGameTime());
                        }
                    }
                    syncCooldowns(p);
                }
            }
            default -> {
            }
        }
    }

    private static void bleed(MinecraftServer server) {
        Iterator<Bleed> it = BLEEDS.iterator();
        while (it.hasNext()) {
            Bleed b = it.next();
            long now = b.victim.level().getGameTime();
            if (!b.victim.isAlive() || now > b.until) {
                it.remove();
                continue;
            }
            if (now % 10 == 0) {
                b.victim.invulnerableTime = 0;
                b.victim.hurt(b.source.damageSources().playerAttack(b.source), b.perHit);
                if (b.victim.level() instanceof ServerLevel sl) {
                    Vec3 n = b.victim.getEyePosition().subtract(0, 0.2, 0);
                    sl.sendParticles(new DustParticleOptions(new Vector3f(0.65F, 0.0F, 0.03F), 1.0F), n.x, n.y, n.z, 6, 0.15, 0.05, 0.15, 0.02);
                }
            }
        }
    }

    static Vec3 rotateY(Vec3 v, double degrees) {
        double a = Math.toRadians(degrees);
        double cos = Math.cos(a), sin = Math.sin(a);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    // ------------------------------------------------------------------------------------------------ bookkeeping

    public static void syncCooldowns(ServerPlayer p) {
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        int[] total = new int[Move.values().length];
        for (Move m : Move.values()) {
            total[m.ordinal()] = (int) (m.cooldown * ZConfig.SERVER.cooldowns.get());
        }
        Net.toPlayer(p, new S2CCooldowns(ready.clone(), total));
    }

    /** Cooldowns tick down twice as fast while time accelerates: pull every ready time one tick closer. */
    static void hasten(ServerPlayer p) {
        long[] ready = READY.get(p.getUUID());
        if (ready == null) {
            return;
        }
        for (Move m : Move.values()) {
            if (m != Move.DOUBLE_ACCEL && m != Move.TIME_ACCEL && ready[m.ordinal()] > 0) {
                ready[m.ordinal()]--;
            }
        }
    }

    public static void forget(ServerPlayer p) {
        ACTIVE.removeIf(c -> c.player == p);
        READY.remove(p.getUUID());
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
        BLEEDS.clear();
    }
}
