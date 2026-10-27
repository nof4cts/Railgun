package com.ironlung.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thick, corroded pipe run. */
public class PipeBlock extends RotatedPillarBlock {
    private static final VoxelShape X = Block.box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape Y = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape Z = Block.box(4, 4, 0, 12, 12, 16);

    public PipeBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(AXIS);
        return axis == Direction.Axis.X ? X : axis == Direction.Axis.Y ? Y : Z;
    }
}
