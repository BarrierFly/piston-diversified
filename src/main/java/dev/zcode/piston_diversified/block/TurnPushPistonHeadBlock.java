package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 拐弯的活塞头 (bent head of the 拐推活塞): the arm points along the push direction ({@code FACING},
 * straight, like every piston head) while the plate sits on the {@code BEND} face of the cell —
 * that face is where blocks were pushed to and where the sticky pull grabs them from.
 */
public class TurnPushPistonHeadBlock extends ModPistonHeadBlock {
    public static final EnumProperty<Direction> BEND = EnumProperty.create("bend", Direction.class);

    public TurnPushPistonHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(BEND, Direction.NORTH));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BEND);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(SHORT)) {
            return Shapes.or(PdShapes.rod(facing, 12, 16, 4), PdShapes.slab(state.getValue(BEND), 0, 4));
        }
        return Shapes.or(PdShapes.rod(facing, 0, 16, 4), PdShapes.slab(state.getValue(BEND), 0, 4));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    /** The plate's sticky face points along the bend. */
    public Direction plateFace(BlockState state) {
        return state.getValue(BEND);
    }
}
