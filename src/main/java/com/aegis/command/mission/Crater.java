package com.aegis.command.mission;

import com.aegis.command.AegisConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Airstrike destruction: carves an irregular bowl-shaped crater, launches a share of the removed
 * blocks as physical rubble (falling blocks that land and settle), scorches the surrounding
 * ground, starts fires, and collapses anything left hanging over the hole so buildings come down.
 * Block entities (chests, furnaces...) and unbreakable blocks are never touched.
 */
public final class Crater {
    private Crater() {}

    public static void carve(ServerLevel level, Vec3 centre, float power) {
        if (!AegisConfig.COMMON.craters.get()) return;
        RandomSource r = level.random;
        double scale = AegisConfig.COMMON.craterScale.get();
        double radius = power * 1.15 * scale;
        int ir = Mth.ceil(radius + 3);
        BlockPos c = BlockPos.containing(centre);
        long seed = r.nextLong();
        int rubble = 0, maxRubble = (int) (34 * scale);

        // 1. bowl: flattened sphere with noisy edge
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                for (int dy = -ir; dy <= ir; dy++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + (dy * 1.45) * (dy * 1.45));
                    double edge = radius * (0.82 + 0.3 * noise(seed, dx, dy, dz));
                    if (dist > edge) continue;
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (!breakable(level, p, s)) continue;
                    if (!s.getFluidState().isEmpty() && s.getFluidState().isSource()) continue;
                    boolean launch = rubble < maxRubble && dy >= -1 && r.nextFloat() < 0.16f && s.isSolidRender(level, p);
                    if (launch) {
                        FallingBlockEntity fb = FallingBlockEntity.fall(level, p, s);
                        fb.dropItem = false;
                        Vec3 out = new Vec3(dx + r.nextGaussian() * 0.5, 0, dz + r.nextGaussian() * 0.5);
                        if (out.lengthSqr() < 1e-3) out = new Vec3(r.nextGaussian(), 0, r.nextGaussian());
                        out = out.normalize().scale(0.35 + r.nextDouble() * 0.75);
                        fb.setDeltaMovement(out.x, 0.55 + r.nextDouble() * 0.75, out.z);
                        fb.hurtMarked = true;
                        rubble++;
                    } else {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 2. scorched floor and rim, embers and fires
        int scorchR = Mth.ceil(radius * 1.9);
        for (int dx = -scorchR; dx <= scorchR; dx++) {
            for (int dz = -scorchR; dz <= scorchR; dz++) {
                double hd = Math.sqrt(dx * dx + dz * dz);
                if (hd > scorchR * (0.8 + 0.3 * noise(seed + 9, dx, 0, dz))) continue;
                // find the exposed surface in this column near the crater
                for (int dy = 4; dy >= -ir - 2; dy--) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !level.getBlockState(p.above()).isAir()) continue;
                    if (!breakable(level, p, s)) break;
                    double k = hd / scorchR;
                    BlockState burnt = scorched(s, k, r);
                    if (burnt != null) level.setBlock(p, burnt, Block.UPDATE_CLIENTS);
                    if (k < 0.85 && r.nextFloat() < 0.2f) {
                        BlockState fire = BaseFireBlock.getState(level, p.above());
                        if (fire.canSurvive(level, p.above())) level.setBlock(p.above(), fire, Block.UPDATE_ALL);
                    }
                    break;
                }
            }
        }

        // 3. structural collapse: anything left unsupported above the hole falls
        int collapsed = 0, maxCollapse = (int) (160 * scale);
        int cr = Mth.ceil(radius + 2);
        for (int dx = -cr; dx <= cr && collapsed < maxCollapse; dx++) {
            for (int dz = -cr; dz <= cr && collapsed < maxCollapse; dz++) {
                for (int dy = -2; dy <= 18 && collapsed < maxCollapse; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !breakable(level, p, s) || !s.getFluidState().isEmpty()) continue;
                    if (!s.isCollisionShapeFullBlock(level, p)) continue;
                    if (!level.getBlockState(p.below()).isAir()) continue;
                    // supported sideways by at least two solid neighbours? then it holds (arches, overhangs)
                    int support = 0;
                    for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
                        if (!level.getBlockState(n).isAir() && level.getBlockState(n.below()).isSolidRender(level, n.below())) support++;
                    }
                    if (support >= 2 && Math.sqrt(dx * dx + dz * dz) > radius) continue;
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, p, s);
                    fb.dropItem = false;
                    fb.setDeltaMovement(r.nextGaussian() * 0.04, -0.05, r.nextGaussian() * 0.04);
                    collapsed++;
                }
            }
        }
    }

    private static boolean breakable(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || level.getBlockEntity(p) != null) return false;
        float hardness = s.getDestroySpeed(level, p);
        return hardness >= 0 && hardness < 45 && !s.is(Blocks.BEDROCK);
    }

    /** Burnt replacement for a surface block, darker toward the centre; null to leave it. */
    private static BlockState scorched(BlockState s, double k, RandomSource r) {
        boolean natural = s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(Blocks.GRAVEL) || s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW_BLOCK) || s.is(BlockTags.TERRACOTTA);
        if (!natural) {
            // man-made: char it occasionally
            return k < 0.6 && r.nextFloat() < 0.35f ? Blocks.BLACKSTONE.defaultBlockState() : null;
        }
        if (k < 0.3) return r.nextFloat() < 0.35f ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        if (k < 0.6) return r.nextFloat() < 0.5f ? Blocks.BASALT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        if (k < 1.0) return r.nextFloat() < 0.6f ? Blocks.COARSE_DIRT.defaultBlockState() : null;
        return null;
    }

    /** Cheap smooth-ish value noise in [-1, 1]. */
    private static double noise(long seed, int x, int y, int z) {
        double a = Math.sin(x * 1.37 + seed % 97) * Math.cos(z * 1.13 + (seed >> 7) % 89);
        double b = Math.sin((x + z) * 0.71 + y * 0.53 + (seed >> 13) % 83);
        return Mth.clamp(a * 0.6 + b * 0.4, -1, 1);
    }
}
