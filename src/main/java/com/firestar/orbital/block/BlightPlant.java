package com.firestar.orbital.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What grows on blighted ground: pale tendrils and glowing bulbs. Grows on any solid ground the blight has taken. */
public class BlightPlant extends BushBlock {
    private static final VoxelShape SHAPE = BushBlock.box(2, 0, 2, 14, 13, 14);

    public BlightPlant(Properties p) {
        super(p);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
