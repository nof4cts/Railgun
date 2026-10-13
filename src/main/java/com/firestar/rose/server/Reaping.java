package com.firestar.rose.server;

import com.firestar.rose.RoseConfig;
import com.firestar.rose.common.RBeats;
import com.firestar.rose.common.RMove;
import com.firestar.rose.net.RoseNet;
import com.firestar.styles.Style;
import com.firestar.styles.Styles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * The server half of Requiem Rose: who may use what, where it lands, and each technique's tick-by-tick script (where the
 * reaper is held or carried, and every cut on its beat in {@link RBeats}).
 */
public final class Reaping {
    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Map<UUID, long[]> READY = new HashMap<>();

    private Reaping() {}

    static final class Cast {
        final RMove move;
        final ServerPlayer player;
        final ServerLevel level;
        final long start;
        final Vec3 anchor;
        final Vec3 eye;
        final Vec3 dir;
        final Vec3 flat;
        final Vec3 right;
        final Vec3 point;
        final Vec3 exit;
        final LivingEntity target;
        final int seed;
        final Set<Integer> struck = new HashSet<>();
        final Set<Integer> struck2 = new HashSet<>();
        Vec3 hold;
        Vec3 dragFrom;

        Cast(RMove move, ServerPlayer p, long start, Vec3 eye, Vec3 dir, Vec3 point, Vec3 exit, LivingEntity target, int seed) {
            this.move = move;
            this.player = p;
            this.level = p.serverLevel();
            this.start = start;
            this.anchor = p.position();
            this.eye = eye;
            this.dir = dir;
            Vec3 f = new Vec3(dir.x, 0, dir.z);
            this.flat = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
            this.right = new Vec3(-flat.z, 0, flat.x);
            this.point = point;
            this.exit = exit;
            this.target = target;
            this.seed = seed;
        }

        int age() {
            return (int) (level.getGameTime() - start);
        }

        Vec3 hand() {
            return eye.subtract(0, 0.3, 0).add(dir.scale(0.8));
        }
    }

    // ------------------------------------------------------------------------------------------------ requests

    public static boolean inCinematic(Entity e) {
        for (Cast c : ACTIVE) {
            if (c.player == e && c.move.cinematic && c.age() <= c.move.length) {
                return true;
            }
        }
        return false;
    }

    public static boolean busy(ServerPlayer p) {
        return current(p) != null;
    }

    private static Cast current(ServerPlayer p) {
        for (Cast c : ACTIVE) {
            if (c.player == p) {
                return c;
            }
        }
        return null;
    }

    private static void deny(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal(why).withStyle(s -> s.withColor(0xFF7FB6)), true);
    }

    /** Every Requiem Rose technique hits two and a half times as hard as its base value (then the config multiplier). */
    static final float POWER = 2.5F;

    private static float dmg(float base) {
        return base * POWER * RoseConfig.SERVER.damage.get().floatValue();
    }

    public static void request(ServerPlayer p, RMove m) {
        if (!p.isAlive() || p.isSpectator() || !Styles.is(p, Style.ROSE)) {
            return;
        }
        Cast running = current(p);
        int form = Reaper.form(p);
        if (m == RMove.ASCEND || m == RMove.RELEASE) {
            if (p.isShiftKeyDown() || m == RMove.RELEASE) {
                if (form == Reaper.MORTAL) {
                    return;
                }
                m = RMove.RELEASE;
            } else if (form == Reaper.ASCENDED) {
                deny(p, "Already ascended. (Sneak + G to release.)");
                return;
            }
        }
        if (running != null) {
            // the Reap chain may cut its own recovery short; nothing else interrupts a technique
            if (!(m == RMove.REAP && running.move == RMove.REAP && running.age() >= RBeats.REAP_HIT + 2)) {
                return;
            }
            ACTIVE.remove(running);
        }
        if (m != RMove.ASCEND && m != RMove.RELEASE && form == Reaper.MORTAL) {
            if (m != RMove.REAP) {
                deny(p, "Ascend first (G).");
            }
            return;
        }
        long now = p.serverLevel().getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[RMove.values().length]);
        if (now < ready[m.ordinal()] && !p.isCreative()) {
            if (m != RMove.REAP) {
                deny(p, m.title + " returns in " + Mth.ceil((ready[m.ordinal()] - now) / 20.0F) + "s");
            }
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize();
        LivingEntity target = null;
        Vec3 point = ray(p, eye, look, 6.0);
        Vec3 exit = p.position();
        int seed = p.getRandom().nextInt();
        switch (m) {
            case REAP -> {
                target = aim(p, 4.8, 50.0);
                seed = (seed & ~3) | Reaper.nextReap(p);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                }
            }
            case CRESCENT -> {
                target = aim(p, RBeats.CRESCENT_RANGE, 10.0);
                point = ray(p, eye, look, RBeats.CRESCENT_RANGE);
            }
            case TETHER -> {
                target = aim(p, RBeats.TETHER_RANGE, 14.0);
                if (target == null) {
                    deny(p, "Nothing to bind.");
                    return;
                }
                point = target.getBoundingBox().getCenter();
            }
            case STEP -> {
                target = aim(p, 24.0, 20.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                    Vec3 tf = target.position().subtract(p.position()).multiply(1, 0, 1);
                    Vec3 across = tf.lengthSqr() < 1.0E-4 ? flat : tf.normalize();
                    exit = safe(p, target.position().add(across.scale(target.getBbWidth() * 0.5 + 1.2)), target.position().subtract(across.scale(1.4)));
                } else {
                    Vec3 to = ray(p, p.position().add(0, 0.9, 0), flat, 11.0);
                    exit = safe(p, ground(p, to.subtract(0, 0.9, 0)), p.position());
                    point = exit.add(0, 1.0, 0);
                }
            }
            case RAY -> {
                target = aim(p, RBeats.RAY_RANGE, 12.0);
                point = target != null ? target.getBoundingBox().getCenter() : ray(p, eye, look, RBeats.RAY_RANGE);
            }
            case REQUIEM -> {
                target = aim(p, 20.0, 26.0);
                if (target == null) {
                    deny(p, "Nobody to reap.");
                    return;
                }
                point = target.getBoundingBox().getCenter();
                exit = target.position();
            }
            case RIFT -> {
                target = aim(p, 30.0, 18.0);
                point = eye.add(flat.scale(RBeats.RIFT_DIST));
            }
            case LAST_BLOOM -> {
                target = aim(p, RBeats.BLOOM_REACH, 20.0);
                point = target != null ? target.position() : ground(p, ray(p, eye, look, 24.0));
            }
            default -> {
            }
        }
        Cast c = new Cast(m, p, now, eye, look, point, exit, target, seed);
        ACTIVE.add(c);
        int cd = m == RMove.REAP && (seed & 3) == 3 ? 12 : m.cooldown;
        ready[m.ordinal()] = now + (int) (cd * RoseConfig.SERVER.cooldowns.get());
        syncReady(p);
        RoseNet.around(p, new RoseNet.Play(m, p.getId(), eye, look, point, exit, now, seed, target == null ? -1 : target.getId()));
    }

    // ------------------------------------------------------------------------------------------------ geometry

    private static Vec3 ray(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
        BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(dir.scale(0.3)) : from.add(dir.scale(range));
    }

    private static Vec3 ground(ServerPlayer p, Vec3 at) {
        BlockHitResult hit = p.level().clip(new ClipContext(at.add(0, 1.5, 0), at.subtract(0, 24.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
    }

    private static Vec3 safe(ServerPlayer p, Vec3 want, Vec3 fallback) {
        AABB box = p.getBoundingBox().move(want.subtract(p.position()));
        if (p.level().noCollision(p, box)) {
            return want;
        }
        if (p.level().noCollision(p, box.move(0, 1.0, 0))) {
            return want.add(0, 1.0, 0);
        }
        return fallback;
    }

    private static boolean valid(Entity e, ServerPlayer p) {
        return e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand);
    }

    static LivingEntity aim(ServerPlayer p, double range, double coneDeg) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.4 + 1.0);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double cone = Math.toRadians(coneDeg);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> valid(e, p))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth()) {
                continue;
            }
            double ang = Math.acos(Mth.clamp(to.normalize().dot(look), -1.0, 1.0));
            if ((ang > cone && dist > 2.0) || !p.hasLineOfSight(e)) {
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

    private static List<LivingEntity> arc(ServerPlayer p, double reach, double minDot) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1), e -> valid(e, p))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() <= reach + e.getBbWidth() * 0.5 && to.normalize().dot(look) > minDot) {
                out.add(e);
            }
        }
        return out;
    }

    static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 d = b.subtract(a);
        double len2 = Math.max(1.0E-6, d.lengthSqr());
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(radius + 1.0), e -> valid(e, p))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double k = Mth.clamp(c.subtract(a).dot(d) / len2, 0.0, 1.0);
            if (c.distanceTo(a.add(d.scale(k))) <= radius + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<LivingEntity> around(Cast c, Vec3 at, double r) {
        return c.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> valid(e, c.player) && e.position().distanceTo(at) < r + 1);
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
            if (age <= c.move.anchorUntil()) {
                place(c, c.anchor);
            }
            if (c.hold != null) {
                place(c, c.hold);
            }
            script(c, age);
        }
    }

    private static void place(Cast c, Vec3 at) {
        ServerPlayer p = c.player;
        if (p.position().distanceToSqr(at) > 0.0025) {
            p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    private static void face(Cast c, Vec3 to, Vec3 look) {
        ServerPlayer p = c.player;
        Vec3 d = look.subtract(to.add(0, p.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.connection.teleport(to.x, to.y, to.z, yaw, Mth.clamp(pitch, -60, 60));
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    private static void hit(Cast c, LivingEntity e, float amount, Vec3 knock, int stun, boolean guardBreak) {
        if (e == null || !e.isAlive()) {
            return;
        }
        if (guardBreak && e instanceof Player v && v.isBlocking()) {
            v.getCooldowns().addCooldown(Items.SHIELD, 100);
            v.stopUsingItem();
            c.level.broadcastEntityEvent(v, (byte) 30);
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
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6, false, false));
        }
    }

    private static Vec3 tc(Cast c) {
        return c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
    }

    private static Vec3 away(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 d = to.subtract(from).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4 ? fallback : d.normalize();
    }

    private static void script(Cast c, int age) {
        ServerPlayer p = c.player;
        switch (c.move) {
            case REAP -> {
                if (age == RBeats.REAP_HIT) {
                    int step = c.seed & 3;
                    boolean heavy = step == 3;
                    for (LivingEntity e : arc(p, heavy ? 5.4 : 4.6, heavy ? -0.2 : 0.2)) {
                        Vec3 out = away(p.position(), e.position(), c.flat);
                        hit(c, e, heavy ? 10.0F : step == 2 ? 6.5F : 5.5F,
                                heavy ? out.scale(1.6).add(0, 0.55, 0) : out.scale(0.3).add(0, step == 1 ? 0.45 : 0.1, 0), 0, heavy);
                    }
                }
            }
            case CRESCENT -> {
                if (age >= RBeats.CRESCENT_THROW && age <= RBeats.CRESCENT_HOME) {
                    Vec3 homeHand = p.getEyePosition().subtract(0, 0.3, 0);
                    Vec3 at = RBeats.crescentAt(c.hand(), c.dir, c.right, age, homeHand);
                    boolean out = age <= RBeats.CRESCENT_TURN;
                    for (LivingEntity e : around(c, at, 2.0)) {
                        if ((out ? c.struck : c.struck2).add(e.getId())) {
                            hit(c, e, 7.5F, away(at, e.position(), c.flat).scale(0.4).add(0, 0.2, 0), 10, false);
                        }
                    }
                }
            }
            case TETHER -> {
                LivingEntity t = c.target;
                if (t == null || !t.isAlive()) {
                    return;
                }
                if (age == RBeats.TETHER_BIND) {
                    hit(c, t, 3.0F, Vec3.ZERO, 30, false);
                    c.dragFrom = t.position();
                }
                if (c.dragFrom != null && age > RBeats.TETHER_BIND && age <= RBeats.TETHER_ARRIVE + 2) {
                    Vec3 dest = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2));
                    double k = age < RBeats.TETHER_YANK ? 0 : Mth.clamp((age - RBeats.TETHER_YANK) / (double) (RBeats.TETHER_ARRIVE - RBeats.TETHER_YANK), 0, 1);
                    k = k * k * (3 - 2 * k);
                    Vec3 to = c.dragFrom.lerp(dest, k).add(0, Math.sin(Math.PI * k) * 1.6, 0);
                    if (c.level.noCollision(t, t.getBoundingBox().move(to.subtract(t.position())))) {
                        t.teleportTo(to.x, to.y, to.z);
                    }
                    t.setDeltaMovement(Vec3.ZERO);
                    t.fallDistance = 0;
                    t.hurtMarked = true;
                }
                if (age == RBeats.TETHER_CUT) {
                    hit(c, t, 9.0F, new Vec3(0, 1.15, 0).add(c.flat.scale(0.2)), 0, true);
                }
            }
            case STEP -> {
                if (age == RBeats.STEP_VANISH) {
                    face(c, c.exit, c.target != null ? tc(c) : c.exit.add(c.flat.scale(3)).add(0, 1.6, 0));
                }
                if (age == RBeats.STEP_CUT && c.target != null) {
                    for (LivingEntity e : around(c, tc(c), 2.6)) {
                        hit(c, e, e == c.target ? 8.5F : 5.0F, away(p.position(), e.position(), c.flat).scale(0.9).add(0, 0.35, 0), 0, true);
                    }
                }
            }
            case RAY -> {
                if (age >= RBeats.RAY_FIRE && age <= RBeats.RAY_END && (age - RBeats.RAY_FIRE) % 3 == 0) {
                    Vec3 aim = tc(c).subtract(c.eye).normalize();
                    Vec3 from = c.hand();
                    BlockHitResult wall = c.level.clip(new ClipContext(from, from.add(aim.scale(RBeats.RAY_RANGE)), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, p));
                    Vec3 end = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : from.add(aim.scale(RBeats.RAY_RANGE));
                    for (LivingEntity e : along(p, from, end, 1.9)) {
                        hit(c, e, 3.4F, aim.scale(0.5), 8, false);
                    }
                }
            }
            case REQUIEM -> {
                LivingEntity t = c.target;
                Vec3 tf = t != null && t.isAlive() ? t.position() : c.exit;
                int[] cuts = RBeats.REQUIEM_CUTS;
                for (int i = 0; i < cuts.length; i++) {
                    if (age == cuts[i]) {
                        Vec3 to = RBeats.requiemTo(tf, c.seed, i);
                        c.hold = safe(p, to.subtract(0, 0.9, 0), tf.add(0, 0.5, 0));
                        face(c, c.hold, tf.add(0, 1, 0));
                        if (t != null) {
                            hit(c, t, 3.2F, Vec3.ZERO, 14, false);
                        }
                    }
                }
                if (t != null && t.isAlive() && age > cuts[0] && age < RBeats.REQUIEM_BURST) {
                    t.setDeltaMovement(Vec3.ZERO);
                    t.hurtMarked = true;
                }
                if (age == RBeats.REQUIEM_GATHER) {
                    c.hold = safe(p, tf.subtract(c.flat.scale(4.0)), p.position());
                    face(c, c.hold, tf.add(0, 1, 0));
                }
                if (age == RBeats.REQUIEM_BURST) {
                    for (LivingEntity e : around(c, tf.add(0, 1, 0), 4.0)) {
                        hit(c, e, e == t ? 16.0F : 7.0F, away(p.position(), e.position(), c.flat).scale(1.4).add(0, 0.9, 0), 0, true);
                    }
                }
                if (age == RBeats.REQUIEM_BURST + 6) {
                    c.hold = null;
                }
            }
            case RIFT -> {
                Vec3 centre = c.eye.add(c.flat.scale(RBeats.RIFT_DIST)).add(0, 1.0, 0);
                Vec3 top = centre.add(0, RBeats.RIFT_HEIGHT * 0.5, 0), bottom = centre.subtract(0, RBeats.RIFT_HEIGHT * 0.5, 0);
                if (age == RBeats.RIFT_CUT) {
                    for (LivingEntity e : along(p, bottom, top, 2.4)) {
                        hit(c, e, 11.0F, Vec3.ZERO, 20, true);
                    }
                }
                if (age > RBeats.RIFT_OPEN && age < RBeats.RIFT_CLOSE) {
                    for (LivingEntity e : around(c, centre, 13.0)) {
                        Vec3 c2 = e.getBoundingBox().getCenter();
                        double y = Mth.clamp(c2.y, bottom.y, top.y);
                        Vec3 pull = new Vec3(centre.x, y, centre.z).subtract(c2);
                        double d = pull.length();
                        if (d > 0.3) {
                            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.normalize().scale(Math.min(0.55, 2.4 / (d + 1.0)))));
                            e.hurtMarked = true;
                        }
                        if (d < 3.0 && (age - RBeats.RIFT_OPEN) % 5 == 0) {
                            hit(c, e, 2.2F, null, 10, false);
                        }
                    }
                }
                if (age == RBeats.RIFT_SNAP) {
                    for (LivingEntity e : around(c, centre, 7.0)) {
                        hit(c, e, 13.0F, away(centre, e.position(), c.flat).scale(1.8).add(0, 0.7, 0), 0, true);
                    }
                }
            }
            case LAST_BLOOM -> {
                double h;
                if (age <= RBeats.BLOOM_RISE_END) {
                    double k = age / (double) RBeats.BLOOM_RISE_END;
                    h = RBeats.BLOOM_HEIGHT * k * k * (3 - 2 * k);
                } else if (age <= RBeats.BLOOM_WAVE_END) {
                    h = RBeats.BLOOM_HEIGHT;
                } else {
                    double k = Mth.clamp((age - RBeats.BLOOM_WAVE_END) / (double) (c.move.length - 6 - RBeats.BLOOM_WAVE_END), 0, 1);
                    h = RBeats.BLOOM_HEIGHT * (1 - k * k * (3 - 2 * k));
                }
                Vec3 at = c.anchor.add(0, h, 0);
                if (c.level.noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
                    place(c, at);
                } else {
                    place(c, p.position());
                }
                Vec3 g = c.anchor;
                if (age == RBeats.BLOOM_IMPACT) {
                    for (LivingEntity e : along(p, g.add(0, 1, 0), g.add(c.flat.scale(RBeats.BLOOM_REACH)).add(0, 1, 0), 4.5)) {
                        hit(c, e, 40.0F, c.flat.scale(1.2).add(0, 1.4, 0), 0, true);
                        c.struck.add(e.getId());
                    }
                }
                if (age > RBeats.BLOOM_IMPACT && age <= RBeats.BLOOM_WAVE_END) {
                    double k = (age - RBeats.BLOOM_IMPACT) / (double) (RBeats.BLOOM_WAVE_END - RBeats.BLOOM_IMPACT);
                    double r = RBeats.BLOOM_REACH * (1 - Math.pow(1 - k, 2.2));
                    for (LivingEntity e : around(c, g, r + 1.5)) {
                        double d = e.position().distanceTo(g);
                        if (d > r - 3.0 && c.struck.add(e.getId())) {
                            hit(c, e, 14.0F, away(g, e.position(), c.flat).scale(1.4).add(0, 0.8, 0), 0, true);
                        }
                    }
                }
            }
            case ASCEND -> {
                if (age == RBeats.ASCEND_IGNITE) {
                    Reaper.setForm(p, Reaper.ASCENDED);
                    for (LivingEntity e : around(c, c.anchor.add(0, 1, 0), 8.0)) {
                        e.setDeltaMovement(away(c.anchor, e.position(), c.flat).scale(1.5).add(0, 0.45, 0));
                        e.hurtMarked = true;
                    }
                }
            }
            case RELEASE -> {
                if (age == 4) {
                    Reaper.setForm(p, Reaper.MORTAL);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ bookkeeping

    public static void syncReady(ServerPlayer p) {
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[RMove.values().length]);
        int[] total = new int[RMove.values().length];
        for (RMove m : RMove.values()) {
            total[m.ordinal()] = (int) (m.cooldown * RoseConfig.SERVER.cooldowns.get());
        }
        RoseNet.to(p, new RoseNet.Ready(ready.clone(), total));
    }

    /** Leaving the style mid-technique: whatever was playing stops. */
    public static void cancel(ServerPlayer p) {
        ACTIVE.removeIf(c -> c.player == p);
    }

    public static void forget(ServerPlayer p) {
        ACTIVE.removeIf(c -> c.player == p);
        READY.remove(p.getUUID());
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
    }
}
