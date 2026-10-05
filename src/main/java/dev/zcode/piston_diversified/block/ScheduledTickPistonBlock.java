package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 0t计划刻活塞 / 0t计划刻黏塞 — replaces the block-event channel with 0gt scheduled ticks. The
 * neighbor update only schedules; when the tick runs, the extend/retract decision is re-derived
 * from the live signal (the tick carries no event id or direction), which produces the
 * characteristic 0-tick quirks: same-tick power cycles, signal-dependent retractions and the
 * like. The in-flight (瞬推) retract detection also happens at execution time.
 *
 * <p>The tick only <em>decides</em>; the move itself is handed to the ordinary block event, which
 * {@code ServerLevel.runBlockEvents} flushes at the end of the tick. That ordering is not
 * cosmetic — it is what makes the client animate the piston at all. Block events are flushed
 * after {@code chunkSource.tick()}, so a world change made here would reach the client as a plain
 * block update before the event: the moving piston would only ever be seen at {@code progressO}
 * (already one tick in) and the piston snapped instead of sliding. Reached from the block event,
 * the event packet goes out first, the client replays the move locally with progress 0, and the
 * server's updates arrive one tick later to confirm it — exactly how a vanilla piston works.
 * See {@link ModPistonBaseBlock#MIRROR_EXTEND} for the same requirement.</p>
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
                // the decision is made; the block event carries it out (and mirrors it to the client)
                level.blockEvent(pos, this, PistonBaseBlock.TRIGGER_EXTEND, direction.get3DDataValue());
            }
        } else if (!bl && state.getValue(EXTENDED)) {
            int type = this.retractType(level, pos, direction, state);
            level.blockEvent(pos, this, type, direction.get3DDataValue());
        }
        // bl && EXTENDED: a stale retract tick simply re-arms, like cancelRetractIfPowered
    }

    /**
     * Server side, the decision is already final: vanilla re-checks the signal here and would
     * swallow exactly the same-tick cases this variant exists for (a powered retract cancelled,
     * an unpowered extend refused). The client keeps the vanilla behaviour, which is only the
     * re-check-free branch anyway.
     */
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (level.isClientSide()) {
            return super.triggerEvent(state, level, pos, id, param);
        }
        Direction direction = state.getValue(FACING);
        if (id == PistonBaseBlock.TRIGGER_EXTEND) {
            this.executeExtend(level, pos, direction, state);
        } else if (id == PistonBaseBlock.TRIGGER_CONTRACT || id == PistonBaseBlock.TRIGGER_DROP) {
            this.executeRetract(level, pos, direction, state, id);
        }
        // true: the client has to replay this move, so the event must be broadcast even when
        // nothing could be pushed
        return true;
    }
}
