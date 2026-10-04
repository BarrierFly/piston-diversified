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
        Direction bend = this.bendOf(level, pos, direction);
        if (bend == null) {
            return super.resolveExtend(level, pos, direction);
        }
        return new ModPistonStructureResolver(level, pos.relative(direction), bend, pos, true).resolve();
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        Direction bend = state.getValue(BEND);
        if (bend.getAxis() == direction.getAxis() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState frontState = level.getBlockState(pos.relative(direction));
        if (frontState.isAir()) {
            return false; // nothing to push: vanilla empty push gives the bent head
        }
        // root the sideways resolver so its start cell is the piston's front cell
        boolean pushed = PistonlessPush.execute(
            serverLevel, pos.relative(direction).relative(bend.getOpposite()), bend, true, false, false
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

    @Override
    protected BlockPos retractPullSource(BlockPos pos, Direction direction, BlockState state) {
        // the sticky pull grabs the cell in front of the bent plate, not piston+2
        Direction bend = state.getBlock() instanceof TurnPushPistonBlock && state.hasProperty(BEND)
            ? state.getValue(BEND)
            : this.defaultBend(direction);
        return pos.relative(direction).relative(bend);
    }

    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending) {
        if (extending) {
            return null;
        }
        Direction bend = this.bendOf(level, pos, direction);
        if (bend == null || bend.getAxis() == direction.getAxis()) {
            return null;
        }
        return new ModPistonStructureResolver(
            level, pos.relative(direction).relative(bend), direction.getOpposite(), pos, true
        );
    }

    /**
     * The bend of whatever bend-carrying block sits at pos (the base itself; also called for the
     * just-placed base during placement edge cases). Falls back to the facing-derived default.
     */
    private Direction bendOf(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TurnPushPistonBlock && state.hasProperty(BEND)) {
            Direction bend = state.getValue(BEND);
            if (bend.getAxis() != direction.getAxis()) {
                return bend;
            }
        }
        return this.defaultBend(direction);
    }

    private Direction defaultBend(Direction direction) {
        return direction.getAxis().isHorizontal() ? direction.getClockWise() : Direction.NORTH;
    }
}
