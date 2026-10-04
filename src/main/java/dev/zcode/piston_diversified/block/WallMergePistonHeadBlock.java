package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Head of the 墙并活塞 family — the arm is a wall-centre-post (8×8) instead of the vanilla 4×4
 * rod, so side-by-side same-facing heads form a connected wall.
 */
public class WallMergePistonHeadBlock extends ModPistonHeadBlock {
    public WallMergePistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return Shapes.or(PdShapes.slab(facing, 0, 4), PdShapes.rod(facing, 4, 20, 8));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
