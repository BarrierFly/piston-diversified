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
 * The retracted 墙并活塞's rod tip poking half a block past the plate (收回时超出活塞盖半格) —
 * an 8×8 wall post in the front cell, kept alive by its retracted base. Side-by-side posts
 * connect the pistons into a wall even while retracted. It is regenerated whenever it is
 * missing (it is part of the piston, not a separate structure), and it cannot be pushed.
 */
public class WallMergeRodBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public WallMergeRodBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PdShapes.rod(state.getValue(FACING), 8, 16, 8);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        return behind.getBlock() instanceof WallMergePistonBlock
            && !behind.getValue(WallMergePistonBlock.EXTENDED)
            && behind.getValue(WallMergePistonBlock.FACING) == facing;
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
