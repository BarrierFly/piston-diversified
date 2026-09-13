package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 头颅活塞 — a normal piston whose head reacts to redstone at its own position
 * (independent of the base; retraction timing stays with the base).
 */
public class SkullPistonBlock extends ModPistonBaseBlock {
    public SkullPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.SKULL_PISTON_HEAD;
    }
}
