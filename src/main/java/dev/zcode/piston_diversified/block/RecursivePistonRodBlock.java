package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 递推活塞杆 — one cell of the recursive piston's telescoping arm. A bare 4px rod that survives
 * only while an extended recursive piston (or another rod, same facing) sits behind it; breaking
 * a rod pops every rod and the head beyond it. Receives no signals and cannot be pushed.
 */
public class RecursivePistonRodBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public RecursivePistonRodBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PdShapes.rod(state.getValue(FACING), 0, 16, 4);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        if (behind.getBlock() instanceof RecursivePistonRodBlock) {
            return behind.getValue(FACING) == facing;
        }
        if (behind.getBlock() instanceof RecursivePistonBlock) {
            return behind.getValue(RecursivePistonBlock.EXTENDED)
                && behind.getValue(RecursivePistonBlock.FACING) == facing;
        }
        // the gravity piston telescopes down through the same rods
        return behind.getBlock() instanceof GravityPistonBlock
            && behind.getValue(RecursivePistonBlock.EXTENDED)
            && behind.getValue(RecursivePistonBlock.FACING) == facing;
    }

    //? if <1.21.2 {
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    //?} else {
    @Override
    public BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess scheduledTickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     net.minecraft.util.RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }
    //?}

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
