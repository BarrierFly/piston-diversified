package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 循环型活塞 — whenever the head finishes extending, schedules a retract; after the retract
 * finishes, re-extends while the signal is present. The self-scheduled retract only fires after
 * the head has solidified (no 瞬推), giving a stable oscillation while powered.
 */
public class LoopPistonBlock extends ModPistonBaseBlock {
    /** Delay from an extend/retract event to the follow-up event, in game ticks. */
    private static final int CYCLE_DELAY = 4;

    public LoopPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.LOOP_PISTON_HEAD;
    }

    @Override
    protected boolean cancelRetractIfPowered() {
        return false;
    }

    @Override
    protected void afterExtendExecuted(ServerLevel level, BlockPos pos, Direction direction) {
        level.scheduleTick(pos, this, CYCLE_DELAY);
    }

    @Override
    protected void afterRetractExecuted(ServerLevel level, BlockPos pos, Direction direction) {
        level.scheduleTick(pos, this, CYCLE_DELAY);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        if (state.getValue(EXTENDED)) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof PistonHeadBlock) {
                level.blockEvent(pos, this, 1, direction.get3DDataValue());
            } else {
                // Head not solid yet (or broken): retry shortly.
                level.scheduleTick(pos, this, 2);
            }
        } else if (this.hasPowerSignal(level, pos, direction)) {
            level.blockEvent(pos, this, 0, direction.get3DDataValue());
        }
    }
}
