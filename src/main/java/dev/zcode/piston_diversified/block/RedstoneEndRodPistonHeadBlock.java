package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Head of the 活塞红石端杆 — outputs a redstone-torch style signal while in place: weak
 * activation in five directions (not upwards) and strong power towards the front cell.
 */
public class RedstoneEndRodPistonHeadBlock extends EndRodPistonHeadBlock {
    public RedstoneEndRodPistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? 0 : 15;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING) ? 15 : 0;
    }
}
