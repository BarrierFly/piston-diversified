package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.ModPistonStructureResolver;
import dev.zcode.piston_diversified.logic.PdResolver;
import dev.zcode.piston_diversified.logic.PistonlessPush;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 拐推活塞 / 拐推黏塞 — pushes the structure in front of it SIDEWAYS (the placement-time bend
 * direction, perpendicular to the push direction, closest to the player) and ends up with a bent
 * head ({@link TurnPushPistonHeadBlock}). The extend/retract animations stay vanilla (straight
 * head, straight rod); only the resolved push/pull direction differs. The sticky variant pulls
 * the block(s) in front of the bent plate back when retracting.
 */
public class TurnPushPistonBlock extends ModPistonBaseBlock {
    public static final EnumProperty<Direction> BEND = EnumProperty.create("bend", Direction.class);

    public TurnPushPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(BEND, Direction.NORTH));
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.TURN_PUSH_STICKY_PISTON_HEAD : ModBlocks.TURN_PUSH_PISTON_HEAD;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BEND);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction facing = state.getValue(FACING);
        return state.setValue(BEND, this.pickBend(context, facing));
    }

    /**
     * The bend is the horizontal direction perpendicular to the push direction that lies closest
     * to the player (same spirit as the crafter's left/right placement).
     */
    private Direction pickBend(BlockPlaceContext context, Direction facing) {
        Direction[] candidates;
        if (facing.getAxis().isHorizontal()) {
            candidates = new Direction[] {facing.getClockWise(), facing.getCounterClockWise()};
        } else {
            candidates = new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        }
        LivingEntity player = context.getPlayer();
        if (player == null) {
            return candidates[0];
        }
        Vec3 towards = player.position().add(0, 0.5, 0).subtract(Vec3.atCenterOf(context.getClickedPos()));
        Direction best = candidates[0];
        double bestDot = -Double.MAX_VALUE;
        for (Direction candidate : candidates) {
            double dot = towards.x * candidate.getStepX() + towards.y * candidate.getStepY() + towards.z * candidate.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation).setValue(BEND, rotation.rotate(state.getValue(BEND)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror).setValue(BEND, mirror.mirror(state.getValue(BEND)));
    }

    // ------------------------------------------------------------- extend: sideways push

    /**
     * The pre-resolve must answer "can the front structure be pushed SIDEWAYS", not forward — a
     * front block that is free ahead but blocked sideways would otherwise never raise the event.
     */
    @Override
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        // the piston state is still in place during the pre-check; the sideways pre-resolve must
        // answer "can the front structure be pushed along the bend" (not forward)
        Direction bend = this.bendOf(direction, level.getBlockState(pos));
        if (bend.getAxis() == direction.getAxis()) {
            return super.resolveExtend(level, pos, direction);
        }
        return new ModPistonStructureResolver(level, pos.relative(direction), bend, pos, true).resolve();
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        Direction bend = state.getValue(BEND);
        if (bend.getAxis() == direction.getAxis()) {
            return false;
        }
        BlockPos frontPos = pos.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        if (frontState.isAir()) {
            return false; // nothing to push: vanilla empty push gives the bent head
        }
        if (level.isClientSide()) {
            // Replay the sideways push locally. The piston fires a vanilla block event, so this
            // runs before the server's block updates and is the only thing that builds the moving
            // pistons with progress 0 — the difference between watching the front structure slide
            // away sideways and seeing it snap into place. Re-resolving the bend push here is
            // enough: the server's updates arrive right after and overwrite whatever we guessed.
            boolean replayed = PistonlessPush.execute(
                level, pos.relative(direction).relative(bend.getOpposite()), bend, true, false, false
            ).success();
            if (!replayed) {
                // the client resolved something the server would not have pushed — emptying the
                // front cell is still the right guess: the server never pushes it forward
                level.setBlock(frontPos, Blocks.AIR.defaultBlockState(), 2);
            }
            return false; // the front cell is clear — the vanilla move slides the (bent) head out
        }
        // root the sideways resolver so its start cell is the piston's front cell
        boolean pushed = PistonlessPush.execute(
            (ServerLevel) level, pos.relative(direction).relative(bend.getOpposite()), bend, true, false, false
        ).success();
        if (!pushed) {
            return true; // blocked sideways: nothing happens at all
        }
        return false; // the front cell is clear — the vanilla move slides the (bent) head out
    }

    @Override
    protected BlockState customizeHeadState(BlockState headState, Direction direction, BlockState baseState) {
        return headState.setValue(TurnPushPistonHeadBlock.BEND, baseState.getValue(BEND));
    }

    // ------------------------------------------------------------- retract: bent pull

    /** The head's plate — and the cell it glued itself to — is offset by the bend from the push axis. */
    @Override
    protected BlockPos retractPullSource(BlockPos pos, Direction direction, BlockState state) {
        // the sticky pull grabs the cell in front of the bent plate, not piston+2
        Direction bend = this.bendOf(direction, state);
        return pos.relative(direction).relative(bend);
    }

    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        if (extending || baseState == null) {
            return null;
        }
        // read the bend from the captured base state — the base cell is a moving piston during
        // the retract move, so the level no longer holds the piston state
        Direction bend = this.bendOf(direction, baseState);
        if (bend.getAxis() == direction.getAxis()) {
            return null;
        }
        // Pull along the bend, not along the piston axis: the block glued to the bent plate sits
        // at head+bend and must travel back to the head cell (head+bend-bend). Using the opposite
        // of `direction` here would drag it a cell sideways onto the base instead — 规划 §三.14
        // "拐推黏塞向拐弯方向非共线拉回" reads as "pull it back along the bend".
        return new ModPistonStructureResolver(
            level, pos.relative(direction).relative(bend), bend.getOpposite(), pos, true
        );
    }

    /** The bend from a state, or the placement default when the state cannot carry one. */
    private Direction bendOf(Direction direction, BlockState state) {
        return state.getBlock() instanceof TurnPushPistonBlock && state.hasProperty(BEND)
            ? state.getValue(BEND)
            : this.defaultBend(direction);
    }

    private Direction defaultBend(Direction direction) {
        return direction.getAxis().isHorizontal() ? direction.getClockWise() : Direction.NORTH;
    }
}
