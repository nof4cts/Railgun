package com.firestar.sage.server;

import com.firestar.sage.SageConfig;
import com.firestar.sage.SageMod;
import com.firestar.sage.common.SBeats;
import com.firestar.sage.common.SMove;
import com.firestar.sage.net.SageNet;
import com.firestar.styles.Style;
import com.firestar.styles.Styles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The rites on the server: who may cast what, then each one's script run tick by tick (damage, motion, the land).
 * The clients get one message when a rite begins and play everything else from the same timings.
 */
public final class Rites {
    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private Rites() {}

    static final class Cast {
        final SMove move;
        final ServerPlayer p;
        final ServerLevel level;
        final long start;
        final Vec3 origin, dir, flat;
        Vec3 point;
        LivingEntity target;
        final long seed;
        final int step;
        final List<Vec3> orbs = new ArrayList<>();
        final List<Vec3> orbVel = new ArrayList<>();
        final List<Boolean> orbLive = new ArrayList<>();

        Cast(SMove move, ServerPlayer p, Vec3 point, LivingEntity target, long seed, int step) {
            this.move = move;
            this.p = p;
            this.level = p.serverLevel();
            this.start = level.getGameTime();
            this.origin = p.getEyePosition();
            this.dir = p.getLookAngle();
            Vec3 f = new Vec3(dir.x, 0, dir.z);
            this.flat = f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
            this.point = point;
            this.target = target;
            this.seed = seed;
            this.step = step;
        }

        int t() {
            return (int) (level.getGameTime() - start);
        }

        Vec3 right() {
            return new Vec3(-flat.z, 0, flat.x);
        }
    }

    // ------------------------------------------------------------------------------------------------ asking

    public static boolean busy(Player p) {
        for (Cast c : ACTIVE) {
            if (c.p == p && c.move != SMove.STAFF && c.t() < c.move.length) {
                return true;
            }
        }
        return false;
    }

    public static boolean inCinematic(Player p) {
        for (Cast c : ACTIVE) {
            if (c.p == p && c.move.cinematic && c.t() < c.move.length) {
                return true;
            }
        }
        return false;
    }

    static int cooldown(SMove m) {
        return (int) Math.round(m.cooldown * SageConfig.SERVER.cooldowns.get());
    }

    public static void ask(ServerPlayer p, SMove m) {
        if (!p.isAlive() || p.isSpectator() || !Styles.is(p, Style.SAGE)) {
            return;
        }
        int form = Sage.form(p);
        if (m == SMove.MANTLE && form == Sage.ASCENDED) {
            m = SMove.UNMANTLE;
        }
        if (m == SMove.UNMANTLE && form == Sage.MORTAL) {
            return;
        }
        if (m != SMove.STAFF && m != SMove.MANTLE && m != SMove.UNMANTLE && form != Sage.ASCENDED) {
            deny(p, "Take up the Mantle first (G).");
            return;
        }
        if (busy(p)) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[SMove.values().length]);
        if (now < ready[m.ordinal()] && !p.isCreative()) {
            if (m != SMove.STAFF) {
                deny(p, m.title + " returns in " + Mth.ceil((ready[m.ordinal()] - now) / 20.0F) + "s");
            }
            return;
        }
        LivingEntity target = null;
        Vec3 point = p.getEyePosition().add(p.getLookAngle().scale(4));
        int step = 0;
        switch (m) {
            case STAFF -> step = Sage.nextStaff(p);
            case ORBS -> {
                point = ray(p, SBeats.ORBS_RANGE);
                target = aim(p, SBeats.ORBS_RANGE, 4.0);
                if (target != null) {
                    point = target.getBoundingBox().getCenter();
                }
            }
            case PULL -> {
                target = aim(p, SBeats.PULL_RANGE, 2.5);
                if (target == null) {
                    deny(p, "Nothing in reach to pull.");
                    return;
                }
                point = target.position();
            }
            case MIRROR -> {
                target = aim(p, SBeats.MIRROR_RANGE, 3.0);
                point = target != null ? target.position() : ground(p.serverLevel(), ray(p, 14.0));
            }
            case STARFALL -> {
                target = aim(p, SBeats.STAR_RANGE, 6.0);
                point = ground(p.serverLevel(), target != null ? target.position() : ray(p, SBeats.STAR_RANGE));
            }
            case THORNWOOD, COLOSSUS -> point = ground(p.serverLevel(), p.position());
            case ECLIPSE, MANTLE, UNMANTLE -> point = p.position();
            default -> {
            }
        }
        Cast c = new Cast(m, p, point, target, p.getRandom().nextLong(), step);
        ACTIVE.add(c);
        ready[m.ordinal()] = now + cooldown(m);
        if (m == SMove.MANTLE) {
            ready[SMove.UNMANTLE.ordinal()] = now + m.length + 20;
        }
        syncReady(p);
        SageNet.around(p, new SageNet.Rite(m.ordinal(), p.getId(), now, c.origin, c.dir, c.point, target == null ? -1 : target.getId(), c.seed,
                step));
    }

    private static void deny(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GOLD), true);
    }

    public static void syncReady(ServerPlayer p) {
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[SMove.values().length]);
        int[] total = new int[SMove.values().length];
        for (SMove m : SMove.values()) {
            total[m.ordinal()] = cooldown(m);
        }
        SageNet.to(p, new SageNet.Ready(ready.clone(), total));
    }

    // ------------------------------------------------------------------------------------------------ the world

    static Vec3 ray(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : eye.add(p.getLookAngle().scale(range));
    }

    /** The living thing nearest the crosshair within reach (a forgiving cone, so fast targets can be caught). */
    static LivingEntity aim(ServerPlayer p, double range, double slack) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, p.getBoundingBox().expandTowards(look.scale(range)).inflate(2),
                e -> e instanceof LivingEntity && e.isAlive() && e != p, 0.6F);
        if (hit != null && hit.getEntity() instanceof LivingEntity l) {
            return l;
        }
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(look.scale(range)).inflate(slack + 2),
                e -> e != p && e.isAlive())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along < 1 || along > range) {
                continue;
            }
            double off = to.subtract(look.scale(along)).length();
            if (off > slack + along * 0.08) {
                continue;
            }
            double score = off * 3 + along * 0.1;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    static Vec3 ground(ServerLevel l, Vec3 at) {
        BlockPos b = BlockPos.containing(at);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
        if (y <= l.getMinBuildHeight()) {
            return at;
        }
        // stay under overhangs: the first solid surface at or below the point if there is one close
        for (int dy = 0; dy < 40; dy++) {
            BlockPos q = b.below(dy);
            if (!l.getBlockState(q).isAir() && l.getBlockState(q.above()).isAir()) {
                return new Vec3(at.x, q.getY() + 1, at.z);
            }
        }
        return new Vec3(at.x, y, at.z);
    }

    static float dmg(float base) {
        return base * SageConfig.SERVER.damage.get().floatValue();
    }

    static void hit(Cast c, Entity e, float amount, Vec3 push) {
        if (e == c.p || !e.isAlive()) {
            return;
        }
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
            return;
        }
        if (e instanceof LivingEntity l) {
            l.invulnerableTime = 0;
            l.hurt(c.p.damageSources().playerAttack(c.p), dmg(amount));
        }
        if (push != null) {
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
    }

    static List<LivingEntity> around(Cast c, Vec3 at, double r) {
        return c.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> e != c.p && e.isAlive()
                && e.getBoundingBox().getCenter().distanceTo(at) <= r + e.getBbWidth() * 0.5);
    }

    static void sound(Cast c, Vec3 at, SoundEvent s, float vol, float pitch) {
        c.level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static Vec3 away(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 d = to.subtract(from).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? fallback : d.normalize();
    }

    private static boolean terrain() {
        return SageConfig.SERVER.terrain.get();
    }

    /** A sphere erased (orb impacts): everything but bedrock. */
    static void erase(ServerLevel l, Vec3 c, double r) {
        if (!terrain()) {
            return;
        }
        Carver.Batch b = Carver.batch(l);
        int R = (int) Math.ceil(r);
        BlockPos o = BlockPos.containing(c);
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        b.erase(o.offset(dx, dy, dz));
                    }
                }
            }
        }
        b.go();
    }

    /** A star's crater: a bowl of air, its floor and rim fused into magma, blackstone and gilded stone, fires about. */
    static void crater(ServerLevel l, Vec3 c, int r, RandomSource rand) {
        if (!terrain()) {
            return;
        }
        Carver.Batch b = Carver.batch(l);
        BlockPos o = BlockPos.containing(c);
        List<int[]> cells = new ArrayList<>();
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    cells.add(new int[]{dx, dy, dz, dx * dx + dy * dy + dz * dz});
                }
            }
        }
        cells.sort((a, q) -> Integer.compare(a[3], q[3]));
        for (int[] k : cells) {
            double d = Math.sqrt(k[3]);
            // the bowl is flattened: deep in the middle, shallow at the edge
            double bowl = Math.sqrt(k[0] * k[0] + k[2] * k[2]);
            double depth = r * 0.55 * Math.max(0, 1 - (bowl / r) * (bowl / r));
            BlockPos q = o.offset(k[0], k[1], k[2]);
            if (bowl <= r && k[1] >= -depth) {
                b.erase(q);
            } else if (bowl <= r + 2 && k[1] >= -depth - 2 && k[1] < -depth + 0.5) {
                float pick = rand.nextFloat();
                BlockState lining = pick < 0.35F ? Blocks.MAGMA_BLOCK.defaultBlockState() : pick < 0.7F ? Blocks.BLACKSTONE.defaultBlockState()
                        : pick < 0.9F ? Blocks.BASALT.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState();
                b.set(q, (lv, s) -> s.isAir() ? null : lining);
            } else if (d > r && d <= r + 3 && k[1] >= 0 && k[1] <= 1 && rand.nextFloat() < 0.18F) {
                b.set(q, (lv, s) -> s.isAir() && !lv.getBlockState(q.below()).isAir() ? Blocks.FIRE.defaultBlockState() : null);
            }
        }
        b.go();
    }

    /** The Colossus's cut: a straight trench, its walls glassed into blackstone. */
    static void trench(ServerLevel l, Vec3 from, Vec3 flat, double len, double half, int depth) {
        if (!terrain()) {
            return;
        }
        Carver.Batch b = Carver.batch(l);
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        for (double a = 3; a < len; a += 0.7) {
            Vec3 c = from.add(flat.scale(a));
            for (double w = -half - 1; w <= half + 1; w += 0.7) {
                Vec3 q = c.add(side.scale(w));
                BlockPos top = BlockPos.containing(q.x, from.y + 6, q.z);
                boolean wall = Math.abs(w) > half;
                for (int dy = 0; dy < depth + 8; dy++) {
                    BlockPos p = top.below(dy);
                    if (!wall) {
                        if (dy < depth + 6) {
                            b.erase(p);
                        }
                    } else if (dy > 5 && dy < depth + 7) {
                        b.set(p, (lv, s) -> s.isAir() ? null : Blocks.BLACKSTONE.defaultBlockState());
                    }
                }
            }
        }
        b.go();
    }

    /** The eclipse's seal: the ground in reach is etched in rings of gilded and plain blackstone. */
    static void seal(ServerLevel l, Vec3 c, double r) {
        if (!terrain()) {
            return;
        }
        Carver.Batch b = Carver.batch(l);
        int R = (int) r;
        BlockPos o = BlockPos.containing(c);
        List<int[]> cols = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= R * R) {
                    cols.add(new int[]{dx, dz, d2});
                }
            }
        }
        cols.sort((a, q) -> Integer.compare(a[2], q[2]));
        for (int[] k : cols) {
            double d = Math.sqrt(k[2]);
            double ring = d % 9.0;
            if (ring > 1.3) {
                continue;
            }
            int x = o.getX() + k[0], z = o.getZ() + k[1];
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockState etch = ring < 0.6 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            b.set(new BlockPos(x, y, z), (lv, s) -> s.isAir() || !s.getFluidState().isEmpty() ? null : etch);
        }
        b.go();
    }

    // ------------------------------------------------------------------------------------------------ running

    public static void tick(MinecraftServer server) {
        Iterator<Cast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            if (!c.p.isAlive() || c.p.isRemoved() || c.p.level() != c.level) {
                it.remove();
                continue;
            }
            int t = c.t();
            try {
                run(c, t);
            } catch (RuntimeException e) {
                SageMod.LOGGER.error("[sage] rite {} failed at tick {}", c.move, t, e);
                it.remove();
                continue;
            }
            if (t >= c.move.length + 40) {
                it.remove();
            }
        }
        Carver.tick(SageConfig.SERVER.blocksPerTick.get());
    }

    private static void hold(Cast c) {
        // the sage stands still through the long rites (floating if he was in the air)
        c.p.setDeltaMovement(Vec3.ZERO);
        c.p.hurtMarked = true;
        c.p.fallDistance = 0;
    }

    private static void run(Cast c, int t) {
        switch (c.move) {
            case STAFF -> staff(c, t);
            case ORBS -> orbs(c, t);
            case PULL -> pull(c, t);
            case MIRROR -> mirror(c, t);
            case STARFALL -> starfall(c, t);
            case THORNWOOD -> thornwood(c, t);
            case COLOSSUS -> colossus(c, t);
            case ECLIPSE -> eclipse(c, t);
            case MANTLE -> mantle(c, t);
            case UNMANTLE -> {
                if (t == 4) {
                    Sage.setForm(c.p, Sage.MORTAL);
                    sound(c, c.p.position(), SageMod.MANTLE_RELEASE.get(), 1.2F, 1.0F);
                }
            }
        }
    }

    private static void staff(Cast c, int t) {
        if (t != SBeats.STAFF_HIT) {
            return;
        }
        Vec3 eye = c.p.getEyePosition();
        Vec3 look = c.p.getLookAngle();
        double reach = c.step == 0 ? 4.6 : 3.8;
        float base = c.step == 2 ? 12.0F : c.step == 1 ? 8.0F : 7.0F;
        if (Sage.form(c.p) != Sage.ASCENDED) {
            base *= 0.5F;
        }
        boolean any = false;
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, c.p.getBoundingBox().inflate(reach), e -> e != c.p && e.isAlive())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            double off = to.subtract(look.scale(along)).length();
            boolean in = c.step == 1 ? along > -0.5 && along < reach && off < 2.6 : along > 0 && along < reach && off < 1.6 + e.getBbWidth() * 0.5;
            if (in) {
                Vec3 push = c.step == 2 ? new Vec3(0, -0.4, 0).add(c.flat.scale(0.6)) : away(c.p.position(), e.position(), c.flat).scale(c.step == 1 ? 0.9 : 0.6)
                        .add(0, 0.25, 0);
                hit(c, e, base, push);
                any = true;
            }
        }
        sound(c, eye, any ? SageMod.STAFF_HIT.get() : SageMod.STAFF_SWING.get(), 1.0F, 0.9F + c.step * 0.08F);
    }

    /** The orb's home behind the sage's back, on the wheel. */
    static Vec3 wheel(ServerPlayer p, int i) {
        double a = Math.PI * 2 * i / SBeats.ORBS_COUNT + p.level().getGameTime() * 0.03;
        Vec3 f = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        f = f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return p.position().add(0, 1.35, 0).subtract(f.scale(0.55)).add(r.scale(Math.cos(a) * 1.15)).add(0, Math.sin(a) * 1.15, 0);
    }

    private static void orbs(Cast c, int t) {
        int k = t - SBeats.ORBS_FIRST;
        if (k >= 0 && k % SBeats.ORBS_EVERY == 0 && k / SBeats.ORBS_EVERY < SBeats.ORBS_COUNT) {
            int i = k / SBeats.ORBS_EVERY;
            Vec3 from = wheel(c.p, i);
            Vec3 aimAt = c.target != null && c.target.isAlive() ? c.target.getBoundingBox().getCenter() : c.point;
            c.orbs.add(from);
            c.orbVel.add(aimAt.subtract(from).normalize().scale(SBeats.ORBS_SPEED));
            c.orbLive.add(true);
            sound(c, from, SageMod.ORB_FIRE.get(), 1.0F, 0.85F + i * 0.04F);
        }
        for (int i = 0; i < c.orbs.size(); i++) {
            if (!c.orbLive.get(i)) {
                continue;
            }
            Vec3 pos = c.orbs.get(i);
            Vec3 vel = c.orbVel.get(i);
            // home in on the chosen target, gently
            if (c.target != null && c.target.isAlive()) {
                Vec3 want = c.target.getBoundingBox().getCenter().subtract(pos).normalize().scale(SBeats.ORBS_SPEED);
                vel = vel.lerp(want, 0.18).normalize().scale(SBeats.ORBS_SPEED);
                c.orbVel.set(i, vel);
            }
            Vec3 next = pos.add(vel);
            boolean done = false;
            Vec3 at = next;
            BlockHitResult bh = c.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.p));
            if (bh.getType() == HitResult.Type.BLOCK) {
                at = bh.getLocation();
                done = true;
            }
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, at).inflate(0.8), e -> e != c.p && e.isAlive())) {
                at = e.getBoundingBox().getCenter();
                done = true;
                break;
            }
            if (pos.distanceTo(c.origin) > SBeats.ORBS_RANGE + 10) {
                done = true;
            }
            c.orbs.set(i, next);
            if (done) {
                c.orbLive.set(i, false);
                c.orbs.set(i, at);
                for (LivingEntity e : around(c, at, SBeats.ORBS_ERASE + 0.8)) {
                    hit(c, e, 9.0F, away(c.p.position(), e.position(), c.flat).scale(0.5).add(0, 0.25, 0));
                }
                erase(c.level, at, SBeats.ORBS_ERASE);
                sound(c, at, SageMod.ORB_ERASE.get(), 1.6F, 0.8F + c.level.random.nextFloat() * 0.3F);
            }
        }
    }

    private static void pull(Cast c, int t) {
        LivingEntity e = c.target;
        if (e == null || !e.isAlive()) {
            return;
        }
        Vec3 dest = c.p.position().add(c.flat.scale(2.6)).add(0, 0.2, 0);
        if (t == SBeats.PULL_BIND) {
            sound(c, e.position(), SageMod.PULL_BIND.get(), 1.4F, 1.0F);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6, false, false));
        }
        if (t > SBeats.PULL_BIND && t <= SBeats.PULL_ARRIVE) {
            float k = (t - SBeats.PULL_BIND) / (float) (SBeats.PULL_ARRIVE - SBeats.PULL_BIND);
            float ease = k * k;
            Vec3 now = e.position();
            Vec3 want = now.lerp(dest, Math.min(1.0, 0.25 + ease));
            e.setDeltaMovement(want.subtract(now));
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
        if (t == SBeats.PULL_BURST) {
            hit(c, e, 18.0F, c.flat.scale(2.2).add(0, 1.3, 0));
            for (LivingEntity o : around(c, dest, 3.5)) {
                if (o != e) {
                    hit(c, o, 8.0F, away(c.p.position(), o.position(), c.flat).scale(1.4).add(0, 0.6, 0));
                }
            }
            sound(c, dest, SageMod.PULL_BURST.get(), 2.0F, 1.0F);
        }
    }

    private static void mirror(Cast c, int t) {
        if (t == SBeats.MIRROR_VANISH) {
            c.p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false));
            sound(c, c.p.position(), SageMod.MIRROR_VANISH.get(), 1.2F, 1.0F);
        }
        if (t == SBeats.MIRROR_STRIKE) {
            Vec3 at;
            Vec3 face;
            if (c.target != null && c.target.isAlive()) {
                Vec3 tf = c.target.getLookAngle().multiply(1, 0, 1);
                tf = tf.lengthSqr() < 1e-4 ? c.flat : tf.normalize();
                at = c.target.position().subtract(tf.scale(c.target.getBbWidth() * 0.5 + 1.2));
                face = tf;
                hit(c, c.target, 16.0F, tf.scale(1.4).add(0, 0.5, 0));
            } else {
                at = c.point;
                face = c.flat;
            }
            at = safe(c.level, at, c.p.position());
            float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
            c.p.teleportTo(c.level, at.x, at.y, at.z, yaw, c.p.getXRot());
            c.p.removeEffect(MobEffects.INVISIBILITY);
            sound(c, at, SageMod.MIRROR_STRIKE.get(), 1.4F, 1.0F);
        }
    }

    static Vec3 safe(ServerLevel l, Vec3 at, Vec3 fallback) {
        for (int dy = 0; dy < 4; dy++) {
            BlockPos b = BlockPos.containing(at.x, at.y + dy, at.z);
            if (l.getBlockState(b).getCollisionShape(l, b).isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()) {
                return new Vec3(at.x, b.getY(), at.z);
            }
        }
        return fallback;
    }

    /** Where a star falls from: high above and behind the point, along the sage's line of sight. */
    public static Vec3 starFrom(Vec3 point, Vec3 flat, int which) {
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        return point.add(flat.scale(-46 + which * 20)).add(side.scale(which == 0 ? 18 : -26)).add(0, SBeats.STAR_HEIGHT, 0);
    }

    public static Vec3 starPoint(Vec3 point, Vec3 flat, int which) {
        return which == 0 ? point : point.add(flat.scale(16)).add(new Vec3(-flat.z, 0, flat.x).scale(-11));
    }

    private static void starfall(Cast c, int t) {
        if (t < SBeats.STAR_IMPACT) {
            hold(c);
        }
        if (t == SBeats.STAR_CALL) {
            sound(c, c.p.position(), SageMod.STAR_CALL.get(), 3.0F, 1.0F);
        }
        if (t == SBeats.STAR_APPEAR) {
            sound(c, c.point, SageMod.STAR_ROAR.get(), 12.0F, 1.0F);
        }
        for (int which = 0; which < 2; which++) {
            int at = which == 0 ? SBeats.STAR_IMPACT : SBeats.STAR_SECOND;
            int r = which == 0 ? SBeats.STAR_CRATER : 8;
            Vec3 p = starPoint(c.point, c.flat, which);
            if (t == at) {
                sound(c, p, SageMod.STAR_IMPACT.get(), 30.0F, which == 0 ? 1.0F : 1.15F);
                for (LivingEntity e : around(c, p, r * 1.7)) {
                    double d = e.position().distanceTo(p);
                    double k = Mth.clamp(1 - d / (r * 1.7), 0, 1);
                    hit(c, e, (float) ((which == 0 ? 30 : 20) + (which == 0 ? 50 : 30) * k), away(p, e.position(), c.flat).scale(1.5 + 3 * k)
                            .add(0, 1.0 + 1.6 * k, 0));
                    e.setSecondsOnFire(10);
                }
                crater(c.level, p, r, c.level.random);
            }
        }
    }

    /** Where the thorn trees rise: a cone ahead, a few dozen trunks, nearest first. */
    public static List<Vec3> thornSpots(Vec3 origin, Vec3 flat, long seed) {
        List<Vec3> out = new ArrayList<>();
        RandomSource r = RandomSource.create(seed);
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        for (int i = 0; i < 34; i++) {
            double a = 3 + SBeats.THORN_LENGTH * Math.sqrt((i + r.nextDouble()) / 34.0);
            double w = (r.nextDouble() * 2 - 1) * (1.5 + a * 0.45);
            out.add(origin.add(flat.scale(a)).add(side.scale(w)));
        }
        out.sort((p, q) -> Double.compare(p.distanceToSqr(origin), q.distanceToSqr(origin)));
        return out;
    }

    /** When tree {@code i} of {@code n} breaks the ground. */
    public static int thornTime(int i, int n) {
        return SBeats.THORN_ERUPT + (int) (SBeats.THORN_SPREAD * (i / (float) Math.max(1, n - 1)));
    }

    private static void thornwood(Cast c, int t) {
        if (t < SBeats.THORN_ERUPT + 6) {
            hold(c);
        }
        if (t == SBeats.THORN_PLANT) {
            sound(c, c.p.position(), SageMod.BELL.get(), 2.0F, 0.75F);
        }
        List<Vec3> spots = thornSpots(c.point, c.flat, c.seed);
        for (int i = 0; i < spots.size(); i++) {
            if (t != thornTime(i, spots.size())) {
                continue;
            }
            Vec3 s = ground(c.level, spots.get(i).add(0, 4, 0));
            if (i % 5 == 0) {
                sound(c, s, SageMod.THORN_ERUPT.get(), 2.6F, 0.85F + (i % 3) * 0.1F);
            }
            for (LivingEntity e : around(c, s.add(0, 1, 0), 2.6)) {
                hit(c, e, 14.0F, new Vec3(0, 1.45, 0).add(away(s, e.position(), c.flat).scale(0.3)));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, true));
            }
            if (terrain() && i % 2 == 0) {
                // the ground splits where the trunks come through
                BlockPos b = BlockPos.containing(s).below();
                Carver.batch(c.level).set(b, (lv, st) -> st.isAir() ? null : Blocks.ROOTED_DIRT.defaultBlockState())
                        .set(b.east(), (lv, st) -> st.isAir() ? null : Blocks.COARSE_DIRT.defaultBlockState()).go();
            }
        }
    }

    private static void colossus(Cast c, int t) {
        if (t < SBeats.COL_CUT + 6) {
            hold(c);
        }
        if (t == SBeats.COL_RISE) {
            sound(c, c.p.position(), SageMod.COLOSSUS_RISE.get(), 6.0F, 1.0F);
        }
        if (t == SBeats.COL_CUT) {
            Vec3 from = c.point;
            sound(c, from.add(c.flat.scale(20)), SageMod.COLOSSUS_CUT.get(), 40.0F, 1.0F);
            Vec3 side = c.right();
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from.add(c.flat.scale(SBeats.COL_LENGTH))).inflate(8, 30, 8),
                    e -> e != c.p && e.isAlive())) {
                Vec3 rel = e.position().subtract(from);
                double along = rel.dot(c.flat);
                double across = Math.abs(rel.dot(side));
                if (along > 1 && along < SBeats.COL_LENGTH && across < 5.5 + e.getBbWidth() * 0.5) {
                    hit(c, e, 70.0F, side.scale(rel.dot(side) >= 0 ? 1.6 : -1.6).add(0, 1.2, 0));
                }
            }
            trench(c.level, from, c.flat, SBeats.COL_LENGTH, 2.5, 7);
        }
    }

    private static void eclipse(Cast c, int t) {
        if (t < SBeats.ECL_RING_END) {
            // he rises and hangs in the dark
            if (t < SBeats.ECL_COVER) {
                c.p.setDeltaMovement(0, 0.18, 0);
            } else {
                c.p.setDeltaMovement(Vec3.ZERO);
            }
            c.p.hurtMarked = true;
            c.p.fallDistance = 0;
        }
        if (t == SBeats.ECL_COVER) {
            sound(c, c.p.position(), SageMod.ECLIPSE_COVER.get(), 20.0F, 1.0F);
        }
        if (t == SBeats.ECL_RING) {
            sound(c, c.p.position(), SageMod.ECLIPSE_RING.get(), 40.0F, 1.0F);
        }
        if (t >= SBeats.ECL_RING && t <= SBeats.ECL_RING_END) {
            double r0 = SBeats.ECL_RADIUS * (t - 1 - SBeats.ECL_RING) / (double) (SBeats.ECL_RING_END - SBeats.ECL_RING);
            double r1 = SBeats.ECL_RADIUS * (t - SBeats.ECL_RING) / (double) (SBeats.ECL_RING_END - SBeats.ECL_RING);
            Vec3 o = c.point;
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r1 + 2, 60, r1 + 2), e -> e != c.p && e.isAlive())) {
                double d = Math.sqrt(e.distanceToSqr(o.x, e.getY(), o.z));
                if (d >= r0 - 1 && d < r1 + 1) {
                    hit(c, e, 45.0F, away(o, e.position(), c.flat).scale(1.2).add(0, 0.9, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 4, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                }
            }
        }
        if (t == SBeats.ECL_RING + 4) {
            seal(c.level, c.point, SBeats.ECL_RADIUS);
        }
    }

    private static void mantle(Cast c, int t) {
        if (t < SBeats.MANTLE_IGNITE) {
            hold(c);
        }
        if (t == SBeats.MANTLE_GATHER) {
            sound(c, c.p.position(), SageMod.MANTLE_GATHER.get(), 2.0F, 1.0F);
        }
        if (t == SBeats.MANTLE_HALO) {
            sound(c, c.p.position(), SageMod.BELL.get(), 3.0F, 1.0F);
        }
        if (t == SBeats.MANTLE_IGNITE) {
            Sage.setForm(c.p, Sage.ASCENDED);
            sound(c, c.p.position(), SageMod.MANTLE_IGNITE.get(), 4.0F, 1.0F);
            for (LivingEntity e : around(c, c.p.position(), 9)) {
                double d = e.distanceTo(c.p);
                hit(c, e, 4.0F, away(c.p.position(), e.position(), c.flat).scale(2.2 - d * 0.15).add(0, 0.6, 0));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ bookkeeping

    public static void cancel(Player p) {
        ACTIVE.removeIf(c -> c.p == p);
    }

    public static void forget(Player p) {
        cancel(p);
        READY.remove(p.getUUID());
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
        Carver.clear();
    }

    /** The build server's self-test: ready every rite again. */
    public static void testReset(ServerPlayer p) {
        READY.remove(p.getUUID());
        ACTIVE.removeIf(c -> c.p == p);
        syncReady(p);
    }
}
