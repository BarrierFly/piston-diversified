package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.ModPistonStructureResolver;
import dev.zcode.piston_diversified.logic.PistonlessPush;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 递推活塞 / 递推黏塞 — a telescoping piston: while powered, the head keeps pushing out one cell
 * at a time (the vacated head cell becomes a {@link RecursivePistonRodBlock}); when unpowered it
 * retracts onto the last rod, one cell at a time, like it retracts onto the base. Head + rods
 * count against the 12 push budget (initial extension stays vanilla: 12 pushed blocks), the
 * sticky variant counts the rods against its pull limit.
 */
public class RecursivePistonBlock extends ModPistonBaseBlock {
    /** Push budget (vanilla). The head/rod assembly plus the pushed blocks may not exceed it. */
    private static final int PUSH_LIMIT = 12;

    public RecursivePistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.RECURSIVE_STICKY_PISTON_HEAD : ModBlocks.RECURSIVE_PISTON_HEAD;
    }

    // ------------------------------------------------------------- geometry

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(EXTENDED)) {
            // thin base plate + bare rod all the way to the front face (the head block continues it)
            return Shapes.or(PdShapes.slab(facing, 0, 4), PdShapes.rod(facing, 4, 16, 4));
        }
        // at rest the head plate sits flush with the front face of this cell
        return Shapes.or(PdShapes.slab(facing, 0, 4), PdShapes.rod(facing, 4, 12, 4), PdShapes.slab(facing, 12, 16));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    // ------------------------------------------------------------- extend chain

    @Override
    protected void afterExtendExecuted(ServerLevel level, BlockPos pos, Direction direction) {
        level.scheduleTick(pos, this, 2);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(EXTENDED)) {
            return;
        }
        Direction direction = state.getValue(FACING);
        if (this.hasPowerSignal(level, pos, direction)) {
            this.tryRecursiveExtend(level, pos, direction);
        } else {
            this.continueRetract(level, pos, direction);
        }
    }

    /** Head fully out + still powered → push out one more cell (rods grow by one). */
    private void tryRecursiveExtend(Level level, BlockPos pos, Direction direction) {
        int rods = this.countRods(level, pos, direction);
        BlockPos headPos = pos.relative(direction, rods + 1);
        BlockState headCell = level.getBlockState(headPos);
        if (headCell.getBlock() == this.headBlock()) {
            ModPistonStructureResolver resolver = new ModPistonStructureResolver(
                level, headPos.relative(direction), direction, headPos, true
            );
            if (!resolver.resolve()) {
                return; // blocked in front: the assembly stays where it is
            }
            // the old head becomes a rod: it occupies one budget slot, as do all existing rods
            if (rods + 1 + resolver.totalWeight() > PUSH_LIMIT) {
                return; // telescoped out fully
            }
            if (this.moveBlocksResolved(level, headPos, direction, true, resolver, level.getBlockState(pos))) {
                BlockState rodState = ModBlocks.RECURSIVE_PISTON_ROD.defaultBlockState()
                    .setValue(RecursivePistonRodBlock.FACING, direction);
                // flag 276: replacing the head must not run its affectNeighborsAfterRemoval
                // (a fitting extended base sits behind the first rod — the piston itself)
                level.setBlock(headPos, rodState, ModPistonBaseBlock.SYNC_RETRACT);
                level.updateNeighborsAt(headPos, rodState.getBlock());
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.scheduleTick(pos, this, 2);
                }
            }
        } else if (headCell.is(Blocks.MOVING_PISTON)) {
            // the head placed by the previous step is still flying out — retry shortly
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.scheduleTick(pos, this, 1);
            }
        }
    }

    // ------------------------------------------------------------- retract chain

    @Override
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id) {
        int rods = this.countRods(level, pos, direction);
        if (rods > 0) {
            // a mid-chain animation still running: swallow the duplicate event, the tick chain
            // (scheduled by the running step) drives the rest
            if (!this.isRetractInFlight(level, pos, direction, rods)) {
                this.retractOneStep(level, pos, direction, state, rods);
            }
            return;
        }
        if (this.sticky && this.pullStillInFlight(level, pos, direction)) {
            // The chain is done but the block the last step pulled is still travelling. Retracting
            // now would classify as 瞬推 and silently drop the pull, leaving the block one cell
            // short of the head — wait for it to land and let the tick chain re-run the retract.
            this.scheduleNext(level, pos, 1);
            return;
        }
        super.executeRetract(level, pos, direction, state, id);
    }

    /** Whether the cell the sticky retract pulls from still carries an outbound moving piston. */
    private boolean pullStillInFlight(Level level, BlockPos pos, Direction direction) {
        BlockPos pullPos = this.retractPullSource(pos, direction, level.getBlockState(pos));
        return level.getBlockState(pullPos).is(Blocks.MOVING_PISTON)
            && level.getBlockEntity(pullPos) instanceof PistonMovingBlockEntity be
            && be.isExtending();
    }

    /** Called from the tick while unpowered: keep consuming rods until the base is reached. */
    private void continueRetract(Level level, BlockPos pos, Direction direction) {
        int rods = this.countRods(level, pos, direction);
        if (rods > 0) {
            if (this.isRetractInFlight(level, pos, direction, rods)) {
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.scheduleTick(pos, this, 1);
                }
                return;
            }
            this.retractOneStep(level, pos, direction, level.getBlockState(pos), rods);
        } else {
            // Wait for the last step's pull to land before handing over to the vanilla retract.
            // A retract that starts while a pulled block is still flying reads as 瞬推 (type 2),
            // which drops the pull — so the chain would always end with the block one cell short.
            if (level.getBlockState(pos.relative(direction, 2)).is(Blocks.MOVING_PISTON)) {
                this.scheduleNext(level, pos, 1);
                return;
            }
            // no rods left: the plain vanilla retract onto the base (sticky pulls apply)
            level.blockEvent(pos, this, 1, direction.get3DDataValue());
        }
    }

    /**
     * One retract step: the head slides back onto the last rod (the rod is consumed). The sticky
     * variant also pulls the structure in front of the head, rods counting against the pull limit.
     */
    private void retractOneStep(Level level, BlockPos pos, Direction direction, BlockState state, int rods) {
        BlockPos headPos = pos.relative(direction, rods + 1);
        BlockPos rodPos = headPos.relative(direction.getOpposite());
        BlockState headState = level.getBlockState(headPos);
        boolean headIsBlock = headState.getBlock() == this.headBlock();

        if (!headIsBlock) {
            // head was broken mid-chain: just consume the last rod, no animation possible
            level.removeBlock(rodPos, false);
            this.scheduleNext(level, pos, 3);
            return;
        }

        // The head must leave before the pull can resolve. A piston head is push-resistant, so a
        // resolver that walks into it fails outright — resolving first made the sticky pull a
        // silent no-op and the arm retracted empty. Flag 276 (no updates): the head's removal
        // hook would otherwise destroy the extended base behind the first rod.
        level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 276);

        if (this.sticky && level instanceof ServerLevel serverLevel) {
            int budget = PUSH_LIMIT - rods;
            // resolver rooted one cell in front of the head, pulling backwards
            ModPistonStructureResolver resolver = new ModPistonStructureResolver(
                level, headPos.relative(direction), direction.getOpposite(), headPos, true
            );
            if (resolver.resolve() && resolver.totalWeight() <= budget) {
                PistonlessPush.execute(
                    serverLevel, headPos.relative(direction, 2), direction.getOpposite(), true, false, false
                );
            }
        }

        // The head slides back onto the rod. A non-source moving piston still renders the head
        // sliding from headPos onto rodPos — a *source* one would make the vanilla renderer treat
        // the moved head state as a piston base and crash on setValue(EXTENDED).
        BlockState movingState = Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(MovingPistonBlock.FACING, direction)
            .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
        level.setBlock(rodPos, movingState, ModPistonBaseBlock.SYNC_RETRACT);
        net.minecraft.world.level.block.entity.BlockEntity retractBe =
            MovingPistonBlock.newMovingBlockEntity(rodPos, movingState, headState, direction, false, false);
        level.setBlockEntity(retractBe);
        level.updateNeighborsAt(rodPos, movingState.getBlock());

        this.scheduleNext(level, pos, 3);
    }

    /** Schedules the next chain step; a no-op on the client, which never drives the chain. */
    private void scheduleNext(Level level, BlockPos pos, int delay) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(pos, this, delay);
        }
    }

    /** A previous step's animation is still running in front of us. */
    private boolean isRetractInFlight(Level level, BlockPos pos, Direction direction, int rods) {
        return level.getBlockState(pos.relative(direction, rods + 1)).is(Blocks.MOVING_PISTON)
            || level.getBlockState(pos.relative(direction, Math.max(1, rods))).is(Blocks.MOVING_PISTON);
    }

    // ------------------------------------------------------------- helpers

    /** Number of consecutive rod cells directly in front of the base (shared with the gravity piston). */
    static int countRods(Level level, BlockPos pos, Direction direction) {
        int rods = 0;
        while (rods < PUSH_LIMIT) {
            BlockState state = level.getBlockState(pos.relative(direction, rods + 1));
            if (state.getBlock() instanceof RecursivePistonRodBlock && state.getValue(RecursivePistonRodBlock.FACING) == direction) {
                rods++;
            } else {
                break;
            }
        }
        return rods;
    }
}
