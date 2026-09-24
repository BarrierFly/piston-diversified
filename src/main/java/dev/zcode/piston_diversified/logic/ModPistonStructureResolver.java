package dev.zcode.piston_diversified.logic;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Copy of the vanilla {@code PistonStructureResolver} with the switches the 无活塞推动事件 needs:
 * <ul>
 *   <li>{@code allowStickiness=false} disables all slime/honey analysis — connected structures
 *       are never formed through stickiness (抛射 impact/scrape).</li>
 *   <li>{@code destroyFragile} treats pushable-but-glass-sounding blocks as destroy.</li>
 *   <li>{@code destroyTnt} treats TNT blocks as destroy (they explode when destroyed).</li>
 * </ul>
 */
public class ModPistonStructureResolver {
    public static final int MAX_PUSH_DEPTH = 12;

    private final Level level;
    private final BlockPos pistonPos;
    private final boolean extending;
    private final BlockPos startPos;
    private final Direction pushDirection;
    private final Direction pistonDirection;
    private final boolean allowStickiness;
    private final boolean destroyFragile;
    private final boolean destroyTnt;
    private final List<BlockPos> toPush = Lists.newArrayList();
    private final List<BlockPos> toDestroy = Lists.newArrayList();

    public ModPistonStructureResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending,
                                      boolean allowStickiness, boolean destroyFragile, boolean destroyTnt) {
        this.level = level;
        this.pistonPos = pistonPos;
        this.pistonDirection = pistonDirection;
        this.extending = extending;
        this.allowStickiness = allowStickiness;
        this.destroyFragile = destroyFragile;
        this.destroyTnt = destroyTnt;
        if (extending) {
            this.pushDirection = pistonDirection;
            this.startPos = pistonPos.relative(pistonDirection);
        } else {
            this.pushDirection = pistonDirection.getOpposite();
            this.startPos = pistonPos.relative(pistonDirection, 2);
        }
    }

    public boolean resolve() {
        this.toPush.clear();
        this.toDestroy.clear();
        BlockState startState = this.level.getBlockState(this.startPos);
        if (this.customDestroy(startState)) {
            this.toDestroy.add(this.startPos);
            return true;
        }
        if (!PistonBaseBlock.isPushable(startState, this.level, this.startPos, this.pushDirection, false, this.pistonDirection)) {
            if (this.extending && startState.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(this.startPos);
                return true;
            }
            return false;
        }
        if (!this.addBlockLine(this.startPos, this.pushDirection)) {
            return false;
        }

        for (int i = 0; i < this.toPush.size(); i++) {
            BlockPos pos = this.toPush.get(i);
            if (this.isSticky(this.level.getBlockState(pos)) && !this.addBranchingBlocks(pos)) {
                return false;
            }
        }

        return true;
    }

    /** Whether this pushable block should be destroyed instead of moved by the pistonless push. */
    private boolean customDestroy(BlockState state) {
        if (this.destroyTnt && state.is(net.minecraft.world.level.block.Blocks.TNT)) {
            return true;
        }
        return this.destroyFragile && isFragileDestroy(state);
    }

    /**
     * The "fragile" rule: pushable (NORMAL) but glass-sounding — the 抛射冲击 destroys these where
     * a normal piston push would carry them. Shared so callers can tell a fragile destruction
     * (glass shatter) apart from an ordinary DESTROY-reaction pop.
     */
    public static boolean isFragileDestroy(BlockState state) {
        return state.getPistonPushReaction() == PushReaction.NORMAL
            && state.getSoundType() == SoundType.GLASS;
    }

    private boolean isSticky(BlockState state) {
        if (!this.allowStickiness) {
            return false;
        }
        return state.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK);
    }

    private boolean canStickToEachOther(BlockState state1, BlockState state2) {
        if (state1.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK) && state2.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK)) {
            return false;
        }
        return state1.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK) && state2.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK)
            ? false
            : isSticky(state1) || isSticky(state2);
    }

    private boolean addBlockLine(BlockPos originPos, Direction direction) {
        BlockState originState = this.level.getBlockState(originPos);
        if (originState.isAir()) {
            return true;
        }

        if (!PistonBaseBlock.isPushable(originState, this.level, originPos, this.pushDirection, false, direction)) {
            return true;
        }

        if (originPos.equals(this.pistonPos)) {
            return true;
        }

        if (this.toPush.contains(originPos)) {
            return true;
        }

        int behindCount = 1;
        if (behindCount + this.toPush.size() > MAX_PUSH_DEPTH) {
            return false;
        }

        while (this.isSticky(originState)) {
            BlockPos behindPos = originPos.relative(this.pushDirection.getOpposite(), behindCount);
            BlockState behindState = originState;
            originState = this.level.getBlockState(behindPos);
            if (originState.isAir()
                || !this.canStickToEachOther(behindState, originState)
                || !PistonBaseBlock.isPushable(originState, this.level, behindPos, this.pushDirection, false, this.pushDirection.getOpposite())
                || behindPos.equals(this.pistonPos)) {
                break;
            }

            if (++behindCount + this.toPush.size() > MAX_PUSH_DEPTH) {
                return false;
            }
        }

        int lineCount = 0;

        for (int k = behindCount - 1; k >= 0; k--) {
            this.toPush.add(originPos.relative(this.pushDirection.getOpposite(), k));
            lineCount++;
        }

        int forwardCount = 1;

        while (true) {
            BlockPos forwardPos = originPos.relative(this.pushDirection, forwardCount);
            int collisionIndex = this.toPush.indexOf(forwardPos);
            if (collisionIndex > -1) {
                this.reorderListAtCollision(lineCount, collisionIndex);

                for (int n = 0; n <= collisionIndex + lineCount; n++) {
                    BlockPos branchPos = this.toPush.get(n);
                    if (this.isSticky(this.level.getBlockState(branchPos)) && !this.addBranchingBlocks(branchPos)) {
                        return false;
                    }
                }

                return true;
            }

            BlockState forwardState = this.level.getBlockState(forwardPos);
            if (forwardState.isAir()) {
                return true;
            }

            if (this.customDestroy(forwardState)) {
                this.toDestroy.add(forwardPos);
                return true;
            }

            if (!PistonBaseBlock.isPushable(forwardState, this.level, forwardPos, this.pushDirection, true, this.pushDirection)
                || forwardPos.equals(this.pistonPos)) {
                return false;
            }

            if (forwardState.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(forwardPos);
                return true;
            }

            if (this.toPush.size() >= MAX_PUSH_DEPTH) {
                return false;
            }

            this.toPush.add(forwardPos);
            lineCount++;
            forwardCount++;
        }
    }

    private void reorderListAtCollision(int offsets, int index) {
        List<BlockPos> head = Lists.newArrayList();
        List<BlockPos> tail = Lists.newArrayList();
        List<BlockPos> middle = Lists.newArrayList();
        head.addAll(this.toPush.subList(0, index));
        tail.addAll(this.toPush.subList(this.toPush.size() - offsets, this.toPush.size()));
        middle.addAll(this.toPush.subList(index, this.toPush.size() - offsets));
        this.toPush.clear();
        this.toPush.addAll(head);
        this.toPush.addAll(tail);
        this.toPush.addAll(middle);
    }

    private boolean addBranchingBlocks(BlockPos fromPos) {
        BlockState fromState = this.level.getBlockState(fromPos);

        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != this.pushDirection.getAxis()) {
                BlockPos branchPos = fromPos.relative(direction);
                BlockState branchState = this.level.getBlockState(branchPos);
                if (this.canStickToEachOther(branchState, fromState) && !this.addBlockLine(branchPos, direction)) {
                    return false;
                }
            }
        }

        return true;
    }

    public Direction getPushDirection() {
        return this.pushDirection;
    }

    public List<BlockPos> getToPush() {
        return this.toPush;
    }

    public List<BlockPos> getToDestroy() {
        return this.toDestroy;
    }
}
