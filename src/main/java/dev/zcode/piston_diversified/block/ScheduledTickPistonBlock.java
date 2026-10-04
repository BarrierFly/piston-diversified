package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 0t计划刻活塞 / 0t计划刻黏塞 — replaces the block-event channel with 0gt scheduled ticks. The
 * neighbor update only schedules; when the tick runs, the extend/retract decision is re-derived
 * from the live signal (the tick carries no event id or direction), which produces the
 * characteristic 0-tick quirks: same-tick power cycles, signal-dependent retractions and the
 * like. The in-flight (瞬推) retract detection also happens at execution time.
 */
public class ScheduledTickPistonBlock extends ModPistonBaseBlock {
    public ScheduledTickPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.ST_STICKY_PISTON_HEAD : ModBlocks.ST_PISTON_HEAD;
    }

    @Override
    protected void sendExtendEvent(Level level, BlockPos pos, Direction direction) {
        level.scheduleTick(pos, this, 0);
    }

    @Override
    protected void sendRetractEvent(Level level, BlockPos pos, Direction direction, int type) {
        level.scheduleTick(pos, this, 0);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        boolean bl = this.hasPowerSignal(level, pos, direction);
        if (bl && !state.getValue(EXTENDED)) {
            // mirror the vanilla extend event's pre-resolve, re-read at execution time
            if (this.resolveExtend(level, pos, direction) || this.extendEventWithoutResolve(level, pos, direction)) {
                this.executeExtend(level, pos, direction, state);
            }
        } else if (!bl && state.getValue(EXTENDED)) {
            int type = this.retractType(level, pos, direction, state);
            this.executeRetract(level, pos, direction, state, type);
        }
        // bl && EXTENDED: a stale retract tick simply re-arms, like cancelRetractIfPowered
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        return true; // no block events are used by this variant
    }
}
