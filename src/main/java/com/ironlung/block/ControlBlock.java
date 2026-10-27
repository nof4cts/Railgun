package com.ironlung.block;

import com.ironlung.IronLungMod;
import com.ironlung.mission.MissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Every interactive part of the submarine: one block class, behaviour chosen by {@link ControlType}. */
public class ControlBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public final ControlType type;

    public ControlBlock(ControlType type, Properties props) {
        super(props);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(POWERED, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean inSub = level.dimension().equals(IronLungMod.DIMENSION);
        if (level.isClientSide) {
            if (inSub && type.opensScreen()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironlung.client.ClientHooks.openControl(type));
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            if (!inSub || !MissionManager.hasMission(sp)) {
                sp.displayClientMessage(Component.translatable("ironlung.msg.dead_console").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
                return InteractionResult.CONSUME;
            }
            if (!type.opensScreen()) MissionManager.onControlUsed(sp, type, pos);
        }
        return InteractionResult.CONSUME;
    }
}
