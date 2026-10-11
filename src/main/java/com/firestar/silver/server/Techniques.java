package com.firestar.silver.server;

import com.firestar.silver.common.Beats;
import com.firestar.silver.common.Move;
import com.firestar.silver.config.ZConfig;
import com.firestar.silver.network.Net;
import com.firestar.silver.network.S2CCast;
import com.firestar.silver.network.S2CCooldowns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * The server half of every move: who may use what, where it goes, and the tick-by-tick script that holds the fighter
 * for a cinematic, moves him through his target, and lands every blow on its beat in {@link Beats}.
 */
public final class Techniques {
    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Map<UUID, long[]> READY = new HashMap<>();

    private Techniques() {}

    static final class Cast {
        final Move move;
        final ServerPlayer player;
        final ServerLevel level;
        final long start;
        final Vec3 anchor;
        final Vec3 origin;
        final Vec3 dir;
        final Vec3 flat;
        final Vec3 point;
        final Vec3 exit;
        final LivingEntity target;
        final int seed;
        final int form;
        Vec3 sphereEnd;
        Vec3 liftFrom;

        Cast(Move move, ServerPlayer player, long start, Vec3 origin, Vec3 dir, Vec3 point, Vec3 exit, LivingEntity target, int seed) {
            this.move = move;
            this.player = player;
            this.level = player.serverLevel();
            this.start = start;
            this.anchor = player.position();
            this.origin = origin;
            this.dir = dir;
            Vec3 f = new Vec3(dir.x, 0.0, dir.z);
            this.flat = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
            this.point = point;
            this.exit = exit;
            this.target = target;
            this.seed = seed;
            this.form = Fighters.form(player);
        }

        int age() {
            return (int) (level.getGameTime() - start);
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

    private static Cast busy(ServerPlayer p) {
        for (Cast c : ACTIVE) {
            if (c.player == p) {
                return c;
            }
        }
        return null;
    }

    private static void deny(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.AQUA), true);
    }

    private static float dmg(float base) {
        return base * ZConfig.SERVER.damage.get().floatValue();
    }

    public static void request(ServerPlayer p, Move m) {
        if (!p.isAlive() || p.isSpectator() || busy(p) != null) {
            return;
        }
        int form = Fighters.form(p);
        // G climbs the awakening one step; sneak+G lets it go
        if (m == Move.AWAKEN_OMEN || m == Move.AWAKEN_ZENITH || m == Move.REVERT) {
            if (p.isShiftKeyDown() || m == Move.REVERT) {
                if (form == Fighters.NONE) {
                    return;
                }
                m = Move.REVERT;
            } else if (form == Fighters.ZENITH) {
                deny(p, "Already at the summit. (Sneak + G to let it go.)");
                return;
            } else {
                m = form == Fighters.NONE ? Move.AWAKEN_OMEN : Move.AWAKEN_ZENITH;
            }
        }
        boolean awakening = m == Move.AWAKEN_OMEN || m == Move.AWAKEN_ZENITH || m == Move.REVERT;
        if (!awakening && form == Fighters.NONE) {
            if (m != Move.STRIKE) {
                deny(p, "Awaken first (G).");
            }
            return;
        }
        if (form < m.form) {
            deny(p, m.title + " needs " + (m.form == 1 ? "Instinct Omen" : "Silver Zenith") + " (G to awaken).");
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        if (now < ready[m.ordinal()] && !p.isCreative()) {
            if (m != Move.STRIKE) {
                deny(p, m.title + " is ready in " + Mth.ceil((ready[m.ordinal()] - now) / 20.0F) + "s");
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
            case STRIKE -> {
                target = aim(p, 4.2, 45.0);
                seed = (seed & ~3) | Fighters.nextCombo(p);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                }
            }
            case HEAVY -> {
                target = aim(p, 4.5, 45.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                }
            }
            case VOLLEY -> {
                target = aim(p, Beats.BLAST_RANGE, 12.0);
                point = target != null ? target.getBoundingBox().getCenter() : ray(p, eye, look, Beats.BLAST_RANGE);
            }
            case VANISH -> {
                target = aim(p, 24.0, 22.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                    Vec3 tf = target.position().subtract(p.position()).multiply(1, 0, 1);
                    Vec3 across = tf.lengthSqr() < 1.0E-4 ? flat : tf.normalize();
                    exit = safe(p, target.position().add(across.scale(target.getBbWidth() * 0.5 + 1.0)), target.position().subtract(across.scale(1.4)));
                } else {
                    Vec3 to = ray(p, p.position().add(0, 0.9, 0), flat, 10.0);
                    exit = safe(p, ground(p, to.subtract(0, 0.9, 0)), p.position());
                    point = exit.add(0, 1.0, 0);
                }
            }
            case WAVE, FINAL -> {
                double range = m == Move.WAVE ? Beats.WAVE_RANGE : Beats.FINAL_RANGE;
                target = aim(p, range, 14.0);
                point = target != null ? target.getBoundingBox().getCenter() : ray(p, eye, look, range);
            }
            case FLURRY, METEOR, RUSH -> {
                target = aim(p, 18.0, 28.0);
                if (target == null) {
                    deny(p, "Nobody to strike.");
                    return;
                }
                point = target.getBoundingBox().getCenter();
                Vec3 tf = target.position().subtract(p.position()).multiply(1, 0, 1);
                Vec3 across = tf.lengthSqr() < 1.0E-4 ? flat : tf.normalize();
                exit = m == Move.FLURRY ? safe(p, target.position().subtract(across.scale(target.getBbWidth() * 0.5 + 1.0)), p.position())
                        : target.position();
            }
            case SPHERE -> {
                target = aim(p, 40.0, 18.0);
                point = target != null ? target.position() : ground(p, ray(p, eye, look, 30.0));
            }
            default -> {
            }
        }
        Cast c = new Cast(m, p, now, eye, look, point, exit, target, seed);
        ACTIVE.add(c);
        ready[m.ordinal()] = now + (int) (m.cooldown * ZConfig.SERVER.cooldowns.get());
        if (m == Move.STRIKE && form == Fighters.ZENITH) {
            ready[m.ordinal()] = now + 3;
        }
        syncCooldowns(p);
        Net.toTrackingAndSelf(p, new S2CCast(m, p.getId(), eye, look, point, exit, now, seed, target == null ? -1 : target.getId()));
    }

    // ------------------------------------------------------------------------------------------------ geometry

    private static Vec3 ray(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
        BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(dir.scale(0.3)) : from.add(dir.scale(range));
    }

    private static Vec3 ground(ServerPlayer p, Vec3 at) {
        BlockHitResult hit = p.level().clip(new ClipContext(at.add(0, 1.0, 0), at.subtract(0, 8.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
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

    static LivingEntity aim(ServerPlayer p, double range, double coneDeg) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.4 + 1.0);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double cone = Math.toRadians(coneDeg);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth()) {
                continue;
            }
            double ang = Math.acos(Mth.clamp(to.normalize().dot(look), -1.0, 1.0));
            if ((ang > cone && dist > 1.8) || !p.hasLineOfSight(e)) {
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

    /** Everything within {@code radius} of the segment a-b (beams). */
    static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 d = b.subtract(a);
        double len2 = Math.max(1.0E-6, d.lengthSqr());
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(radius + 1.0),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double k = Mth.clamp(c.subtract(a).dot(d) / len2, 0.0, 1.0);
            if (c.distanceTo(a.add(d.scale(k))) <= radius + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<LivingEntity> around(Cast c, Vec3 at, double r) {
        return c.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> e != c.player && e.isAlive() && !(e instanceof ArmorStand));
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
        p.connection.teleport(to.x, to.y, to.z, yaw, Mth.clamp(pitch, -60.0F, 60.0F));
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    private static void still(ServerPlayer p) {
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    private static Vec3 targetCentre(Cast c) {
        return c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
    }

    private static void beam(Cast c, Vec3 from, Vec3 dir, double range, double radius, float amount) {
        BlockHitResult wall = c.level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
        Vec3 end = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : from.add(dir.scale(range));
        for (LivingEntity e : along(c.player, from, end, radius)) {
            hit(c, e, amount, dir.scale(0.45), 6, false);
        }
    }

    private static void script(Cast c, int age) {
        ServerPlayer p = c.player;
        boolean zenith = c.form == Fighters.ZENITH;
        switch (c.move) {
            case STRIKE -> {
                if (age == Beats.STRIKE_HIT) {
                    int step = c.seed & 3;
                    float amount = step == 2 ? 5.0F : 3.5F;
                    Vec3 knock = p.getLookAngle().scale(step == 2 ? 0.8 : 0.25).add(0, step == 2 ? 0.3 : 0.08, 0);
                    for (LivingEntity e : front(p, 4.0, 1.4)) {
                        hit(c, e, amount * (zenith ? 1.25F : 1.0F), knock, 0, false);
                    }
                }
            }
            case HEAVY -> {
                if (age == Beats.HEAVY_HIT) {
                    for (LivingEntity e : front(p, 4.6, 1.6)) {
                        hit(c, e, 8.0F, p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0).add(0, 0.6, 0), 0, true);
                    }
                }
            }
            case VOLLEY -> {
                for (int i = 0; i < Beats.VOLLEY_SHOTS; i++) {
                    int fire = Beats.VOLLEY_FIRST + i * Beats.VOLLEY_GAP;
                    Vec3 d = Beats.blastDir(c.target != null ? targetCentre(c).subtract(c.origin).normalize() : c.dir, c.seed, i);
                    Vec3 from = c.origin.add(d.scale(0.8));
                    BlockHitResult wall = c.level.clip(new ClipContext(from, from.add(d.scale(Beats.BLAST_RANGE)), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, p));
                    Vec3 end = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : from.add(d.scale(Beats.BLAST_RANGE));
                    int arrive = fire + (int) Math.ceil(end.distanceTo(from) / Beats.BLAST_SPEED);
                    if (age == arrive) {
                        for (LivingEntity e : along(p, from, end, 1.1)) {
                            hit(c, e, 2.2F, d.scale(0.25), 0, false);
                            break;
                        }
                        for (LivingEntity e : around(c, end, 1.6)) {
                            hit(c, e, 1.0F, null, 0, false);
                        }
                    }
                }
            }
            case VANISH -> {
                if (age == Beats.VANISH_AT) {
                    teleport(c, c.exit, c.target != null ? targetCentre(c) : c.exit.add(c.flat.scale(3)).add(0, 1.6, 0));
                }
            }
            case WAVE -> {
                if (age >= Beats.WAVE_FIRE && age <= Beats.WAVE_END && (age - Beats.WAVE_FIRE) % 3 == 0) {
                    Vec3 aim = targetCentre(c).subtract(c.origin).normalize();
                    beam(c, c.origin.subtract(0, 0.3, 0).add(aim.scale(0.8)), aim, Beats.WAVE_RANGE, zenith ? 2.2 : 1.5, zenith ? 4.0F : 2.8F);
                }
            }
            case FLURRY -> {
                if (age == Beats.FLURRY_DASH) {
                    teleport(c, c.exit, targetCentre(c));
                }
                if (age > Beats.FLURRY_DASH && age < Beats.FLURRY_FINAL + 2) {
                    still(p);
                }
                if (age >= Beats.FLURRY_FIRST && age <= Beats.FLURRY_LAST && c.target != null) {
                    hit(c, c.target, 0.9F, Vec3.ZERO, 6, false);
                }
                if (age == Beats.FLURRY_FINAL) {
                    Vec3 away = targetCentre(c).subtract(p.position()).multiply(1, 0, 1).normalize();
                    for (LivingEntity e : around(c, targetCentre(c), 2.4)) {
                        hit(c, e, 8.0F, away.scale(2.2).add(0, 0.7, 0), 0, true);
                    }
                }
            }
            case METEOR -> {
                LivingEntity t = c.target;
                if (t == null || !t.isAlive()) {
                    return;
                }
                if (age == 3) {
                    Vec3 tf = t.position().subtract(p.position()).multiply(1, 0, 1);
                    Vec3 across = tf.lengthSqr() < 1.0E-4 ? c.flat : tf.normalize();
                    teleport(c, safe(p, t.position().subtract(across.scale(t.getBbWidth() * 0.5 + 0.9)), p.position()), t.getBoundingBox().getCenter());
                }
                if (age == Beats.METEOR_LAUNCH) {
                    hit(c, t, 4.0F, null, 0, false);
                    c.liftFrom = t.position();
                }
                // the target is carried, not thrown: up on the launch, held for the hits, driven down by the spike
                if (c.liftFrom != null && age > Beats.METEOR_LAUNCH && age <= Beats.METEOR_SLAM) {
                    double h;
                    if (age <= Beats.METEOR_UP) {
                        double k = (age - Beats.METEOR_LAUNCH) / (double) (Beats.METEOR_UP - Beats.METEOR_LAUNCH);
                        h = 5.0 * (1 - (1 - k) * (1 - k));
                    } else if (age < Beats.METEOR_SPIKE) {
                        h = 5.0 + 0.15 * Math.sin(age * 0.9);
                    } else {
                        double k = (age - Beats.METEOR_SPIKE) / (double) (Beats.METEOR_SLAM - Beats.METEOR_SPIKE);
                        h = 5.0 * (1 - k * k);
                    }
                    Vec3 to = c.liftFrom.add(0, h, 0);
                    if (c.level.noCollision(t, t.getBoundingBox().move(to.subtract(t.position())))) {
                        t.teleportTo(to.x, to.y, to.z);
                    }
                    t.setDeltaMovement(Vec3.ZERO);
                    t.fallDistance = 0;
                    t.hurtMarked = true;
                }
                for (int h : Beats.METEOR_HITS) {
                    if (age == h) {
                        hit(c, t, 2.5F, null, 10, false);
                    }
                }
                if (age == Beats.METEOR_SPIKE) {
                    hit(c, t, 5.0F, null, 0, false);
                }
                if (age == Beats.METEOR_SLAM) {
                    Vec3 g = ground(p, t.position());
                    teleport(c, safe(p, g.add(c.flat.scale(-1.6)), p.position()), t.getBoundingBox().getCenter());
                    for (LivingEntity e : around(c, g, 4.0)) {
                        Vec3 away = e.position().subtract(g).multiply(1, 0, 1);
                        away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
                        hit(c, e, e == t ? 9.0F : 5.0F, away.scale(1.0).add(0, 0.5, 0), 0, true);
                    }
                }
            }
            case RUSH -> {
                for (int i = 0; i < Beats.RUSH_DASHES.length; i++) {
                    if (age == Beats.RUSH_DASHES[i]) {
                        Vec3 to = Beats.rushDash(c.exit, c.seed, i);
                        teleport(c, safe(p, to, to.add(0, 1.0, 0)), targetCentre(c));
                        if (c.target != null) {
                            hit(c, c.target, 2.6F, Vec3.ZERO, 12, false);
                        }
                    }
                }
                if (age > Beats.RUSH_DASHES[0] && age < Beats.RUSH_FINAL + 2) {
                    still(p);
                }
                if (age == Beats.RUSH_FINAL) {
                    Vec3 tc = targetCentre(c);
                    Vec3 away = tc.subtract(p.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
                    for (LivingEntity e : around(c, tc, 2.8)) {
                        hit(c, e, 10.0F, away.scale(2.6).add(0, 0.9, 0), 0, true);
                    }
                }
            }
            case SPHERE -> {
                if (age == Beats.SPHERE_BOOM) {
                    Vec3 at = c.target != null && c.target.isAlive() ? c.target.position() : c.point;
                    float r = zenith ? 7.0F : 5.5F;
                    for (LivingEntity e : around(c, at.add(0, 1, 0), r)) {
                        Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
                        away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
                        float k = (float) (1.0 - e.position().distanceTo(at) / (r + 1.0));
                        hit(c, e, (zenith ? 28.0F : 20.0F) * Math.max(0.35F, k), away.scale(1.6).add(0, 1.0, 0), 0, true);
                    }
                }
            }
            case FINAL -> {
                if (age >= Beats.FINAL_FIRE && age <= Beats.FINAL_END && (age - Beats.FINAL_FIRE) % 2 == 0) {
                    Vec3 aim = targetCentre(c).subtract(c.origin).normalize();
                    beam(c, c.origin.subtract(0, 0.3, 0).add(aim.scale(0.8)), aim, Beats.FINAL_RANGE, 2.8, 2.6F);
                }
            }
            case AWAKEN_OMEN -> {
                if (age == Beats.OMEN_SURGE) {
                    Fighters.setForm(p, Fighters.OMEN);
                    shove(c, 6.0, 1.0);
                }
            }
            case AWAKEN_ZENITH -> {
                if (age == Beats.ZENITH_SURGE) {
                    Fighters.setForm(p, Fighters.ZENITH);
                    shove(c, 9.0, 1.6);
                }
            }
            case REVERT -> {
                if (age == 4) {
                    Fighters.setForm(p, Fighters.NONE);
                }
            }
            default -> {
            }
        }
    }

    /** The surge of a transformation pushes everything nearby back. */
    private static void shove(Cast c, double r, double strength) {
        for (LivingEntity e : around(c, c.anchor.add(0, 1, 0), r)) {
            Vec3 away = e.position().subtract(c.anchor).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? c.flat : away.normalize();
            e.setDeltaMovement(away.scale(strength).add(0, 0.35, 0));
            e.hurtMarked = true;
        }
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

    public static void forget(ServerPlayer p) {
        ACTIVE.removeIf(c -> c.player == p);
        READY.remove(p.getUUID());
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
    }
}
