package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 快速活塞 / 快速黏塞 — the moving pistons it creates are flagged "fast" and convert to their
 * final block the same tick the movement completes instead of one tick later.
 */
public class FastPistonBlock extends ModPistonBaseBlock {
    public FastPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.FAST_STICKY_PISTON_HEAD : ModBlocks.FAST_PISTON_HEAD;
    }

    @Override
    protected boolean marksMovingPistonsFast() {
        return true;
    }
}
