package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

import dev.zcode.piston_diversified.registry.ModBlocks;

/**
 * 静音活塞 — behaves like a normal piston but never plays extend/retract sounds and never
 * emits the activation game events, so sculk sensors stay silent.
 */
public class SilentPistonBlock extends ModPistonBaseBlock {
    public SilentPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.SILENT_PISTON_HEAD;
    }

    @Override
    protected boolean isSilent() {
        return true;
    }
}
