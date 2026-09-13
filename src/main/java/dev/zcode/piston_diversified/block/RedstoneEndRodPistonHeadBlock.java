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

    /**
     * The {@code direction} parameter is measured from the asking block towards this block, so a
     * block at {@code pos.relative(d)} is fed when {@code direction == d.getOpposite()} (verified
     * against vanilla ObserverBlock, which powers its back cell with {@code FACING == direction}).
     * Torch-style weak power in five directions = everything except the block above.
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction != Direction.DOWN ? 15 : 0;
    }

    /** Strong power towards the cell the rod points at. */
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING).getOpposite() ? 15 : 0;
    }
}
