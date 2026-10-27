package com.abomination.horror.block;

import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.registry.ModParticles;
import com.abomination.horror.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pulsing corruption The Abomination leaves around its lairs and boss arenas. It beats and whispers,
 * bleeds, saps anything that wades through it, rots away on its own, and sometimes drops a Heart Fragment -
 * the environmental clue that leads players to the hidden Awakened encounter.
 */
public class FleshGrowthBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);

    public FleshGrowthBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) level.removeBlock(pos, false); // the corruption recedes over time
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof AbominationEntity) && living.tickCount % 20 == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ModParticles.BLOOD.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.2D, pos.getZ() + random.nextDouble(), 0.0D, 0.08D, 0.0D);
        }
        if (random.nextInt(80) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    random.nextBoolean() ? ModSounds.HEARTBEAT.get() : ModSounds.WHISPER.get(), SoundSource.BLOCKS, 0.35F, 0.7F + random.nextFloat() * 0.3F, false);
        }
    }
}
