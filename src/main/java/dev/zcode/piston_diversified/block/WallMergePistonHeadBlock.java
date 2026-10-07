package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Head of the 墙并活塞 family — the arm is a wall-centre-post (8×8) instead of the vanilla 4×4
 * rod, so side-by-side same-facing heads form a connected wall.
 *
 * <p>The post respects {@code SHORT}: nothing places this head with {@code SHORT=true} (heads only
 * ever get the default {@code SHORT=false} state), so the long post reaching back into the base
 * cell is the norm — but a head left standing on its own shrinks to the short model instead of
 * keeping 8px of invisible collision and a post visually disconnected from the base.</p>
 */
public class WallMergePistonHeadBlock extends ModPistonHeadBlock {
    public WallMergePistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return Shapes.or(PdShapes.slab(facing, 0, 4),
            PdShapes.rod(facing, 4, state.getValue(SHORT) ? 12 : 20, 8));
    }
}
