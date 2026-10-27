package com.ironlung.mission;

import com.ironlung.block.CageLampBlock;
import com.ironlung.block.ControlBlock;
import com.ironlung.block.ControlType;
import com.ironlung.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The cramped interior. Air space is 3 wide x 3 tall x 5 long, nose to the north.
 * Every position is relative to the slot's base corner; the same constants are used on the client.
 */
public final class SubInterior {
    private SubInterior() {}

    public static final int SLOT_SPACING = 64;
    public static final int BASE_Y = 100;

    public static BlockPos base(int slot) {
        return new BlockPos(slot * SLOT_SPACING, BASE_Y, 0);
    }

    /** Where the pilot stands, relative to base. */
    public static final double SPAWN_X = 1.5, SPAWN_Y = 0.0, SPAWN_Z = 2.5;

    public static final Map<ControlType, BlockPos> CONTROL_POS = new EnumMap<>(ControlType.class);
    private static final Map<ControlType, Direction> CONTROL_FACING = new EnumMap<>(ControlType.class);
    public static final List<BlockPos> LAMPS = List.of(new BlockPos(1, 2, 1), new BlockPos(1, 2, 3));

    static {
        put(ControlType.CAMERA, 1, 1, -1, Direction.SOUTH);
        put(ControlType.HELM, 1, 0, -1, Direction.SOUTH);
        put(ControlType.PRESSURE_GAUGE, 0, 1, -1, Direction.SOUTH);
        put(ControlType.DEPTH_GAUGE, 2, 1, -1, Direction.SOUTH);
        put(ControlType.BALLAST, 0, 0, -1, Direction.SOUTH);
        put(ControlType.PROXIMITY, 2, 0, -1, Direction.SOUTH);
        put(ControlType.CHART, -1, 1, 1, Direction.EAST);
        put(ControlType.SEALANT, -1, 0, 3, Direction.EAST);
        put(ControlType.BREAKER, 3, 1, 2, Direction.WEST);
        put(ControlType.HATCH, 1, 1, 5, Direction.NORTH);
    }

    private static void put(ControlType t, int x, int y, int z, Direction f) {
        CONTROL_POS.put(t, new BlockPos(x, y, z));
        CONTROL_FACING.put(t, f);
    }

    /** Hull positions (relative) used as sources for positional sounds outside the walls. */
    public static final List<BlockPos> HULL_SOUND_POINTS = List.of(
            new BlockPos(-1, 1, 0), new BlockPos(-1, 1, 4), new BlockPos(3, 1, 0), new BlockPos(3, 1, 4),
            new BlockPos(1, 3, 2), new BlockPos(1, -1, 2), new BlockPos(1, 1, -1), new BlockPos(1, 1, 5),
            new BlockPos(-1, 2, 2), new BlockPos(3, 2, 2));

    public static void build(ServerLevel level, BlockPos base) {
        // clear a generous box first, then lay the shell
        for (int x = -3; x <= 5; x++)
            for (int y = -3; y <= 5; y++)
                for (int z = -3; z <= 7; z++)
                    set(level, base.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        for (int x = -1; x <= 3; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -1; z <= 5; z++) {
                    boolean shell = x == -1 || x == 3 || y == -1 || y == 3 || z == -1 || z == 5;
                    if (!shell) continue;
                    set(level, base.offset(x, y, z), shellBlock(x, y, z));
                }
            }
        }
        // outer skin: a second layer so nothing reads as a thin wall
        for (int x = -2; x <= 4; x++)
            for (int y = -2; y <= 4; y++)
                for (int z = -2; z <= 6; z++) {
                    boolean skin = x == -2 || x == 4 || y == -2 || y == 4 || z == -2 || z == 6;
                    if (skin) set(level, base.offset(x, y, z), ModBlocks.HULL_PLATE.get().defaultBlockState());
                }

        // pipes along the upper corners
        BlockState pipeZ = ModBlocks.PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z = 0; z <= 4; z++) {
            set(level, base.offset(0, 2, z), pipeZ);
            set(level, base.offset(2, 2, z), pipeZ);
        }
        BlockState pipeY = ModBlocks.PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        set(level, base.offset(2, 0, 4), pipeY);
        set(level, base.offset(2, 1, 4), pipeY);

        for (BlockPos l : LAMPS) {
            set(level, base.offset(l), ModBlocks.CAGE_LAMP.get().defaultBlockState().setValue(CageLampBlock.LIT, true));
        }

        for (Map.Entry<ControlType, BlockPos> e : CONTROL_POS.entrySet()) {
            BlockState s = ModBlocks.control(e.getKey()).defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, CONTROL_FACING.get(e.getKey()))
                    .setValue(ControlBlock.POWERED, true);
            set(level, base.offset(e.getValue()), s);
        }
        set(level, base.offset(3, 1, 1), ModBlocks.WARNING_PLATE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        set(level, base.offset(-1, 1, 3), ModBlocks.WARNING_PLATE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        set(level, base.offset(1, 0, 5), ModBlocks.HULL_SEAM.get().defaultBlockState());
    }

    private static BlockState shellBlock(int x, int y, int z) {
        Block b;
        if (y == -1) b = ModBlocks.GRATE_FLOOR.get();
        else if (y == 3) b = (z % 2 == 0) ? ModBlocks.HULL_RIVETED.get() : ModBlocks.HULL_SEAM.get();
        else {
            int h = Math.floorMod(x * 7 + y * 13 + z * 5, 5);
            b = h == 0 ? ModBlocks.BOLTED_PANEL.get() : h == 1 ? ModBlocks.HULL_SEAM.get() : h == 2 ? ModBlocks.HULL_PLATE.get() : ModBlocks.HULL_RIVETED.get();
        }
        return b.defaultBlockState();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** Push power state into lamps and consoles. lamps: whether bulbs glow; consoles: whether screens are live. */
    public static void applyPower(ServerLevel level, BlockPos base, boolean lamps, boolean consoles) {
        for (BlockPos l : LAMPS) {
            BlockPos p = base.offset(l);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CageLampBlock && s.getValue(CageLampBlock.LIT) != lamps) {
                level.setBlock(p, s.setValue(CageLampBlock.LIT, lamps), Block.UPDATE_ALL);
            }
        }
        for (Map.Entry<ControlType, BlockPos> e : CONTROL_POS.entrySet()) {
            if (!e.getKey().powerable) continue;
            BlockPos p = base.offset(e.getValue());
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ControlBlock && s.getValue(ControlBlock.POWERED) != consoles) {
                level.setBlock(p, s.setValue(ControlBlock.POWERED, consoles), Block.UPDATE_ALL);
            }
        }
    }

    public static void clear(ServerLevel level, BlockPos base) {
        for (int x = -3; x <= 5; x++)
            for (int y = -3; y <= 5; y++)
                for (int z = -3; z <= 7; z++)
                    set(level, base.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }
}
