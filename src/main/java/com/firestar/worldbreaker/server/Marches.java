package com.firestar.worldbreaker.server;

import com.firestar.worldbreaker.WorldbreakerMod;
import com.firestar.worldbreaker.common.March;
import com.firestar.worldbreaker.config.WConfig;
import com.firestar.worldbreaker.network.Net;
import com.firestar.worldbreaker.network.S2CMarch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every march under way: the giants walk on the shared arithmetic in {@link March}; here they also do what giants do to
 * a world. Each tick a slab of everything in front of each body is erased, each footfall punches a crater and throws real
 * blocks into the air, and anything caught under a foot or against a shin is crushed.
 */
public final class Marches {
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> READY = new HashMap<>();
    private static int nextId = 1;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private Marches() {}

    static final class Active {
        final int id;
        final UUID caller;
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 dir;
        final long start;
        final March.Giant[] giants;
        final int life;
        final double[] ground;
        final double[] swept;
        final S2CMarch packet;

        Active(int id, ServerPlayer p, Vec3 origin, Vec3 dir, long start, long seed, int count, int life) {
            this.id = id;
            this.caller = p.getUUID();
            this.level = p.serverLevel();
            this.origin = origin;
            this.dir = dir;
            this.start = start;
            this.giants = March.formation(count, seed);
            this.life = life;
            this.ground = new double[count];
            this.swept = new double[count];
            java.util.Arrays.fill(ground, origin.y);
            this.packet = new S2CMarch(id, p.getId(), origin, dir, start, seed, count, life);
        }
    }

    /** A player asks for the march. */
    public static void call(ServerPlayer p) {
        if (WConfig.SERVER.opsOnly.get() && !p.hasPermissions(2)) {
            p.displayClientMessage(Component.literal("Only operators can call the march.").withStyle(ChatFormatting.RED), true);
            return;
        }
        long now = p.serverLevel().getGameTime();
        Long ready = READY.get(p.getUUID());
        if (ready != null && now < ready && !p.isCreative()) {
            p.displayClientMessage(Component.literal("The ground is still settling... " + (ready - now) / 20 + "s").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        for (Active a : ACTIVE) {
            if (a.caller.equals(p.getUUID())) {
                p.displayClientMessage(Component.literal("Your march is still walking.").withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
        begin(p, p.getLookAngle());
    }

    public static void begin(ServerPlayer p, Vec3 look) {
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        long now = p.serverLevel().getGameTime();
        Active a = new Active(nextId++, p, p.position(), dir, now + 1, new Random().nextLong(), WConfig.SERVER.giants.get(), WConfig.SERVER.life.get());
        ACTIVE.add(a);
        READY.put(p.getUUID(), now + WConfig.SERVER.cooldown.get());
        Net.toLevel(a.level, a.packet);
        WorldbreakerMod.LOGGER.info("[march] {} called {} giants at {}", p.getName().getString(), a.giants.length, p.blockPosition());
    }

    /** Send every march in progress to someone who just arrived. */
    public static void sync(ServerPlayer p) {
        for (Active a : ACTIVE) {
            if (a.level == p.serverLevel()) {
                Net.toPlayer(p, a.packet);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        int budget = WConfig.SERVER.destroyBlocks.get() ? WConfig.SERVER.blocksPerTick.get() : 0;
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            long t = a.level.getGameTime() - a.start;
            if (t > a.life) {
                it.remove();
                continue;
            }
            if (t < 0) {
                continue;
            }
            for (March.Giant g : a.giants) {
                budget = step(a, g, (int) t, budget);
            }
        }
    }

    private static int step(Active a, March.Giant g, int t, int budget) {
        ServerLevel level = a.level;
        double presence = March.presence(t, a.life);
        double h = March.HEIGHT * g.scale();
        Vec3 heading = March.heading(a.dir, g);
        Vec3 side = March.side(heading);
        Vec3 c = March.position(a.origin, a.dir, g, t);
        BlockPos col = BlockPos.containing(c);
        if (level.hasChunkAt(col)) {
            double surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            // never climb a wall in one stride, never fall into a hole it just dug
            double target = Math.max(a.origin.y - 24, Math.min(a.origin.y + 30, surface));
            a.ground[g.index()] += (target - a.ground[g.index()]) * 0.06;
        }
        double gy = a.ground[g.index()];
        c = new Vec3(c.x, gy, c.z);
        if (presence < 0.6) {
            a.swept[g.index()] = March.walked(t, g.phase());
            return budget;
        }

        // the body ploughs through: erase a slab in front of it, from the shins to the shoulders
        double now = March.walked(t, g.phase());
        Vec3 start = March.position(a.origin, a.dir, g, 0);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double half = h * 0.2;
        int top = (int) (h * 0.97 * presence);
        while (a.swept[g.index()] < now && budget > 0) {
            double s = a.swept[g.index()];
            Vec3 front = new Vec3(start.x, gy, start.z).subtract(a.dir.scale(s)).add(heading.scale(h * 0.09));
            for (double l = -half; l <= half; l += 0.9) {
                Vec3 p = front.add(side.scale(l));
                int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
                if (!level.hasChunkAt(m.set(x, (int) gy, z))) {
                    continue;
                }
                // narrower at the shins, the full width from the hips up
                int from = Math.abs(l) < h * 0.15 ? 1 : (int) (h * 0.4);
                for (int y = from; y <= top && budget > 0; y++) {
                    m.set(x, (int) Math.floor(gy) + y, z);
                    BlockState st = level.getBlockState(m);
                    if (st.isAir() || st.getDestroySpeed(level, m) < 0) {
                        continue;
                    }
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                    budget--;
                }
            }
            a.swept[g.index()] = s + 1.0;
        }
        if (budget <= 0) {
            a.swept[g.index()] = now;
        }

        // the crush: anything against the legs or under the body
        double dmg = WConfig.SERVER.damage.get();
        AABB body = new AABB(c.x - h * 0.2, gy - 1, c.z - h * 0.2, c.x + h * 0.2, gy + h, c.z + h * 0.2);
        for (Entity e : level.getEntities((Entity) null, body, e -> e instanceof LivingEntity && e.isAlive())) {
            if (safe(a, e)) {
                continue;
            }
            Vec3 rel = e.position().subtract(c);
            if (Math.abs(rel.dot(heading)) > h * 0.12 || Math.abs(rel.dot(side)) > h * 0.2) {
                continue;
            }
            LivingEntity le = (LivingEntity) e;
            le.invulnerableTime = 0;
            le.hurt(level.damageSources().cramming(), (float) (40 * dmg));
            Vec3 shove = rel.dot(side) >= 0 ? side : side.scale(-1);
            le.setDeltaMovement(shove.scale(1.6).add(heading.scale(1.2)).add(0, 0.9, 0));
            le.hurtMarked = true;
        }

        if (March.footfall(t, g) && presence > 0.8) {
            footfall(a, g, t, c, heading, budget > 0);
        }
        return budget;
    }

    private static boolean safe(Active a, Entity e) {
        return WConfig.SERVER.callerSafe.get() && e.getUUID().equals(a.caller) || e instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator());
    }

    /** A foot comes down: a crater, blocks thrown into the air, a shockwave through everything near. */
    private static void footfall(Active a, March.Giant g, int t, Vec3 c, Vec3 heading, boolean destroy) {
        ServerLevel level = a.level;
        double h = March.HEIGHT * g.scale();
        Vec3 foot = March.foot(c, heading, g, March.leftFoot(t, g));
        double r = h * 0.085;
        Random rng = new Random(a.id * 31L + g.index() * 7L + t);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (destroy && WConfig.SERVER.destroyBlocks.get()) {
            int flung = 0;
            int maxFling = WConfig.SERVER.debris.get();
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    for (int dy = -3; dy <= 5; dy++) {
                        double e = (dx * dx + dz * dz) / (r * r) + (dy < 0 ? dy * dy / 9.0 : dy * dy / 30.0);
                        if (e > 1.0) {
                            continue;
                        }
                        m.set(Math.floor(foot.x) + dx, Math.floor(foot.y) + dy, Math.floor(foot.z) + dz);
                        if (!level.hasChunkAt(m)) {
                            continue;
                        }
                        BlockState st = level.getBlockState(m);
                        if (st.isAir() || st.getDestroySpeed(level, m) < 0 || !st.getFluidState().isEmpty()) {
                            continue;
                        }
                        if (flung < maxFling && dy >= -1 && e > 0.55 && rng.nextInt(3) == 0) {
                            FallingBlockEntity fb = FallingBlockEntity.fall(level, m.immutable(), st);
                            Vec3 out = new Vec3(dx, 0, dz).normalize();
                            fb.setDeltaMovement(out.scale(0.5 + rng.nextDouble() * 0.9).add(0, 0.9 + rng.nextDouble() * 0.9, 0));
                            fb.dropItem = false;
                            fb.hurtMarked = true;
                            flung++;
                        } else {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                        }
                    }
                }
            }
        }
        double dmg = WConfig.SERVER.damage.get();
        double reach = h * 0.3;
        AABB box = new AABB(foot, foot).inflate(reach, 6, reach);
        for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive())) {
            if (safe(a, e)) {
                continue;
            }
            double d = e.position().distanceTo(foot);
            if (d > reach) {
                continue;
            }
            LivingEntity le = (LivingEntity) e;
            float k = (float) (1 - d / reach);
            le.invulnerableTime = 0;
            le.hurt(d < r ? level.damageSources().cramming() : level.damageSources().explosion(null, null), (float) ((d < r ? 60 : 14 * k) * dmg));
            Vec3 away = e.position().subtract(foot).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? heading : away.normalize();
            le.setDeltaMovement(away.scale(0.6 + 1.2 * k).add(0, 0.5 + 0.9 * k, 0));
            le.hurtMarked = true;
        }
    }

    public static void forget(ServerPlayer p) {
        READY.remove(p.getUUID());
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
    }

    public static int active() {
        return ACTIVE.size();
    }
}
