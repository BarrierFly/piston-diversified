package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
//?}

/**
 * Head of the 活塞红石端杆 — outputs a redstone-torch style signal while in place: weak
 * activation in five directions (not upwards) and strong power towards the front cell.
 *
 * <p>Because it is a signal source, appearing/disappearing must fan out a full second-order
 * neighbour update exactly like a redstone torch does: {@code updateNeighborsAt} is run on all
 * six surrounding cells (each of which then re-evaluates its own neighbours), not just on the
 * head's direct neighbours. Ordinary pistons place/remove their head with a single-order update,
 * which is why the vanilla flow leaves redstone logic two blocks away stale.
 */
public class RedstoneEndRodPistonHeadBlock extends EndRodPistonHeadBlock {
    public RedstoneEndRodPistonHeadBlock(Properties properties) {
        super(properties);
    }

    /**
     * Second-order update burst copied from {@code RedstoneTorchBlock.notifyNeighbors}: notify
     * every cell around this position so each of them pushes the change one step further.
     */
    private void pdNotifyNeighbors(Level level, BlockPos pos, BlockState state) {
        //? if >=1.21.2 {
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, null, Direction.UP);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this, ExperimentalRedstoneUtils.withFront(orientation, direction));
        }
        //?} else {
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this);
        }
        //?}
    }

    //? if <1.21.2 {
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    //?} else {
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    //?}
        this.pdNotifyNeighbors(level, pos, state);
    }

    //? if >=1.20.3 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            this.pdNotifyNeighbors(level, pos, state);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    //?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!movedByPiston && !state.is(newState.getBlock())) {
            this.pdNotifyNeighbors(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //?}

    /** The vanilla retract deletes this head with a no-update setBlock; release the burst here too. */
    @Override
    public void pdAfterHeadRemovedWithoutUpdate(Level level, BlockPos pos, BlockState state) {
        this.pdNotifyNeighbors(level, pos, state);
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
