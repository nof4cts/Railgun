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
        final int seed;
        final List<KnifeEntity> knives = new ArrayList<>();

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
            this.seed = player.getRandom().nextInt();
        }

        int age() {
            return (int) (level.getGameTime() - start);
        }
    }

    private record Bleed(LivingEntity victim, ServerPlayer source, long until, float perHit) {}

    // ------------------------------------------------------------------------------------------------ requests

    public static boolean inCinematic(Entity e) {
        for (Cast c : ACTIVE) {
            if (c.player == e && c.move.cinematic && c.age() <= c.move.length) {
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
            }
            return;
        }
        if (m == Move.TIME_ACCEL && TimeAccel.of(p) != null) {
            TimeAccel.cancel(p);
            p.displayClientMessage(Component.literal("Time slows back to its course.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (m == Move.INFINITE_SPEED && InfiniteSpeed.active(p)) {
            InfiniteSpeed.release(p);
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
                boolean blink = m == Move.PUNCH && InfiniteSpeed.active(p);
                target = blink ? aim(p, 12.0, 25.0) : aim(p, 4.5, 40.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                    if (blink && target.distanceTo(p) > 3.0) {
                        // infinite speed: every punch is a blink of light to the target first
                        Vec3 tf = target.position().subtract(p.position());
                        Vec3 across = new Vec3(tf.x, 0, tf.z).lengthSqr() < 1.0E-4 ? flat : new Vec3(tf.x, 0, tf.z).normalize();
                        exit = safe(p, target.position().subtract(across.scale(target.getBbWidth() * 0.5 + 1.3)), p.position());
                    }
                }
            }
            case LIGHTSPEED_RUSH -> {
                Vec3 waist = p.position().add(0, 0.9, 0);
                BlockHitResult wall = p.level().clip(new ClipContext(waist, waist.add(flat.scale(Beats.RUSH_RANGE)), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p));
                double dist = wall.getType() == HitResult.Type.BLOCK ? Math.max(0.0, wall.getLocation().distanceTo(waist) - 0.7) : Beats.RUSH_RANGE;
                exit = safe(p, ground(p, p.position().add(flat.scale(dist))), p.position().add(flat.scale(dist)));
                point = exit.add(0, 1.0, 0);
                for (LivingEntity e : along(p, p.position(), exit, 1.8)) {
                    target = e;
                    break;
                }
            }
            case KNIFE_STORM -> {
                target = aim(p, 40.0, 16.0);
                point = target != null ? target.getBoundingBox().getCenter() : ray(p, eye, look, 30.0);
            }
            case KNIVES -> {
                target = aim(p, 40.0, 14.0);
                point = target != null ? target.getBoundingBox().getCenter() : ray(p, eye, look, 40.0);
            }
            case SPEED_SLICE -> {
                double range = Beats.SLICE_RANGE;
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
                    exit = target.position();
                } else {
                    point = ray(p, eye, look, 9.0);
                    exit = ground(p, point);
                    point = exit.add(0, 1.0, 0);
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
        Net.toTrackingAndSelf(p, new S2CCast(c.move, p.getId(), c.origin, c.dir, c.point, c.exit, c.start, c.seed,
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

    /** Every living thing within {@code radius} of the line from a to b (feet to feet, checked at the waist). */
    static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
        Vec3 wa = a.add(0, 0.9, 0), wb = b.add(0, 0.9, 0);
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(wa, wb).inflate(radius + 1.0);
        Vec3 d = wb.subtract(wa);
        double len2 = Math.max(1.0E-6, d.lengthSqr());
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double k = Mth.clamp(c.subtract(wa).dot(d) / len2, 0.0, 1.0);
            if (c.distanceTo(wa.add(d.scale(k))) <= radius + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        out.sort((x, y) -> Double.compare(x.distanceToSqr(a), y.distanceToSqr(a)));
        return out;
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
                if (age == 1 && c.exit.distanceToSqr(c.anchor) > 1.0) {
                    teleport(c, c.exit, c.point);
                }
                if (age == Beats.PUNCH_HIT) {
                    for (LivingEntity e : front(p, 4.0, 1.4)) {
                        hit(c, e, 4.0F, p.getLookAngle().scale(0.35).add(0, 0.12, 0), 0, false);
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
                }
            }
            case SPEED_SLICE -> {
                if (age == 2) {
                }
                if (age == Beats.SLICE_VANISH) {
                    teleport(c, c.exit, c.target != null ? c.target.getEyePosition() : c.exit.add(c.flat.scale(-3)));
                }
                if (age == Beats.SLICE_HIT) {
                    if (c.target != null && c.target.isAlive()) {
                        hit(c, c.target, 10.0F, new Vec3(0, 0.15, 0), 20, true);
                        BLEEDS.add(new Bleed(c.target, p, c.level.getGameTime() + 60, dmg(0.7F)));
                    }
                }
            }
            case JUDGEMENT -> {
                // eight dashes of light, each straight through him from a new side
                for (int i = 0; i < Beats.JUDGE_DASHES.length; i++) {
                    if (age == Beats.JUDGE_DASHES[i]) {
                        Vec3 from = i == 0 ? c.anchor : Beats.judgeDash(c.exit, c.seed, i - 1);
                        Vec3 to = Beats.judgeDash(c.exit, c.seed, i);
                        teleport(c, safe(p, to, to.add(0, 1.0, 0)), c.exit.add(0, 1.2, 0));
                        for (LivingEntity e : along(p, from, to, 1.6)) {
                            hit(c, e, 3.0F, Vec3.ZERO, 14, false);
                        }
                    }
                }
                if (age > Beats.JUDGE_DASHES[0] && age < Beats.JUDGE_FINAL + 2) {
                    p.setDeltaMovement(Vec3.ZERO);
                    p.fallDistance = 0.0F;
                }
                if (age == Beats.JUDGE_FINAL) {
                    Vec3 where = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
                    Vec3 away = where.subtract(p.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
                    for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(where, where).inflate(2.8),
                            e -> e != p && e.isAlive() && !(e instanceof ArmorStand))) {
                        hit(c, e, 9.0F, away.scale(1.6).add(0, 1.1, 0), 0, true);
                    }
                }
            }
            case INFINITE_SPEED -> {
                if (age == Beats.INF_ON) {
                    InfiniteSpeed.begin(p);
                }
            }
            case LIGHTSPEED_RUSH -> {
                int step = age - Beats.RUSH_GO + 1;
                if (step >= 1 && step <= Beats.RUSH_STEPS) {
                    Vec3 at = c.anchor.lerp(c.exit, step / (double) Beats.RUSH_STEPS);
                    teleport(c, at, at.add(c.flat.scale(5)).add(0, 1.6, 0));
                }
                if (age == Beats.RUSH_GO + Beats.RUSH_STEPS - 1) {
                    Vec3 side = new Vec3(-c.flat.z, 0, c.flat.x);
                    for (LivingEntity e : along(p, c.anchor, c.exit, 1.8)) {
                        double s2 = e.position().subtract(c.anchor).dot(side) >= 0 ? 1 : -1;
                        hit(c, e, 5.0F, side.scale(0.9 * s2).add(c.flat.scale(0.3)).add(0, 0.45, 0), 10, false);
                    }
                }
            }
            case KNIFE_STORM -> {
                Vec3 right = new Vec3(-c.flat.z, 0, c.flat.x);
                Vec3 centre = c.anchor.add(0, 1.9, 0).subtract(c.flat.scale(0.4));
                int n = Beats.STORM_KNIVES;
                int i = age - Beats.STORM_SPAWN;
                if (i >= 0 && i < n) {
                    // the knives appear one by one in a halo behind him, held in still time
                    double a = Math.PI * (0.08 + 0.84 * i / (n - 1.0));
                    double row = i % 2 == 0 ? 0.0 : 0.55;
                    Vec3 at = centre.add(right.scale(Math.cos(a) * (2.0 + row))).add(0, Math.sin(a) * (1.3 + row), 0).subtract(c.flat.scale(row * 0.3));
                    KnifeEntity k = KnifeEntity.held(c.level, p, at, c.target != null ? c.target.getBoundingBox().getCenter() : c.point);
                    k.damage = 2.6F;
                    c.level.addFreshEntity(k);
                    c.knives.add(k);
                }
                if (age == Beats.STORM_AIM) {
                    Vec3 aim = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
                    for (KnifeEntity k : c.knives) {
                        k.face(aim);
                    }
                }
                if (age >= Beats.STORM_FIRE && age < Beats.STORM_FIRE + 4) {
                    Vec3 aim = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
                    int from = (age - Beats.STORM_FIRE) * 3;
                    for (int j = from; j < Math.min(c.knives.size(), from + 3); j++) {
                        KnifeEntity k = c.knives.get(j);
                        if (!k.isRemoved()) {
                            k.launch(aim.subtract(k.position()).normalize().scale(Beats.KNIFE_SPEED * 1.15));
                        }
                    }
                }
                if (age == c.move.length) {
                    for (KnifeEntity k : c.knives) {
                        if (!k.isRemoved() && k.isHeld()) {
                            k.discard();
                        }
                    }
                }
            }
            case DOUBLE_ACCEL -> {
                if (age == 1) {
                }
                if (age == Beats.DA_BURST) {
                    StandUsers.state(p).daUntil = c.level.getGameTime() + Beats.DA_DURATION;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Beats.DA_DURATION, 2, false, false, true));
                    StandUsers.sync(p);
                }
            }
            case TIME_ACCEL -> {
                if (age == 1) {
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
