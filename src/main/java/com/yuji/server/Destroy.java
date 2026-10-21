package com.yuji.server;

import com.yuji.Y;
import com.yuji.net.YNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * The destruction engine: real, server-side block changes, streamed in a shared per-tick budget so even a World
 * Ender of hundreds of blocks never stalls the server. Shapes:
 *   crater   a bowl, a raised rim of thrown-up earth, and fractures running out through the ground
 *   flatten  everything above a shallow bowl is swept away (the giant ultimates), with a low rim and fractures
 *   sphere   a ball carved out of whatever is there
 *   trench   a directional cut that races along its line, from the treetops down into the ground
 *   slab     the thin plane a slash leaves through everything it passes
 * Columns are processed nearest-first behind an expanding front, so the world comes apart as a wave in step with
 * the shockwave you see. Protected blocks are skipped unless destruction.unrestricted is on; VISUAL mode sends the
 * debris without touching the world; every change is journaled (up to destruction.restoreLimit) for /yuji restore.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class Destroy {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final List<Job> JOBS = new ArrayList<>();
    /** Recent destruction, newest last: each entry is one cast's changes (packed pos, old state). */
    private static final Deque<Journal> JOURNAL = new ArrayDeque<>();
    private static int journaled;

    private Destroy() {}

    static final class Journal {
        final ServerLevel level;
        final List<long[]> pos = new ArrayList<>();
        final List<BlockState> old = new ArrayList<>();

        Journal(ServerLevel level) {
            this.level = level;
        }
    }

    private abstract static class Job {
        final ServerLevel level;
        final ServerPlayer owner;
        final Journal journal;
        final Vec3 centre;
        final float debrisPower;
        final List<BlockPos> debrisPos = new ArrayList<>();
        final List<BlockState> debrisState = new ArrayList<>();
        int seen;
        long age;

        Job(ServerLevel level, ServerPlayer owner, Journal journal, Vec3 centre, float debrisPower) {
            this.level = level;
            this.owner = owner;
            this.journal = journal;
            this.centre = centre;
            this.debrisPower = debrisPower;
        }

        /** Work within the budget; returns blocks changed (or scanned, weighted). True when finished. */
        abstract boolean step(int[] budget);

        boolean breakable(BlockPos pos, BlockState s) {
            if (s.isAir()) return false;
            if (!Y.unrestricted) {
                if (s.is(Blocks.BEDROCK) || s.is(Blocks.COMMAND_BLOCK) || s.is(Blocks.CHAIN_COMMAND_BLOCK) || s.is(Blocks.REPEATING_COMMAND_BLOCK)
                        || s.is(Blocks.STRUCTURE_BLOCK) || s.is(Blocks.JIGSAW) || s.is(Blocks.BARRIER) || s.is(Blocks.END_PORTAL_FRAME)
                        || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY) || s.is(Blocks.REINFORCED_DEEPSLATE)) return false;
                if (s.getDestroySpeed(level, pos) < 0) return false;
                if (owner != null && !level.mayInteract(owner, pos)) return false;
            }
            return true;
        }

        void remove(BlockPos pos, int[] budget) {
            BlockState s = level.getBlockState(pos);
            budget[0]--;
            if (!breakable(pos, s)) return;
            set(pos.immutable(), s, AIR, budget);
            seen++;
            if (debrisPos.size() < 60 && (seen % 7 == 0) && s.getFluidState().isEmpty()) {
                debrisPos.add(pos.immutable());
                debrisState.add(s);
            }
        }

        void place(BlockPos pos, BlockState to, int[] budget) {
            BlockState s = level.getBlockState(pos);
            budget[0]--;
            if (!s.isAir() && !s.canBeReplaced()) return;
            set(pos.immutable(), s, to, budget);
        }

        private void set(BlockPos pos, BlockState from, BlockState to, int[] budget) {
            budget[1]--;
            if (!Y.destroys()) return;
            if (journal != null && journaled < Y.restoreLimit) {
                journal.pos.add(new long[]{pos.asLong()});
                journal.old.add(from);
                journaled++;
            }
            level.setBlock(pos, to, FLAGS);
        }

        void flushDebris() {
            if (debrisPos.isEmpty()) return;
            YNet.near(level, centre, 320, new YNet.Debris(centre, new ArrayList<>(debrisPos), new ArrayList<>(debrisState), debrisPower));
            debrisPos.clear();
            debrisState.clear();
        }
    }

    // ------------------------------------------------------------------ public API
    public static Journal journal(ServerLevel level) {
        Journal j = new Journal(level);
        JOURNAL.addLast(j);
        while (JOURNAL.size() > 12) {
            Journal gone = JOURNAL.removeFirst();
            journaled -= gone.pos.size();
        }
        return j;
    }

    /** A crater (style 0), a flattening (1) or a sphere (2) of radius r around c. */
    public static void blast(ServerLevel level, ServerPlayer owner, Journal j, Vec3 c, double r, double depth, int style) {
        if (Y.destructionOff() || r < 1) return;
        r = Math.min(r, Y.maxRadius);
        JOBS.add(new Radial(level, owner, j, c, r, depth, style));
    }

    public static void trench(ServerLevel level, ServerPlayer owner, Journal j, Vec3 start, Vec3 dir, double len, double width, double depth, int ticks) {
        if (Y.destructionOff() || len < 1) return;
        JOBS.add(new Trench(level, owner, j, start, dir, Math.min(len, Y.maxRadius * 3.0), width, depth, Math.max(1, ticks)));
    }

    /** The thin plane a slash cuts: immediate, bounded. */
    public static void slab(ServerLevel level, ServerPlayer owner, Journal j, Vec3 origin, Vec3 dir, Vec3 side, double len, double width) {
        if (Y.destructionOff()) return;
        Job job = new Job(level, owner, j, origin.add(dir.scale(len * 0.5)), 0.6f) {
            boolean step(int[] b) {
                return true;
            }
        };
        int[] budget = {20000, 20000};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double hw = width * 0.5;
        for (double a = 1.5; a <= len; a += 0.5) {
            for (double w = -hw; w <= hw; w += 0.5) {
                Vec3 p = origin.add(dir.scale(a)).add(side.scale(w));
                pos.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
                if (!level.isLoaded(pos)) continue;
                job.remove(pos, budget);
                if (budget[0] <= 0) break;
            }
        }
        job.flushDebris();
    }

    public static int restore(ServerLevel level, int casts) {
        int n = 0;
        for (int k = 0; k < casts && !JOURNAL.isEmpty(); k++) {
            Journal j = JOURNAL.removeLast();
            if (j.level != level) continue;
            for (int i = j.pos.size() - 1; i >= 0; i--) {
                level.setBlock(BlockPos.of(j.pos.get(i)[0]), j.old.get(i), Block.UPDATE_CLIENTS);
                n++;
            }
            journaled -= j.pos.size();
        }
        JOBS.removeIf(job -> job.level == level);
        return n;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
        int[] budget = {Y.blocksPerTick * 6, Y.blocksPerTick};
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext() && budget[0] > 0 && budget[1] > 0) {
            Job j = it.next();
            j.age++;
            boolean done = j.step(budget);
            j.flushDebris();
            if (done) it.remove();
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        JOBS.clear();
        JOURNAL.clear();
        journaled = 0;
    }

    static double surface(ServerLevel level, double x, double z, double near) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (h <= level.getMinBuildHeight() + 1) return near;
        return h;
    }

    // ------------------------------------------------------------------ radial: crater, flatten, sphere
    private static final class Radial extends Job {
        final double r, depth;
        final int style;
        final List<int[]> cols = new ArrayList<>();
        final int cx, cy, cz;
        final double speed;
        int index;
        int y = Integer.MIN_VALUE;
        final long seed;

        Radial(ServerLevel level, ServerPlayer owner, Journal j, Vec3 c, double r, double depth, int style) {
            super(level, owner, j, c, (float) Math.min(3.0, 0.6 + r / 25.0));
            this.r = r;
            this.depth = depth;
            this.style = style;
            this.cx = Mth.floor(c.x);
            this.cy = Mth.floor(c.y);
            this.cz = Mth.floor(c.z);
            this.seed = (long) (c.x * 31 + c.z * 17);
            int R = (int) Math.ceil(r * (style == 2 ? 1.0 : 1.9));
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= R * R) cols.add(new int[]{dx, dz, d2});
                }
            }
            cols.sort(Comparator.comparingInt(a -> a[2]));
            this.speed = Math.max(1.5, r / 30.0);
        }

        private double spokes(double ang) {
            // eight fractures, each wandering a little
            double best = 9;
            for (int k = 0; k < 8; k++) {
                double a0 = k * Math.PI / 4 + Math.sin(seed * 0.37 + k) * 0.3;
                double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - a0))) * Math.PI / 180;
                best = Math.min(best, d);
            }
            return best;
        }

        @Override
        boolean step(int[] budget) {
            double front = speed * (age + 2);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int top = level.getMaxBuildHeight() - 1, bottom = level.getMinBuildHeight();
            while (index < cols.size() && budget[0] > 0 && budget[1] > 0) {
                int[] col = cols.get(index);
                double d = Math.sqrt(col[2]);
                if (d > front) return false;
                int x = cx + col[0], z = cz + col[1];
                pos.set(x, cy, z);
                if (!level.isLoaded(pos)) {
                    index++;
                    y = Integer.MIN_VALUE;
                    continue;
                }
                double k = d / r;
                double noise = Math.sin(x * 0.37 + seed) * Math.cos(z * 0.29 - seed) * 0.8;
                if (style == 2) {
                    // sphere
                    double half = Math.sqrt(Math.max(0, r * r - col[2]));
                    int y0 = (int) Math.floor(cy - half), y1 = (int) Math.ceil(cy + half);
                    if (y == Integer.MIN_VALUE) y = Math.min(top, y1);
                    while (y >= Math.max(bottom, y0) && budget[0] > 0) {
                        pos.set(x, y, z);
                        remove(pos, budget);
                        y--;
                    }
                    if (y < Math.max(bottom, y0)) next();
                    continue;
                }
                int surf = (int) surface(level, x, z, cy);
                if (k <= 1.0) {
                    double bowl = depth * r * (1 - k * k) + noise;
                    if (style == 1) bowl = depth * r * (1 - k * k) * 0.6 + noise;
                    int floor = Math.max(bottom, (int) Math.floor(cy - bowl));
                    int ceil = Math.min(top, Math.max(surf, cy) + (int) (r * (style == 1 ? 0.9 : 0.6)) + 12);
                    if (y == Integer.MIN_VALUE) y = ceil;
                    while (y >= floor && budget[0] > 0) {
                        pos.set(x, y, z);
                        if (level.getBlockState(pos).isAir()) budget[0]--;
                        else remove(pos, budget);
                        y--;
                    }
                    if (y < floor) next();
                } else if (k <= (style == 1 ? 1.12 : 1.28)) {
                    // the rim: earth thrown up and piled around the edge
                    double peak = style == 1 ? 1.05 : 1.12;
                    double h = r * (style == 1 ? 0.05 : 0.11) * Math.max(0, 1 - Math.abs(k - peak) / (style == 1 ? 0.07 : 0.16)) + noise * 0.5;
                    int hb = (int) Math.round(h);
                    for (int i = 1; i <= hb && budget[0] > 0; i++) {
                        pos.set(x, surf + i - 1, z);
                        place(pos, rimBlock(x, z, i), budget);
                    }
                    next();
                } else {
                    // fractures running out through the ground
                    double ang = Math.atan2(col[1], col[0]);
                    double reach = style == 1 ? 1.6 : 1.9;
                    double off = spokes(ang) * d;
                    if (k <= reach && off < 1.2 * (1 - (k - 1) / (reach - 1))) {
                        int crack = 2 + (int) (4 * (1 - (k - 1) / (reach - 1)));
                        for (int i = 0; i < crack && budget[0] > 0; i++) {
                            pos.set(x, surf - 1 - i, z);
                            remove(pos, budget);
                        }
                    }
                    next();
                }
            }
            return index >= cols.size();
        }

        private void next() {
            index++;
            y = Integer.MIN_VALUE;
        }

        private BlockState rimBlock(int x, int z, int layer) {
            int h = (x * 73856093) ^ (z * 19349663) ^ (layer * 83492791);
            int m = Math.floorMod(h, 10);
            if (m < 4) return Blocks.COARSE_DIRT.defaultBlockState();
            if (m < 6) return Blocks.GRAVEL.defaultBlockState();
            if (m < 9) return Blocks.COBBLESTONE.defaultBlockState();
            return Blocks.STONE.defaultBlockState();
        }
    }

    // ------------------------------------------------------------------ trench
    private static final class Trench extends Job {
        final Vec3 start, dir, across;
        final double len, width, depth, perTick;
        double done;
        int col = Integer.MIN_VALUE, y = Integer.MIN_VALUE;

        Trench(ServerLevel level, ServerPlayer owner, Journal j, Vec3 start, Vec3 dir, double len, double width, double depth, int ticks) {
            super(level, owner, j, start, 1.2f);
            Vec3 h = new Vec3(dir.x, 0, dir.z);
            this.dir = h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
            this.across = new Vec3(-this.dir.z, 0, this.dir.x);
            this.start = start;
            this.len = len;
            this.width = width;
            this.depth = depth;
            this.perTick = len / ticks;
        }

        @Override
        boolean step(int[] budget) {
            double front = perTick * (age + 1);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int hw = (int) Math.ceil(width / 2);
            int bottom = level.getMinBuildHeight();
            while (done <= Math.min(front, len) && budget[0] > 0 && budget[1] > 0) {
                double taper = Math.max(0.3, 1 - Math.pow(done / len, 3));
                int w = Math.max(0, (int) Math.round(hw * taper));
                if (col == Integer.MIN_VALUE) col = -w;
                if (col > w) {
                    done += 0.5;
                    col = Integer.MIN_VALUE;
                    continue;
                }
                Vec3 p = start.add(dir.scale(done)).add(across.scale(col));
                int x = Mth.floor(p.x), z = Mth.floor(p.z);
                pos.set(x, Mth.floor(p.y), z);
                if (!level.isLoaded(pos)) {
                    col++;
                    y = Integer.MIN_VALUE;
                    continue;
                }
                double surf = surface(level, x, z, p.y);
                int floor = Math.max(bottom, (int) Math.floor(Math.min(surf, p.y) - depth * taper * (1 - Math.abs(col) / (w + 1.0) * 0.5)));
                int ceil = (int) surf + (depth >= 20 ? 40 : 12);
                if (y == Integer.MIN_VALUE) y = ceil;
                while (y >= floor && budget[0] > 0) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) budget[0]--;
                    else remove(pos, budget);
                    y--;
                }
                if (y < floor) {
                    col++;
                    y = Integer.MIN_VALUE;
                }
            }
            return done > len;
        }
    }

    static boolean plant(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE);
    }
}
