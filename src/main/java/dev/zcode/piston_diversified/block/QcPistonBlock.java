package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 随朝向QC活塞 / 随朝向QC黏塞 — quasi-connectivity reads the cell in front of the piston
 * (the head cell) instead of the cell above it.
 */
public class QcPistonBlock extends ModPistonBaseBlock {
    public QcPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.QC_STICKY_PISTON_HEAD : ModBlocks.QC_PISTON_HEAD;
    }

    @Override
    protected BlockPos qcCell(BlockPos pos, Direction facing) {
        return pos.relative(facing);
    }
}
