package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.PdResolver;
import dev.zcode.piston_diversified.logic.PdUpdateBatch;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 全更新置后活塞 / 全更新置后黏塞 — a normal piston whose move defers every update it emits
 * (NC, PP and, on 1.21+, the extra update) to a single FIFO replay at the stage tail.
 *
 * <p>The batch is opened around {@link #moveBlocksResolved} (the push body) and around
 * {@link #executeRetract} (which also carries the retract path's immediate base-cell NC+PP and the
 * head clearing). Because a nested {@code begin} only joins, a sticky pull's own
 * {@code moveBlocksResolved} does not replay early: everything lands after the last write of the
 * stage. The replay itself runs before the base's {@code EXTENDED} flip (flag 67, which keeps its
 * vanilla timing) because that flip happens after {@code moveBlocksResolved} returns.</p>
 *
 * <p>The interception lives in {@code PdUpdateBatch} + {@code ServerLevelMixin} /
 * {@code BlockStateBaseMixin}; it is inert unless a batch is open, so no other piston is affected.</p>
 */
public class DeferredPistonBlock extends ModPistonBaseBlock {
    public DeferredPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.DEFERRED_STICKY_PISTON_HEAD : ModBlocks.DEFERRED_PISTON_HEAD;
    }

    @Override
    protected boolean moveBlocksResolved(Level level, BlockPos pos, Direction facing, boolean extending, PdResolver resolver, BlockState baseState) {
        boolean outer = PdUpdateBatch.begin(level);
        try {
            return super.moveBlocksResolved(level, pos, facing, extending, resolver, baseState);
        } finally {
            PdUpdateBatch.end(level, outer);
        }
    }

    @Override
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id, Boolean pullOverride) {
        boolean outer = PdUpdateBatch.begin(level);
        try {
            super.executeRetract(level, pos, direction, state, id, pullOverride);
        } finally {
            PdUpdateBatch.end(level, outer);
        }
    }
}
