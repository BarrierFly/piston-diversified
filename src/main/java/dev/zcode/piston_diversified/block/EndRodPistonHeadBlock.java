package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bare end-rod head (no plate). Used by 活塞端杆 and, with a signal twist, by 活塞红石端杆.
 *
 * <p>The rod respects {@code SHORT} like the weak/recoil heads do: nothing currently places this
 * head with {@code SHORT=true} (heads only ever get the default {@code SHORT=false} state), so the
 * long rod is the norm — but if a short head is ever left standing on its own, the outline shrinks
 * to the short model instead of leaving invisible collision and a rod disconnected from the base.</p>
 */
public class EndRodPistonHeadBlock extends ModPistonHeadBlock {
    public EndRodPistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PdShapes.rod(state.getValue(FACING), 0, state.getValue(SHORT) ? 12 : 16, 4);
    }
}
