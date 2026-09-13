package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Head of the 头颅活塞 — POWERED drives the excited face texture. */
public class SkullPistonHeadBlock extends ModPistonHeadBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SkullPistonHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }
}
