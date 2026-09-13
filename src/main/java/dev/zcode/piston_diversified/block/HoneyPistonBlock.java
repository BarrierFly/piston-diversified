package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 蜂蜜活塞 — a sticky piston that pulls the second cell back after a 0-tick instant retract
 * (瞬推), so fast pulses no longer displace the pushed block.
 */
public class HoneyPistonBlock extends ModPistonBaseBlock {
    public HoneyPistonBlock(Properties properties) {
        super(true, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.HONEY_PISTON_HEAD;
    }

    @Override
    protected boolean pullOnInstantRetract() {
        return true;
    }
}
