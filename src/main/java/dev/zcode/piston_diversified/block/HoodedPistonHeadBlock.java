package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Head of the 带罩(黏)活塞. The hood is a shell whose collision box covers the whole cell (判定箱
 * 与视觉模型同步: 头=罩外壳、占满整格). The four side rails of the shell are what the sticky
 * variant makes sticky ({@link #pdIsSticky()}, read by {@code HoodStickiness}).
 */
public class HoodedPistonHeadBlock extends ModPistonHeadBlock {
    private final boolean sticky;

    public HoodedPistonHeadBlock(boolean sticky, Properties properties) {
        super(properties);
        this.sticky = sticky;
    }

    /** Whether the hood's five outer faces are sticky (带罩黏塞). */
    public boolean pdIsSticky() {
        return this.sticky;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
