package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 头颅活塞 — a normal piston whose head carries an independent POWERED state and switches
 * between a calm and an excited face while active.
 */
public class SkullPistonBlock extends ModPistonBaseBlock {
    public SkullPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.SKULL_PISTON_HEAD;
    }

    @Override
    protected BlockState customizeHeadState(BlockState headState, Direction direction) {
        return headState.setValue(SkullPistonHeadBlock.POWERED, true);
    }
}
