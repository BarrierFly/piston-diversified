package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.ModPistonStructureResolver;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.PushReaction;

/**
 * 重力活塞 — pushes up like a vanilla piston, extends sideways with a head that turns into a
 * falling block ({@link GravityPistonHeadBlock}), and pushes down by telescoping through air:
 * while the head's front is air or destroy-on-push the chain keeps extending (rods, like the
 * 递推活塞, up to the 12 budget), stopping at the first real block. Once the base has no head it
 * cannot retract and ignores signals until a same-facing head (any piston's) connects again.
 * Not sticky.
 */
public class GravityPistonBlock extends ModPistonBaseBlock {
    private static final int PUSH_LIMIT = 12;

    public GravityPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.GRAVITY_PISTON_HEAD;
    }

    // ------------------------------------------------------------- headless rule

    /** Retractable only while a head (any piston's) is attached in front. */
    @Override
    protected boolean canRetract(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        BlockState frontState = level.getBlockState(pos.relative(direction));
        return frontState.getBlock() instanceof PistonHeadBlock && frontState.getValue(PistonHeadBlock.FACING) == direction;
    }

    // ------------------------------------------------------------- downward telescoping

    @Override
    protected void afterExtendExecuted(ServerLevel level, BlockPos pos, Direction direction) {
        if (direction == Direction.DOWN) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(EXTENDED) || state.getValue(FACING) != Direction.DOWN) {
            return;
        }
        if (this.hasPowerSignal(level, pos, Direction.DOWN)) {
            this.tryDownwardExtend(level, pos);
            return;
        }
        // retract chain driving (mid-chain steps schedule themselves here)
        int rods = RecursivePistonBlock.countRods(level, pos, Direction.DOWN);
        if (rods == 0) {
            // chain fully consumed: hand over to the plain vanilla retract onto the base
            level.blockEvent(pos, this, 1, Direction.DOWN.get3DDataValue());
            return;
        }
        if (this.isRetractInFlight(level, pos, rods)) {
            level.scheduleTick(pos, this, 1);
            return;
        }
        this.retractOneStep(level, pos, rods);
    }

    /**
     * One downward step: air or destroyable in front of the head → the head telescopes one more
     * cell (a rod is left behind, like the 递推活塞); any real block in front stops the chain.
     */
    private void tryDownwardExtend(ServerLevel level, BlockPos pos) {
        int rods = RecursivePistonBlock.countRods(level, pos, Direction.DOWN);
        BlockPos headPos = pos.relative(Direction.DOWN, rods + 1);
        BlockState headState = level.getBlockState(headPos);
        if (headState.is(Blocks.MOVING_PISTON)) {
            level.scheduleTick(pos, this, 1); // head still flying
            return;
        }
        if (headState.getBlock() != this.headBlock()) {
            return; // head broken or replaced: the chain stays where it is
        }

        BlockPos frontPos = headPos.below();
        BlockState frontState = level.getBlockState(frontPos);
        if (!this.canPassThrough(frontState, level, frontPos)) {
            return; // resting on a block: done
        }

        ModPistonStructureResolver resolver = new ModPistonStructureResolver(level, frontPos, Direction.DOWN, headPos, true);
        if (!resolver.resolve()) {
            return;
        }
        if (rods + 1 + resolver.totalWeight() > PUSH_LIMIT) {
            return; // telescoped out fully
        }
        if (this.moveBlocksResolved(level, headPos, Direction.DOWN, true, resolver, level.getBlockState(pos))) {
            BlockState rodState = ModBlocks.RECURSIVE_PISTON_ROD.defaultBlockState()
                .setValue(RecursivePistonRodBlock.FACING, Direction.DOWN);
            // flag 276: replacing the head must not run its removal hooks against the base
            level.setBlock(headPos, rodState, 276);
            level.updateNeighborsAt(headPos, rodState.getBlock());
            level.scheduleTick(pos, this, 2);
        }
    }

    /** Air (and replaceables) pass; destroy-on-push blocks are cleared; real blocks stop the chain. */
    private boolean canPassThrough(BlockState state, Level level, BlockPos pos) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.canBeReplaced()) {
            return true;
        }
        if (state.getPistonPushReaction() == PushReaction.DESTROY) {
            return true; // resolved into toDestroy and popped by the move
        }
        return false;
    }

    // ------------------------------------------------------------- retract with rods

    @Override
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id) {
        if (direction == Direction.DOWN && level instanceof ServerLevel serverLevel) {
            int rods = RecursivePistonBlock.countRods(level, pos, Direction.DOWN);
            if (rods > 0) {
                if (!this.isRetractInFlight(level, pos, rods)) {
                    this.retractOneStep(serverLevel, pos, rods);
                }
                // otherwise a step is animating; its scheduled tick continues the chain
                return;
            }
        }
        super.executeRetract(level, pos, direction, state, id);
    }

    private boolean isRetractInFlight(Level level, BlockPos pos, int rods) {
        return level.getBlockState(pos.relative(Direction.DOWN, rods + 1)).is(Blocks.MOVING_PISTON)
            || level.getBlockState(pos.relative(Direction.DOWN, rods)).is(Blocks.MOVING_PISTON);
    }

    /** Non-sticky mirror of the 递推 retract: the head slides onto the last rod, rod consumed. */
    private void retractOneStep(net.minecraft.server.level.ServerLevel level, BlockPos pos, int rods) {
        Direction direction = Direction.DOWN;
        BlockPos headPos = pos.relative(direction, rods + 1);
        BlockPos rodPos = headPos.above();
        BlockState headState = level.getBlockState(headPos);
        if (headState.getBlock() != this.headBlock()) {
            // head gone: consume the last rod without animation
            level.removeBlock(rodPos, false);
            level.scheduleTick(pos, this, 3);
            return;
        }

        level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 276);
        BlockState movingState = Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(MovingPistonBlock.FACING, direction)
            .setValue(MovingPistonBlock.TYPE, net.minecraft.world.level.block.state.properties.PistonType.DEFAULT);
        level.setBlock(rodPos, movingState, 276);
        // non-source: see RecursivePistonBlock — a source retract piston crashes the renderer
        net.minecraft.world.level.block.entity.BlockEntity retractBe =
            MovingPistonBlock.newMovingBlockEntity(rodPos, movingState, headState, direction, false, false);
        level.setBlockEntity(retractBe);
        level.updateNeighborsAt(rodPos, movingState.getBlock());
        level.scheduleTick(pos, this, 3);
    }
}
