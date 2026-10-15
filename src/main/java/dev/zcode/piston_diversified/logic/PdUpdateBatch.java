package dev.zcode.piston_diversified.logic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}

/**
 * 全更新置后(黏)活塞: collects every regular (NC), shape (PP) and extra update a piston's move
 * emits, in emission order, and replays them as one FIFO batch at the stage tail.
 *
 * <p>Vanilla already suppresses the direct NC of its own writes (flags 18/68/324/82) and re-emits
 * most updates at the tail of {@code moveBlocks}; what it does <em>not</em> defer are the shape
 * triple a moving-piston placement emits, the retract path's immediate NC+PP, and (1.21+) the
 * implicit {@code affectNeighborsAfterRemoval} inside the chunk write. Those are caught by the
 * mixins ({@code ServerLevelMixin}, {@code BlockStateBaseMixin}) while a batch is active: instead
 * of dispatching, they append here. {@code DeferredPistonBlock} opens the batch around its move
 * and replays it once the stage's writes are done.</p>
 *
 * <p>The batch is scoped to a single event execution on a server level (thread local, gated by
 * {@link #isActive()}); the client never batches. Replay is notification-style: NC goes back
 * through {@code updateNeighborsAt}, PP re-runs the state's shape update against the <em>live</em>
 * state, and the extra update re-runs {@code affectNeighborsAfterRemoval} on the captured state.</p>
 */
public final class PdUpdateBatch {
    public enum Kind { NC, PP_NEIGHBOUR, PP_INDIRECT, EXTRA }

    private record Entry(Kind kind, BlockPos pos, Block block, BlockState state, Object orientation, boolean movedByPiston) {
    }

    private static final ThreadLocal<PdUpdateBatch> ACTIVE = new ThreadLocal<>();

    private final List<Entry> entries = new ArrayList<>();

    private PdUpdateBatch() {
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    /** Opens a batch (or joins the one already open). Returns true only for the outermost caller. */
    public static boolean begin(Level level) {
        if (level.isClientSide()) {
            return false;
        }
        if (ACTIVE.get() != null) {
            return false;
        }
        ACTIVE.set(new PdUpdateBatch());
        return true;
    }

    /** Nested calls no-op; the outermost caller replays the whole batch after clearing the flag. */
    public static void end(Level level, boolean outer) {
        if (!outer) {
            return;
        }
        PdUpdateBatch batch = ACTIVE.get();
        ACTIVE.remove();
        if (batch == null) {
            return;
        }
        for (Entry entry : batch.entries) {
            replay(level, entry);
        }
    }

    public static void recordNC(BlockPos pos, Block block, Object orientation) {
        PdUpdateBatch batch = ACTIVE.get();
        if (batch != null) {
            batch.entries.add(new Entry(Kind.NC, pos, block, null, orientation, false));
        }
    }

    public static void recordPP(Kind kind, BlockPos pos) {
        PdUpdateBatch batch = ACTIVE.get();
        if (batch != null) {
            batch.entries.add(new Entry(kind, pos, null, null, null, false));
        }
    }

    public static void recordExtra(BlockPos pos, BlockState state, boolean movedByPiston) {
        PdUpdateBatch batch = ACTIVE.get();
        if (batch != null) {
            batch.entries.add(new Entry(Kind.EXTRA, pos, null, state, null, movedByPiston));
        }
    }

    private static void replay(Level level, Entry entry) {
        switch (entry.kind()) {
            case NC -> {
                //? if >=1.21.2 {
                level.updateNeighborsAt(entry.pos(), entry.block(), (Orientation) entry.orientation());
                //?} else {
                level.updateNeighborsAt(entry.pos(), entry.block());
                //?}
            }
            case PP_NEIGHBOUR -> level.getBlockState(entry.pos()).updateNeighbourShapes(level, entry.pos(), 2);
            case PP_INDIRECT -> level.getBlockState(entry.pos()).updateIndirectNeighbourShapes(level, entry.pos(), 2);
            case EXTRA -> {
                //? if >=1.20.3 {
                if (level instanceof ServerLevel serverLevel) {
                    entry.state().affectNeighborsAfterRemoval(serverLevel, entry.pos(), entry.movedByPiston());
                }
                //?}
            }
        }
    }
}
