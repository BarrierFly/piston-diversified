package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bare end-rod head (no plate). Used by 活塞端杆 and, with a signal twist, by 活塞红石端杆.
 */
public class EndRodPistonHeadBlock extends ModPistonHeadBlock {
    public EndRodPistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PdShapes.rod(state.getValue(FACING), 0, 16, 4);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
