package com.gojo.rework.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Changes to the land, spread over ticks under a block budget shared by every rite: queued block positions, each with
 * what it becomes (or air). Bedrock and unbreakable blocks are left alone.
 */
public final class Carver {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final List<Job> JOBS = new ArrayList<>();

    private Carver() {}

    private record Edit(BlockPos pos, BiFunction<ServerLevel, BlockState, BlockState> to) {}

    private static final class Job {
        final ServerLevel level;
        final Deque<Edit> edits = new ArrayDeque<>();

        Job(ServerLevel level) {
            this.level = level;
        }
    }

    /** Queue a set of edits as one job (they are applied in the order given). */
    public static Batch batch(ServerLevel level) {
        return new Batch(level);
    }

    public static final class Batch {
        private final Job job;

        Batch(ServerLevel level) {
            this.job = new Job(level);
        }

        public Batch set(BlockPos pos, BiFunction<ServerLevel, BlockState, BlockState> to) {
            job.edits.add(new Edit(pos.immutable(), to));
            return this;
        }

        public Batch erase(BlockPos pos) {
            return set(pos, (l, s) -> Blocks.AIR.defaultBlockState());
        }

        public void go() {
            if (!job.edits.isEmpty()) {
                JOBS.add(job);
            }
        }
    }

    public static void tick(int budget) {
        int left = budget;
        var it = JOBS.iterator();
        while (it.hasNext() && left > 0) {
            Job j = it.next();
            while (!j.edits.isEmpty() && left > 0) {
                Edit e = j.edits.poll();
                if (!j.level.isLoaded(e.pos)) {
                    continue;
                }
                BlockState old = j.level.getBlockState(e.pos);
                if (old.is(Blocks.BEDROCK) || old.getDestroySpeed(j.level, e.pos) < 0) {
                    continue;
                }
                BlockState now = e.to.apply(j.level, old);
                if (now != null && now != old) {
                    j.level.setBlock(e.pos, now, FLAGS);
                    left--;
                }
            }
            if (j.edits.isEmpty()) {
                it.remove();
            }
        }
    }

    public static void clear() {
        JOBS.clear();
    }
}
