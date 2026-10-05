package dev.zcode.piston_diversified.logic;

import dev.zcode.piston_diversified.PdGamerules;
import dev.zcode.piston_diversified.block.ModPistonBaseBlock;
import dev.zcode.piston_diversified.duck.PistonDuck;
import dev.zcode.piston_diversified.logic.PotatoStructureResolver.Member;
import dev.zcode.piston_diversified.logic.PotatoStructureResolver.MemberKind;
import dev.zcode.piston_diversified.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Executes a 马铃薯活塞 push: the selected structure travels one cell as the mod's own moving
 * pistons (2gt, like a vanilla piston), first cells dropped, water rules honoured. Also runs the
 * recursive piston-less push that keeps a flying structure airborne (悬浮飞行): the new front
 * structure is intersected with the recorded one, the front cell's destroy-on-push block is
 * cleared, and the result pushes on — the fresh leading cell's landing registers the next event.
 *
 * <p>Runs on the client too: the piston fires a vanilla block event, so the client replays the
 * whole move here and builds the moving pistons with progress 0 — the only way it gets to watch
 * the slide instead of the blocks snapping into their final cells. Everything the server alone
 * owns (drops, game events, the flight record) is skipped there; the server's block updates
 * arrive right after and replace whatever the client guessed.</p>
 */
public final class PotatoPushLogic {
    private PotatoPushLogic() {
    }

    /**
     * Moves {@code members} one cell along {@code pushDirection} and clears {@code toDestroy}.
     * The record travels on the leading cell (the one closest to the previous head) and marks it
     * primary, which is what schedules the next flight step when it lands.
     *
 * <p>Refuses the whole push ({@code false}, nothing touched) when any member's destination cell
     * still holds a block that is not itself moving. The structure is not a straight line, so a
     *      side member can be aimed at a cell the resolver never claimed — pushing anyway would
     *      overwrite that block with a moving piston and destroy it outright, with no drop. That is how
     *      unpushable blocks went missing mid-flight.</p>
     */
    public static boolean executePush(Level level, List<Member> members, List<BlockPos> toDestroy,
                                      Direction pushDirection, long[] record) {
        if (!canMove(level, members, toDestroy, pushDirection)) {
            return false;
        }
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            BlockPos destroyPos = toDestroy.get(i);
            BlockState destroyState = level.getBlockState(destroyPos);
            BlockEntity be = destroyState.hasBlockEntity() ? level.getBlockEntity(destroyPos) : null;
            Block.dropResources(destroyState, level, destroyPos, be);
            level.setBlock(destroyPos, Blocks.AIR.defaultBlockState(), 18);
            level.gameEvent(GameEvent.BLOCK_DESTROY, destroyPos, GameEvent.Context.of(destroyState));
        }
        if (members.isEmpty()) {
            return true;
        }

        BlockPos origin = members.get(0).pos();
        net.minecraft.world.level.material.FluidState fluidOrigin = level.getFluidState(origin);
        Set<BlockPos> sourceCells = new HashSet<>();
        java.util.Map<BlockPos, MemberKind> kinds = new java.util.HashMap<>();
        List<BlockState> carried = new ArrayList<>(members.size());
        for (Member member : members) {
            sourceCells.add(member.pos());
            kinds.put(member.pos(), member.kind());
            BlockState moved = switch (member.kind()) {
                case NORMAL -> member.state();
                case WATERLOGGED -> member.state().setValue(BlockStateProperties.WATERLOGGED, false);
                case FLUID -> member.state().getFluidState().createLegacyBlock();
            };
            carried.add(moved);
        }

        // place the moving pistons from the far end backwards, so each target is free
        for (int i = members.size() - 1; i >= 0; i--) {
            Member member = members.get(i);
            BlockPos target = member.pos().relative(pushDirection);
            sourceCells.remove(target);
            BlockState movingState = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, pushDirection);
            level.setBlock(target, movingState, ModPistonBaseBlock.SYNC_MOVING_PISTON);
            // the vanilla moving-piston entity carries the flight record on the mod's duck, so
            // the whole movement/landing path stays vanilla
            net.minecraft.world.level.block.entity.BlockEntity be = MovingPistonBlock.newMovingBlockEntity(
                target, movingState, carried.get(i), pushDirection, true, false
            );
            level.setBlockEntity(be);
            if (be instanceof PistonDuck duck) {
                if (!level.isClientSide()) {
                    duck.pistonDiversified$setFlight(record, i == 0,
                        members.get(0).kind() == MemberKind.WATERLOGGED && !fluidOrigin.isEmpty());
                }
                // nothing replays a flight step on the client, so this moving piston only exists
                // there if the server sends its block entity (see PistonMovingBlockEntityMixin)
                duck.pistonDiversified$setNeedsClientSync(!level.isClientSide());
            }
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos sourcePos : sourceCells) {
            if (kinds.get(sourcePos) == MemberKind.WATERLOGGED) {
                continue; // 原位残留的水保留
            }
            level.setBlock(sourcePos, air, 82);
        }

        for (BlockPos sourcePos : sourceCells) {
            if (level.getBlockState(sourcePos).isAir()) {
                air.updateNeighbourShapes(level, sourcePos, 2);
                air.updateIndirectNeighbourShapes(level, sourcePos, 2);
            }
        }

        for (Member member : members) {
            BlockPos target = member.pos().relative(pushDirection);
            level.updateNeighborsAt(target, Blocks.MOVING_PISTON);
            level.updateNeighborsAt(member.pos(), Blocks.AIR);
        }
        return true;
    }

    /**
     * Whether every member's destination is either free or occupied by another member that is
     * vacating it in the same step. Cells listed in {@code toDestroy} are cleared first, so they
     * count as free. A destination holding anything else means the structure as selected cannot
     * actually move one cell — refuse rather than overwrite.
     */
    private static boolean canMove(Level level, List<Member> members, List<BlockPos> toDestroy,
                                   Direction pushDirection) {
        Set<BlockPos> vacated = new HashSet<>();
        for (Member member : members) {
            vacated.add(member.pos());
        }
        vacated.addAll(toDestroy);
        for (Member member : members) {
            BlockPos target = member.pos().relative(pushDirection);
            if (vacated.contains(target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (!targetState.isAir() && !targetState.canBeReplaced() && targetState.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * One flight step (悬浮飞行): re-select the structure as if a virtual potato piston stood
     * behind the landed cell, intersect it with the record (相对位置一致才保留), clear the front
     * cell if it is destroy-on-push, and push the remainder on.
     */
    public static boolean flightPush(ServerLevel level, PotatoFlightQueue.Event event) {
        BlockPos pos = event.pos();
        Direction direction = event.direction();
        BlockState landed = level.getBlockState(pos);
        String landedId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(landed.getBlock()).toString();
        if (!landedId.equals(event.expectedBlock())) {
            return false; // the landed cell is no longer what we pushed: stop here
        }

        BlockPos virtualPiston = pos.relative(direction.getOpposite());
        PotatoStructureResolver resolver =
            new PotatoStructureResolver(level, virtualPiston, direction, PdGamerules.potatoPushLimit(level));
        if (!resolver.resolve(pos)) {
            return false;
        }

        Set<BlockPos> recorded = new HashSet<>();
        for (long packed : event.record()) {
            recorded.add(PotatoStructureResolver.unpackRelative(pos, packed));
        }
        List<Member> intersection = new ArrayList<>();
        for (Member member : resolver.getMembers()) {
            if (recorded.contains(member.pos())) {
                intersection.add(member);
            }
        }
        if (intersection.isEmpty()) {
            return false;
        }

        List<BlockPos> destroy = new ArrayList<>(resolver.getToDestroy());
        BlockPos frontMost = intersection.get(0).pos();
        for (Member member : intersection) {
            if (member.pos().getX() * direction.getStepX() + member.pos().getY() * direction.getStepY()
                + member.pos().getZ() * direction.getStepZ()
                > frontMost.getX() * direction.getStepX() + frontMost.getY() * direction.getStepY()
                    + frontMost.getZ() * direction.getStepZ()) {
                frontMost = member.pos();
            }
        }
        BlockPos frontPos = frontMost.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        if (!frontState.isAir() && !frontState.canBeReplaced() && !recorded.contains(frontPos)) {
            // A destroy-on-push block directly in front joins the structure and flies with it —
            // the same rule the seed push uses. Anything else blocking the front ends the flight.
            if (!frontState.hasBlockEntity() && frontState.getPistonPushReaction() == PushReaction.DESTROY) {
                boolean joined = false;
                for (Member member : resolver.getMembers()) {
                    if (member.pos().equals(frontPos)) {
                        intersection.add(member);
                        joined = true;
                        break;
                    }
                }
                if (!joined) {
                    return false; // the resolver did not actually claim the front block
                }
            } else {
                return false;
            }
        }

        BlockPos newOrigin = intersection.get(0).pos();
        long[] newRecord = PotatoStructureResolver.record(intersection, newOrigin);
        // a refused push stops the flight with every cell still in place — never a lost block
        return executePush(level, intersection, destroy, direction, newRecord);
    }
}