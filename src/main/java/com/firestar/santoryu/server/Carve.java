package com.firestar.santoryu.server;

import com.firestar.santoryu.config.ZConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Slashes that cut the land: a thin slab swept along a segment, everything soft enough inside it gone. */
public final class Carve {
    private Carve() {}

    public static boolean allowed(ServerLevel level) {
        return ZConfig.SERVER.cutBlocks.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * @param normal  the slab's normal; blocks further than {@code thickness} from the cut plane survive
     * @param hardest the hardest block (destroy speed) the cut gets through; obsidian is 50, stone 1.5
     */
    public static int slab(ServerLevel level, Vec3 a, Vec3 b, double radius, Vec3 normal, double thickness, float hardest, int budget) {
        if (!allowed(level) || budget <= 0) {
            return 0;
        }
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(ab.lengthSqr(), 1.0E-6);
        int minX = (int) Math.floor(Math.min(a.x, b.x) - radius), maxX = (int) Math.ceil(Math.max(a.x, b.x) + radius);
        int minY = (int) Math.floor(Math.min(a.y, b.y) - radius), maxY = (int) Math.ceil(Math.max(a.y, b.y) + radius);
        int minZ = (int) Math.floor(Math.min(a.z, b.z) - radius), maxZ = (int) Math.ceil(Math.max(a.z, b.z) + radius);
        int cut = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = Math.max(minY, level.getMinBuildHeight()); y <= Math.min(maxY, level.getMaxBuildHeight() - 1); y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vec3 c = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    Vec3 rel = c.subtract(a);
                    if (Math.abs(rel.dot(n)) > thickness) {
                        continue;
                    }
                    double k = Math.max(0.0, Math.min(1.0, rel.dot(ab) / len2));
                    if (c.distanceToSqr(a.add(ab.scale(k))) > radius * radius) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0.0F || hardness > hardest) {
                        continue;
                    }
                    if ((cut & 7) == 0) {
                        level.destroyBlock(pos, false);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    if (++cut >= budget) {
                        return cut;
                    }
                }
            }
        }
        return cut;
    }

    public static int budget() {
        return ZConfig.SERVER.maxBlocksPerTick.get();
    }
}
