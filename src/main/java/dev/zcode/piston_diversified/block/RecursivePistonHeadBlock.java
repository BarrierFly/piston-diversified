package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Head of the 递推活塞 family — fits an extended recursive base or a recursive rod behind it
 * (a vanilla head only fits piston bases, so the telescoped head would instantly pop off).
 */
public class RecursivePistonHeadBlock extends ModPistonHeadBlock {
    public RecursivePistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isFittingBase(BlockState headState, BlockState baseState) {
        if (super.isFittingBase(headState, baseState) && baseState.getBlock() instanceof RecursivePistonBlock) {
            return true;
        }
        return baseState.getBlock() instanceof RecursivePistonRodBlock
            && baseState.getValue(RecursivePistonRodBlock.FACING) == headState.getValue(FACING);
    }
}
