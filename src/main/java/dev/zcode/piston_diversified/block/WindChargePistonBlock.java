package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.WindChargeEffect;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 风弹活塞 — pushes no blocks; once the head is fully out it applies the wind charge's
 * trigger interaction to the element in front of the head.
 */
public class WindChargePistonBlock extends ModPistonBaseBlock {
    private static final int EFFECT_DELAY = 4;

    public WindChargePistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.WIND_CHARGE_PISTON_HEAD;
    }

    @Override
    protected boolean canPushBlocks() {
        return false;
    }

    @Override
    protected void afterExtendExecuted(ServerLevel level, BlockPos pos, Direction direction) {
        level.scheduleTick(pos, this, EFFECT_DELAY);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        if (state.getValue(EXTENDED)
            && level.getBlockState(pos.relative(direction)).getBlock() instanceof PistonHeadBlock) {
            WindChargeEffect.trigger(level, pos.relative(direction, 2));
        }
    }
}
