package com.firestar.santoryu.server;

import com.firestar.santoryu.common.Beats;
import com.firestar.santoryu.common.Move;
import com.firestar.santoryu.config.ZConfig;
import com.firestar.santoryu.network.Net;
import com.firestar.santoryu.network.S2CCast;
import com.firestar.santoryu.network.S2CCooldowns;
import com.firestar.santoryu.registry.KatanaItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The server half of every technique: who may cast what, where it goes, and the tick-by-tick script that holds the
 * swordsman for his cinematic, moves him through his target, and lands the cuts on the beats in {@link Beats}.
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
        final float power;
        final Set<Integer> struck = new HashSet<>();
        Vec3 shot;

        Cast(Move move, ServerPlayer player, long start, Vec3 origin, Vec3 dir, Vec3 point, Vec3 exit, float power) {
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
            this.power = power;
        }

        int age() {
            return (int) (level.getGameTime() - start);
        }
    }

    // ------------------------------------------------------------------------------------------------ requests

    public static boolean inCinematic(Entity e) {
        for (Cast c : ACTIVE) {
            if (c.player == e && c.move.cinematic && c.age() <= c.move.anchorUntil() + 12) {
                return true;
            }
        }
        return false;
    }

    private static boolean busy(ServerPlayer p) {
        for (Cast c : ACTIVE) {
            if (c.player == p) {
                return true;
            }
        }
        return false;
    }

    private static void deny(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true);
    }

    public static void request(ServerPlayer p, Move m) {
        if (!p.isAlive() || p.isSpectator()) {
            return;
        }
        if (!com.firestar.styles.Styles.is(p, com.firestar.styles.Style.SWORDS)) {
            deny(p, "Take up Santoryu first (N).");
            return;
        }
        if (busy(p)) {
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        if (now < ready[m.ordinal()] && !p.isCreative()) {
            deny(p, m.title + " is ready in " + Mth.ceil((ready[m.ordinal()] - now) / 20.0F) + "s");
            return;
        }
        if (!(p.getMainHandItem().getItem() instanceof KatanaItem)) {
            deny(p, "Hold a katana to use " + m.title);
            return;
        }
        Blades blades = Blades.of(p);
        if (blades.count < m.need.blades) {
            deny(p, m.title + " needs " + m.need.blades + " different katana (you carry " + blades.count + ")");
            return;
        }
        if (m.need.enma && !blades.enma) {
            deny(p, m.title + " needs Enma");
            return;
        }

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double range = switch (m) {
            case ONI_GIRI -> Beats.ONI_RANGE;
            case SHISHI_SONSON -> Beats.SHISHI_RANGE;
            case SANZEN_SEKAI -> Beats.SANZEN_RANGE;
            case ASURA -> Beats.ASURA_RANGE;
            case RENGOKU -> Beats.RENGOKU_RANGE;
            case DRAGON -> Beats.DRAGON_RANGE;
            case KOKUJO -> 12.0;
            default -> 40.0;
        };
        Vec3 point = aim(p, eye, look, range);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 exit = p.position();
        switch (m) {
            case ONI_GIRI, SHISHI_SONSON, SANZEN_SEKAI, ASURA, RENGOKU, DRAGON -> {
                Vec3 feetTarget = new Vec3(point.x, p.getY(), point.z);
                double through = m == Move.ONI_GIRI ? 2.5 : 3.5;
                Vec3 want = feetTarget.add(flat.scale(through));
                double max = range + through;
                Vec3 delta = want.subtract(p.position());
                if (delta.length() > max) {
                    want = p.position().add(delta.normalize().scale(max));
                }
                exit = safeExit(p, p.position(), want);
            }
            case KOKUJO -> point = groundAt(p, p.position().add(flat.scale(7.0)), p.getY());
            default -> {
            }
        }

        float cd = (float) (m.cooldownSeconds * 20 * ZConfig.SERVER.cooldowns.get());
        ready[m.ordinal()] = now + (long) cd;
        Cast cast = new Cast(m, p, now, eye, look, point, exit, blades.power);
        ACTIVE.add(cast);
        syncCooldowns(p);
        Net.toTrackingAndSelf(p, new S2CCast(m, p.getId(), eye, look, point, exit, now, p.getRandom().nextInt(),
                blades.right, blades.left, blades.mouth));
    }

    public static void syncCooldowns(ServerPlayer p) {
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        int[] total = new int[Move.values().length];
        for (Move m : Move.values()) {
            total[m.ordinal()] = (int) (m.cooldownSeconds * 20 * ZConfig.SERVER.cooldowns.get());
        }
        Net.toPlayer(p, new S2CCooldowns(ready.clone(), total));
    }

    /** What the swordsman is looking at: the living thing nearest his line of sight, else the block, else open air. */
    private static Vec3 aim(ServerPlayer p, Vec3 eye, Vec3 look, double range) {
        ServerLevel level = p.serverLevel();
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB box = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(4.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 to = c.subtract(eye);
            double along = to.dot(look);
            if (along < 0.5 || along > reach + 1.0) {
                continue;
            }
            double off = to.subtract(look.scale(along)).length() - e.getBbWidth() * 0.5;
            if (off > 1.2 + along * 0.08) {
                continue;
            }
            double score = off * 4.0 + along * 0.15;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            return best.getBoundingBox().getCenter();
        }
        return block.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : block.getLocation();
    }

    private static Vec3 groundAt(ServerPlayer p, Vec3 at, double fallbackY) {
        BlockHitResult down = p.serverLevel().clip(new ClipContext(at.add(0, 4, 0), at.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        return down.getType() == HitResult.Type.MISS ? new Vec3(at.x, fallbackY, at.z) : down.getLocation();
    }

    /** Walks the swordsman's box from {@code from} toward {@code to}, climbing one-block steps, and stops at walls. */
    private static Vec3 safeExit(ServerPlayer p, Vec3 from, Vec3 to) {
        ServerLevel level = p.serverLevel();
        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.4));
        Vec3 last = from;
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.add(delta.scale(i / (double) steps));
            at = new Vec3(at.x, last.y, at.z);
            AABB box = p.getDimensions(p.getPose()).makeBoundingBox(at);
            if (!level.noCollision(p, box)) {
                boolean climbed = false;
                for (int up = 1; up <= 2; up++) {
                    if (level.noCollision(p, box.move(0, up, 0))) {
                        at = at.add(0, up, 0);
                        climbed = true;
                        break;
                    }
                }
                if (!climbed) {
                    break;
                }
            }
            last = at;
        }
        return last;
    }

    // ------------------------------------------------------------------------------------------------ the scripts

    public static void tick(MinecraftServer server) {
        Iterator<Cast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            int t = c.age();
            if (t > c.move.length || !c.player.isAlive() || c.player.isRemoved() || c.player.serverLevel() != c.level) {
                it.remove();
                continue;
            }
            if (t < c.move.anchorUntil()) {
                hold(c);
            }
            try {
                Carve.floor = Math.min(c.anchor.y, c.exit.y);
                run(c, t);
            } catch (RuntimeException ex) {
                com.firestar.santoryu.SantoryuMod.LOGGER.error("Technique {} failed at tick {}", c.move, t, ex);
                it.remove();
            } finally {
                Carve.floor = Double.NEGATIVE_INFINITY;
            }
        }
    }

    private static void hold(Cast c) {
        ServerPlayer p = c.player;
        if (p.position().distanceToSqr(c.anchor) > 0.01) {
            p.teleportTo(c.anchor.x, c.anchor.y, c.anchor.z);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    private static void move(Cast c, Vec3 to) {
        ServerPlayer p = c.player;
        p.teleportTo(to.x, to.y, to.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    private static float dmg(Cast c, float base) {
        return (float) (base * c.power * ZConfig.SERVER.damage.get());
    }

    private static void run(Cast c, int t) {
        Vec3 feet = c.anchor;
        Vec3 chest = feet.add(0, 1.1, 0);
        switch (c.move) {
            case ONI_GIRI -> {
                if (t == Beats.ONI_DASH) {
                    move(c, c.exit);
                    dust(c, chest, c.exit.add(0, 1.1, 0), 0xFFFFFF, 18);
                }
                if (t == Beats.ONI_HIT) {
                    strike(c, chest, c.exit.add(0, 1.1, 0), 1.8, dmg(c, 16.0F), 0.6, 0.35, null);
                    sweep(c, c.exit.add(0, 1.1, 0), 3);
                }
            }
            case TATSU_MAKI -> {
                for (int i = 0; i < Beats.TATSU_PULSES; i++) {
                    if (t == Beats.TATSU_FIRST + i * Beats.TATSU_EVERY) {
                        boolean last = i == Beats.TATSU_PULSES - 1;
                        twister(c, feet, 5.5, 4.5, dmg(c, last ? 9.0F : 4.0F), last ? 1.4 : 0.55, last ? 1.2 : -0.6);
                        c.level.sendParticles(ParticleTypes.SWEEP_ATTACK, feet.x, feet.y + 1.0, feet.z, 6, 2.0, 0.8, 2.0, 0.0);
                    }
                }
                if (t == Beats.TATSU_FIRST + Beats.TATSU_PULSES * Beats.TATSU_EVERY) {
                    Carve.slab(c.level, feet.add(0, 1.0, 0), feet.add(0, 1.1, 0), 5.0, new Vec3(0, 1, 0), 0.6, 0.9F, Carve.budget());
                }
            }
            case POUND_HO -> projectile(c, t, Beats.POUND_FIRE, Beats.POUND_FLIGHT, Beats.POUND_SPEED, 1.8, 12.0F, 1.1, 2.5F);
            case SHISHI_SONSON -> {
                if (t == Beats.SHISHI_VANISH) {
                    move(c, c.exit);
                }
                if (t == Beats.SHISHI_CLICK) {
                    strike(c, chest, c.exit.add(0, 1.1, 0), 2.0, dmg(c, 32.0F), 0.4, 0.8, e ->
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3)));
                    Carve.slab(c.level, chest, c.exit.add(0, 1.1, 0), 1.5, new Vec3(0, 1, 0), 0.5, 3.0F, Carve.budget());
                    sweep(c, c.point, 5);
                }
            }
            case HYAKUHACHI -> projectile(c, t, Beats.HYAKU_FIRE, Beats.HYAKU_FLIGHT, Beats.HYAKU_SPEED, 3.4, 28.0F, 1.6, 6.0F);
            case KOKUJO -> {
                if (t >= Beats.KOKUJO_FORM && t <= Beats.KOKUJO_LAST && (t - Beats.KOKUJO_FORM) % Beats.KOKUJO_EVERY == 0) {
                    twister(c, c.point, 9.0, 12.0, dmg(c, 5.0F), 0.0, -0.9);
                    if ((t - Beats.KOKUJO_FORM) % 12 == 0) {
                        Carve.slab(c.level, c.point.add(0, 0.6, 0), c.point.add(0, 7.0, 0), 4.5, new Vec3(1, 0, 0), 9.0, 2.0F, Carve.budget() / 3);
                    }
                }
                if (t == Beats.KOKUJO_BURST) {
                    twister(c, c.point, 10.0, 14.0, dmg(c, 22.0F), 2.2, 1.6);
                    c.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.point.x, c.point.y + 3, c.point.z, 1, 0, 0, 0, 0);
                }
            }
            case SANZEN_SEKAI -> {
                if (t == Beats.SANZEN_DASH) {
                    move(c, c.exit);
                }
                if (t == Beats.SANZEN_HIT) {
                    strike(c, chest, c.exit.add(0, 1.1, 0), 2.8, dmg(c, 46.0F), 1.2, 1.0, null);
                    Carve.slab(c.level, chest, c.exit.add(0, 1.1, 0), 2.5, new Vec3(0, 1, 0), 0.6, 4.0F, Carve.budget());
                    sweep(c, c.point, 8);
                }
            }
            case ASURA -> {
                if (t == Beats.ASURA_MANIFEST) {
                    fear(c, 18.0, 30.0F, 80);
                }
                if (t == Beats.ASURA_DASH) {
                    move(c, c.exit);
                }
                if (t == Beats.ASURA_HIT) {
                    strike(c, chest, c.exit.add(0, 1.1, 0), 3.4, dmg(c, 72.0F), 1.8, 1.4, null);
                    Vec3 side = new Vec3(-c.flat.z, 0, c.flat.x);
                    for (int i = -1; i <= 1; i++) {
                        Vec3 off = side.scale(i * 1.4).add(0, i * 0.7, 0);
                        Carve.slab(c.level, chest.add(off), c.exit.add(0, 1.1, 0).add(off).add(c.flat.scale(6)), 3.0, new Vec3(0, 1, 0), 0.5, 6.0F, Carve.budget() / 3);
                    }
                    sweep(c, c.point, 12);
                }
            }
            case RENGOKU -> {
                if (t == Beats.RENGOKU_CONQUER) {
                    fear(c, 22.0, 40.0F, 120);
                }
                if (t == Beats.RENGOKU_DASH) {
                    move(c, c.exit);
                }
                if (t == Beats.RENGOKU_HIT) {
                    strike(c, chest, c.exit.add(0, 1.1, 0), 3.2, dmg(c, 64.0F), 1.5, 1.2, e -> e.setSecondsOnFire(8));
                    Vec3 side = new Vec3(-c.flat.z, 0, c.flat.x);
                    Vec3 mid = c.point;
                    Carve.slab(c.level, mid.add(side.scale(-5)).add(0, 4, 0), mid.add(side.scale(5)).add(0, -4, 0), 4.0, c.flat, 0.6, 8.0F, Carve.budget() / 2);
                    Carve.slab(c.level, mid.add(side.scale(5)).add(0, 4, 0), mid.add(side.scale(-5)).add(0, -4, 0), 4.0, c.flat, 0.6, 8.0F, Carve.budget() / 2);
                    sweep(c, c.point, 12);
                }
            }
            case DRAGON -> {
                if (t == Beats.DRAGON_CONQUER) {
                    fear(c, 28.0, 60.0F, 160);
                }
                if (t == Beats.DRAGON_LEAP) {
                    move(c, c.exit);
                }
                if (t == Beats.DRAGON_HIT) {
                    strike(c, chest, c.point.add(c.flat.scale(4)), 5.5, dmg(c, 130.0F), 2.4, 2.2, e -> e.setSecondsOnFire(5));
                    Vec3 side = new Vec3(-c.flat.z, 0, c.flat.x);
                    Carve.slab(c.level, c.point.add(c.flat.scale(-10)), c.point.add(c.flat.scale(14)), 5.0, new Vec3(0, 1, 0), 0.7, 10.0F, Carve.budget() / 2);
                    Carve.slab(c.level, c.point.add(0, 8, 0), c.point.add(0, -6, 0), 6.0, side, 0.7, 10.0F, Carve.budget() / 2);
                    c.player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
                    sweep(c, c.point, 16);
                }
            }
        }
    }

    /** A flying slash: a blade of air that leaves the swords at {@code fire} and flies on for {@code flight} ticks. */
    private static void projectile(Cast c, int t, int fire, int flight, double speed, double radius, float base, double knock, float hardest) {
        if (t < fire || t > fire + flight) {
            return;
        }
        Vec3 from = c.origin.add(c.dir.scale(0.8)).add(0, -0.25, 0);
        Vec3 a = from.add(c.dir.scale(speed * Math.max(0, t - fire - 1)));
        Vec3 b = from.add(c.dir.scale(speed * (t - fire)));
        strike(c, a, b, radius, dmg(c, base), knock, 0.25, null);
        Vec3 side = new Vec3(-c.flat.z, 0, c.flat.x);
        Vec3 normal = c.dir.cross(side);
        Carve.slab(c.level, a, b, radius, normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : normal, 0.55, hardest, Carve.budget());
    }

    private static void strike(Cast c, Vec3 a, Vec3 b, double radius, float damage, double knock, double lift,
                               java.util.function.Consumer<LivingEntity> extra) {
        ServerPlayer p = c.player;
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(ab.lengthSqr(), 1.0E-6);
        AABB box = new AABB(a, b).inflate(radius + 2.0);
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
            if (c.struck.contains(e.getId()) && c.move != Move.TATSU_MAKI && c.move != Move.KOKUJO) {
                continue;
            }
            Vec3 center = e.getBoundingBox().getCenter();
            double k = Mth.clamp(center.subtract(a).dot(ab) / len2, 0.0, 1.0);
            Vec3 nearest = a.add(ab.scale(k));
            if (center.distanceTo(nearest) > radius + e.getBbWidth() * 0.5) {
                continue;
            }
            if (e.isAlliedTo(p)) {
                continue;
            }
            c.struck.add(e.getId());
            e.invulnerableTime = 0;
            if (e.hurt(p.damageSources().playerAttack(p), damage)) {
                Vec3 push = center.subtract(nearest);
                Vec3 out = push.lengthSqr() < 1.0E-3 ? c.flat : new Vec3(push.x, 0, push.z).normalize();
                Vec3 dirPush = c.flat.scale(0.6).add(out.scale(0.4)).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(dirPush.x * knock, lift, dirPush.z * knock));
                e.hurtMarked = true;
                if (extra != null) {
                    extra.accept(e);
                }
                c.level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 18, 0.3, 0.4, 0.3, 0.6);
            }
        }
    }

    /** A spinning column: everything in it is dragged round and inward, then lifted (lift < 0 = pulled down, held). */
    private static void twister(Cast c, Vec3 base, double radius, double height, float damage, double fling, double lift) {
        ServerPlayer p = c.player;
        AABB box = new AABB(base.x - radius, base.y - 1, base.z - radius, base.x + radius, base.y + height, base.z + radius);
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(p))) {
            Vec3 rel = e.position().subtract(base);
            Vec3 flatRel = new Vec3(rel.x, 0, rel.z);
            double d = flatRel.length();
            if (d > radius) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), damage);
            Vec3 in = d < 1.0E-3 ? Vec3.ZERO : flatRel.scale(-1.0 / d);
            Vec3 round = new Vec3(-in.z, 0, in.x);
            Vec3 v;
            if (fling > 0.0) {
                v = in.scale(-fling).add(round.scale(0.4)).add(0, Math.max(lift, 0.5), 0);
            } else {
                v = round.scale(0.75).add(in.scale(0.35)).add(0, lift > 0 ? lift : 0.55 + 0.1 * (height - rel.y) / height, 0);
            }
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
        }
    }

    /** Conqueror's haki: the weak are overwhelmed and drop where they stand. */
    private static void fear(Cast c, double radius, float maxHealth, int ticks) {
        ServerPlayer p = c.player;
        AABB box = p.getBoundingBox().inflate(radius);
        for (Mob m : c.level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.distanceTo(p) <= radius)) {
            if (m.getMaxHealth() <= maxHealth) {
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false));
                m.setTarget(null);
                c.level.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.1F, 0.85F), 1.6F),
                        m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 10, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    private static void sweep(Cast c, Vec3 at, int count) {
        c.level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, count, 1.2, 0.6, 1.2, 0.0);
    }

    private static void dust(Cast c, Vec3 a, Vec3 b, int rgb, int count) {
        DustParticleOptions o = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.2F);
        for (int i = 0; i < count; i++) {
            Vec3 at = a.lerp(b, i / (double) count);
            c.level.sendParticles(o, at.x, at.y, at.z, 1, 0.1, 0.3, 0.1, 0.0);
        }
    }

    public static void forget(ServerPlayer p) {
        ACTIVE.removeIf(c -> c.player == p);
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
    }
}
