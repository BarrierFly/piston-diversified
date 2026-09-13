package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Head of the 虚弱活塞 — vanilla plate with a 2×2 pixel arm. */
public class WeakPistonHeadBlock extends ModPistonHeadBlock {
    public WeakPistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape plate = PdShapes.slab(facing, 0, 4);
        VoxelShape arm = state.getValue(SHORT) ? PdShapes.rod(facing, 4, 16, 2) : PdShapes.rod(facing, 4, 20, 2);
        return Shapes.or(plate, arm);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
