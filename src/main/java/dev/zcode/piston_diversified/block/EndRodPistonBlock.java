package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 活塞端杆 — no plate; the piston arm is styled as an end rod. The retracted base emits light 15,
 * the head is always lit at 15. Collision: base slab + 4px rod, head is a bare rod.
 */
public class EndRodPistonBlock extends ModPistonBaseBlock {
    public EndRodPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.END_ROD_PISTON_HEAD;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return Shapes.or(PdShapes.slab(facing, 12, 16), PdShapes.rod(facing, 4, 16, 4));
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
