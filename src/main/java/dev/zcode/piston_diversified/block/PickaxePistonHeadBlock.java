package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Head of the 镐活塞 — carries the TOOL property so the plate shows the pickaxe the piston
 * actually carries (six plate textures).
 */
public class PickaxePistonHeadBlock extends ModPistonHeadBlock {
    public static final EnumProperty<PickaxeTool> TOOL = EnumProperty.create("tool", PickaxeTool.class);

    public PickaxePistonHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(TOOL, PickaxeTool.DEFAULT));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOOL);
    }
}
