package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 连锁型活塞 / 连锁型黏塞 — counts as powered while a piston head anywhere adjacent points at it,
 * so other pistons extending towards it activate it as well.
 */
public class ChainPistonBlock extends ModPistonBaseBlock {
    public ChainPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.CHAIN_STICKY_PISTON_HEAD : ModBlocks.CHAIN_PISTON_HEAD;
    }

    @Override
    protected boolean extraPowerSignal(Level level, BlockPos pos, Direction facing) {
        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof PistonHeadBlock
                && neighborState.getValue(PistonHeadBlock.FACING) == side.getOpposite()) {
                return true;
            }
        }

        return false;
    }
}
