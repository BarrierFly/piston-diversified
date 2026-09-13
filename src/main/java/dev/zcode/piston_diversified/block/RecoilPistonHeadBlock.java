package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Head of the 后坐活塞 — half-block thick plate (8px) plus the regular 4px arm. */
public class RecoilPistonHeadBlock extends ModPistonHeadBlock {
    public RecoilPistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape plate = PdShapes.slab(facing, 0, 8);
        VoxelShape arm = state.getValue(SHORT) ? PdShapes.rod(facing, 8, 16, 4) : PdShapes.rod(facing, 8, 20, 4);
        return Shapes.or(plate, arm);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
