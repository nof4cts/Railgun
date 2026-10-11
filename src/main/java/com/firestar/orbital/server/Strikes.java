package com.firestar.orbital.server;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.common.Cannon;
import com.firestar.orbital.config.OConfig;
import com.firestar.orbital.network.Net;
import com.firestar.orbital.network.S2CBeam;
import com.firestar.orbital.network.S2CStrike;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every strike under way, and what each does to the world on its timeline: Helios burns a crater where it lands and then
 * cuts a molten trench wherever its caller looks; Antipode exchanges a disc of land for the other world's, its relief
 * mirrored, ring by ring outward from the mark.
 */
public final class Strikes {
    private Strikes() {}

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static int nextId = 1;

    static final class Active {
        final int id;
        final Cannon cannon;
        final UUID caller;
        final int callerId;
        final ServerLevel level;
        final Vec3 target;
        final long start;
        final long seed;
        final float radius;
        final S2CStrike packet;
        // Helios
        Vec3 beam;
        final Set<Long> cut = new HashSet<>();
        final List<long[]> queue = new ArrayList<>();
        // Antipode
        int[][] columns;
        int done;

        Active(int id, Cannon cannon, ServerPlayer p, Vec3 target, long start, long seed, float radius) {
            this.id = id;
            this.cannon = cannon;
            this.caller = p.getUUID();
            this.callerId = p.getId();
            this.level = p.serverLevel();
            this.target = target;
            this.start = start;
            this.seed = seed;
            this.radius = radius;
            this.beam = target;
            this.packet = new S2CStrike(id, cannon.ordinal(), p.getId(), target, start, seed, radius);
        }

        int t() {
            return (int) (level.getGameTime() - start);
        }
    }

    public static int count() {
        return ACTIVE.size();
    }

    /** Where the caller is aiming, or null if nothing is in range. */
    public static Vec3 aim(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(OConfig.SERVER.range.get()));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    /** The caller pressed the button. */
    public static boolean fire(ServerPlayer p, Cannon c) {
        long now = p.serverLevel().getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Cannon.values().length]);
        if (now < ready[c.ordinal()] && !p.isCreative()) {
            p.displayClientMessage(Component.literal(c.code + " " + c.title + " is recharging: " + (ready[c.ordinal()] - now + 19) / 20 + "s")
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        for (Active a : ACTIVE) {
            if (a.caller.equals(p.getUUID())) {
                p.displayClientMessage(Component.literal("Your last strike is still under way.").withStyle(ChatFormatting.GOLD), true);
                return false;
            }
        }
        Vec3 at = aim(p);
        if (at == null) {
            p.displayClientMessage(Component.literal("No target in range.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        begin(p, c, at);
        ready[c.ordinal()] = now + OConfig.SERVER.cooldown.get() * 20L;
        return true;
    }

    public static void begin(ServerPlayer p, Cannon c, Vec3 at) {
        ServerLevel level = p.serverLevel();
        // the mark sits on the ground under the crosshair
        BlockPos col = BlockPos.containing(at.x, at.y, at.z);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
        double y = Math.min(at.y, ground);
        Vec3 target = new Vec3(at.x, y, at.z);
        float radius = c == Cannon.HELIOS ? OConfig.SERVER.heliosRadius.get().floatValue() : OConfig.SERVER.antipodeRadius.get().floatValue();
        Active a = new Active(nextId++, c, p, target, level.getGameTime() + 1, new Random().nextLong(), radius);
        ACTIVE.add(a);
        Net.toLevel(level, a.packet);
        OrbitalMod.LOGGER.info("[strike] {} fired {} at {}", p.getName().getString(), c.title, BlockPos.containing(target));
    }

    public static void sync(ServerPlayer p) {
        for (Active a : ACTIVE) {
            if (a.level == p.serverLevel()) {
                Net.toPlayer(p, a.packet);
            }
        }
    }

    public static void forget(ServerPlayer p) {
        // a strike carries on without its caller: Helios simply stops following anyone's eyes
    }

    public static void clearAll() {
        ACTIVE.clear();
        READY.clear();
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        int budget = OConfig.SERVER.destroyBlocks.get() ? OConfig.SERVER.blocksPerTick.get() : 0;
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            int t = a.t();
            if (t > a.cannon.end + 20 || a.level.getServer() != server) {
                it.remove();
                continue;
            }
            if (t < 0) {
                continue;
            }
            budget = a.cannon == Cannon.HELIOS ? helios(a, t, budget) : antipode(a, t, budget);
        }
    }

    // ------------------------------------------------------------------------------------------------ Helios

    private static int helios(Active a, int t, int budget) {
        Cannon c = a.cannon;
        if (t < c.impact || t > c.end) {
            return budget;
        }
        ServerPlayer caller = a.level.getServer().getPlayerList().getPlayer(a.caller);
        if (t == c.impact) {
            // the crater where the lance lands: twice its own width
            queueDisc(a, a.target, a.radius * 2.0F);
            hurt(a, a.target, a.radius * 3.0F, OConfig.SERVER.heliosDamage.get().floatValue() * 1.5F, true);
        }
        if (t > c.impact && t <= c.hold && caller != null && caller.isAlive() && caller.level() == a.level) {
            // the lance follows the caller's eyes, as fast as a mountain of mirrors can turn
            Vec3 want = aim(caller);
            if (want != null) {
                Vec3 d = new Vec3(want.x - a.beam.x, 0, want.z - a.beam.z);
                double max = OConfig.SERVER.heliosSpeed.get();
                if (d.length() > max) {
                    d = d.normalize().scale(max);
                }
                a.beam = a.beam.add(d);
            }
        }
        if (t <= c.hold + 6) {
            int ground = a.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(a.beam.x), Mth.floor(a.beam.z));
            a.beam = new Vec3(a.beam.x, ground, a.beam.z);
            queueDisc(a, a.beam, a.radius);
            if (t % 2 == 0) {
                hurt(a, a.beam, a.radius + 1.5F, OConfig.SERVER.heliosDamage.get().floatValue(), false);
            }
            Net.toLevel(a.level, new S2CBeam(a.id, a.beam));
        }
        // cut what is queued
        Iterator<long[]> q = a.queue.iterator();
        while (q.hasNext() && budget > 0) {
            long[] col = q.next();
            budget -= cutColumn(a, (int) col[0], (int) col[1], (int) col[2]);
            q.remove();
        }
        return budget;
    }

    /** Queue every column of a disc that has not been cut yet; mode 0 = cut, 1 = scorched wall. */
    private static void queueDisc(Active a, Vec3 c, float r) {
        int R = Mth.ceil(r + 1.6F);
        int cx = Mth.floor(c.x), cz = Mth.floor(c.z);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.6) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                long key = BlockPos.asLong(x, 0, z);
                boolean core = d <= r;
                long wallKey = key ^ 0x5DEECE66DL;
                if (core) {
                    if (a.cut.add(key)) {
                        a.queue.add(new long[]{x, z, 0});
                    }
                } else if (!a.cut.contains(key) && a.cut.add(wallKey)) {
                    a.queue.add(new long[]{x, z, 1});
                }
            }
        }
    }

    private static int cutColumn(Active a, int x, int z, int mode) {
        ServerLevel l = a.level;
        int top = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int ground = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floor = Math.max(l.getMinBuildHeight() + 1, ground - OConfig.SERVER.heliosDepth.get());
        Random r = new Random(a.seed ^ BlockPos.asLong(x, 0, z));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int used = 0;
        if (mode == 0) {
            for (int y = top; y > floor; y--) {
                m.set(x, y, z);
                if (!l.getBlockState(m).isAir()) {
                    l.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                    used++;
                }
            }
            m.set(x, floor, z);
            l.setBlock(m, r.nextInt(7) == 0 ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), FLAGS);
            return used + 1;
        }
        // the trench wall: the stone it passed close to, scorched and fused
        for (int y = ground - 1; y > floor; y--) {
            m.set(x, y, z);
            BlockState s = l.getBlockState(m);
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                continue;
            }
            int k = r.nextInt(10);
            l.setBlock(m, (k < 5 ? Blocks.BASALT : k < 8 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK).defaultBlockState(), FLAGS);
            used++;
        }
        // and anything standing at the brink burns away
        for (int y = top; y >= ground; y--) {
            m.set(x, y, z);
            if (!l.getBlockState(m).isAir()) {
                l.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                used++;
            }
        }
        return used;
    }

    // ------------------------------------------------------------------------------------------------ Antipode

    private static int antipode(Active a, int t, int budget) {
        Cannon c = a.cannon;
        if (t == c.impact) {
            hurt(a, a.target, a.radius, OConfig.SERVER.antipodeDamage.get().floatValue(), true);
            a.columns = columns(a.radius);
        }
        if (a.columns == null || t < c.impact || a.done >= a.columns.length) {
            return budget;
        }
        // the exchange spreads from the mark to the rim over the hold
        float k = Mth.clamp((t - c.impact + 1) / (float) (c.hold - c.impact), 0, 1);
        int want = (int) Math.ceil(a.columns.length * k);
        if (t >= c.hold) {
            want = a.columns.length;
        }
        while (a.done < want && budget > 0) {
            int[] col = a.columns[a.done++];
            budget -= exchange(a, col[0], col[1]);
        }
        if (t % 5 == 0 && t < c.hold) {
            hurt(a, a.target, a.radius * (0.2F + 0.8F * k), OConfig.SERVER.antipodeDamage.get().floatValue() * 0.25F, false);
        }
        return budget;
    }

    /** Column offsets in a disc, nearest first. */
    private static int[][] columns(float r) {
        int R = Mth.ceil(r);
        List<int[]> out = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    out.add(new int[]{dx, dz});
                }
            }
        }
        out.sort((p, q) -> Integer.compare(p[0] * p[0] + p[1] * p[1], q[0] * q[0] + q[1] * q[1]));
        return out.toArray(new int[0][]);
    }

    /** The other world's relief at a column: the mirror of ours, raised into crystal spires here and there. */
    private static int exchange(Active a, int dx, int dz) {
        ServerLevel l = a.level;
        int x = Mth.floor(a.target.x) + dx, z = Mth.floor(a.target.z) + dz;
        int y0 = Mth.floor(a.target.y);
        double d = Math.sqrt(dx * dx + dz * dz);
        double edge = Mth.clamp((a.radius - d) / (a.radius * 0.25), 0, 1);
        edge = edge * edge * (3 - 2 * edge);
        int top = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int s = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        double relief = s - y0;
        // the other world: its hills are our valleys; and its land has its own swell
        double swell = Math.sin(x * 0.11 + a.seed % 97) * Math.cos(z * 0.09 + a.seed % 61) * 3.5;
        int n = (int) Math.round(Mth.lerp(edge, s, y0 - relief * 0.9 + swell));
        n = Math.max(l.getMinBuildHeight() + 2, Math.min(l.getMaxBuildHeight() - 20, n));
        Random r = new Random(a.seed ^ BlockPos.asLong(x, 0, z));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int used = 0;
        // clear everything above the new ground
        for (int y = Math.max(top, s + 1); y > n; y--) {
            m.set(x, y, z);
            if (!l.getBlockState(m).isAir()) {
                l.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                used++;
            }
        }
        // the other world's soil, from a little under the new surface up to it
        boolean warped = Math.sin(x * 0.07 + z * 0.05 + a.seed % 13) + r.nextDouble() * 0.3 > 0.15;
        for (int y = Math.min(n - 4, s - 3); y <= n; y++) {
            m.set(x, y, z);
            BlockState st;
            if (y == n) {
                st = (d > a.radius - 1.3 ? Blocks.CRYING_OBSIDIAN : warped ? Blocks.WARPED_NYLIUM : Blocks.CRIMSON_NYLIUM).defaultBlockState();
            } else if (r.nextInt(30) == 0) {
                st = Blocks.CRYING_OBSIDIAN.defaultBlockState();
            } else {
                st = (y > n - 3 ? Blocks.BLACKSTONE : Blocks.DEEPSLATE).defaultBlockState();
            }
            l.setBlock(m, st, FLAGS);
            used++;
        }
        // its flora: glowing fungus, and rare spires of crystal
        if (d < a.radius - 2) {
            int roll = r.nextInt(1000);
            if (roll < 9) {
                int h = 4 + r.nextInt(9);
                for (int y = n + 1; y <= n + h; y++) {
                    l.setBlock(m.set(x, y, z), Blocks.AMETHYST_BLOCK.defaultBlockState(), FLAGS);
                }
                l.setBlock(m.set(x, n + h + 1, z), Blocks.AMETHYST_CLUSTER.defaultBlockState(), FLAGS);
                used += h + 1;
            } else if (roll < 40) {
                l.setBlock(m.set(x, n + 1, z), (warped ? Blocks.WARPED_FUNGUS : Blocks.CRIMSON_FUNGUS).defaultBlockState(), FLAGS);
                used++;
            } else if (roll < 52) {
                l.setBlock(m.set(x, n, z), Blocks.SHROOMLIGHT.defaultBlockState(), FLAGS);
                used++;
            }
        }
        return used;
    }

    // ------------------------------------------------------------------------------------------------ shared

    private static void hurt(Active a, Vec3 c, float r, float dmg, boolean throwUp) {
        Entity owner = a.level.getEntity(a.callerId);
        boolean callerSafe = OConfig.SERVER.callerSafe.get();
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r * 3 + 40, c.z + r);
        for (LivingEntity e : a.level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            if (e.getUUID().equals(a.caller) && callerSafe) {
                continue;
            }
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurt(owner != null ? a.level.damageSources().indirectMagic(owner, owner) : a.level.damageSources().magic(), dmg);
            if (a.cannon == Cannon.HELIOS) {
                e.setSecondsOnFire(8);
            }
            if (throwUp) {
                Vec3 out = new Vec3(dx, 0, dz);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : out.normalize().scale(1.2);
                e.setDeltaMovement(out.add(0, 1.1, 0));
                e.hurtMarked = true;
            }
        }
    }
}
