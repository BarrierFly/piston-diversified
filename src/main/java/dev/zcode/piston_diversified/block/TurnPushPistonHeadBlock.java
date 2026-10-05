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
        Direction bend = state.getValue(BEND);
        // vanilla's arm: a 4x4 bar running from just behind the tip out past the base cell
        // (z 4..20 for the long head, 4..16 for the short one). When the plate sits on an east or
        // west face the centred bar would miss it by 2px, so both the bar and the model are
        // nudged against the plate — see gen_assets.bent_head_model.
        double armTo = state.getValue(SHORT) ? 16.0 : 20.0;
        return Shapes.or(PdShapes.arm(facing, bend, 4.0, armTo), PdShapes.slab(bend, 0, 4));
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
