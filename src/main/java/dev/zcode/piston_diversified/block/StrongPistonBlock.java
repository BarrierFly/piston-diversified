package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.ModPistonStructureResolver;
import dev.zcode.piston_diversified.logic.PdResolver;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 强力活塞（1~3 档）— pushes normally, but while resolving the structure every push-resistant
 * block (obsidian/bedrock family, no block entity, no piston) counts as 6/3/2 pushable blocks
 * against the 12 budget instead of failing the push. The converted blocks really do move — they
 * travel as moving pistons like everything else.
 *
 * <p>Recalculation only applies to this piston's own pushes; when another piston pushes a strong
 * piston it is still an ordinary unpushable block. No stickiness changes: slime-glued
 * unpushable blocks are converted too. Not sticky.</p>
 */
public class StrongPistonBlock extends ModPistonBaseBlock {
    private final int tier;

    public StrongPistonBlock(int tier, Properties properties) {
        super(false, properties);
        this.tier = tier;
    }

    /** 1..3: an unpushable block counts as 6/3/2 pushable blocks. */
    public int tier() {
        return this.tier;
    }

    @Override
    public Block headBlock() {
        return ModBlocks.STRONG_PISTON_HEAD;
    }

    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        return new ModPistonStructureResolver(level, pos, direction, extending, true, false, false, this.tier);
    }
}
