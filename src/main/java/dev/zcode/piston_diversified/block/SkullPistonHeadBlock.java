package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Head of the 头颅活塞 — its POWERED face follows redstone applied at the head's own
 * position (any side), independent of the base piston.
 */
public class SkullPistonHeadBlock extends ModPistonHeadBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SkullPistonHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    //? if <1.21.2 {
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 2);
        }
    }
    //?} else {
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 2);
        }
    }
    //?}

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean bl = level.hasNeighborSignal(pos);
        if (bl != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, bl), 2);
        }
    }
}
