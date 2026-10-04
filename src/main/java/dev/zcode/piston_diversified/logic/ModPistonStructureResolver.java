package dev.zcode.piston_diversified.logic;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Copy of the vanilla {@code PistonStructureResolver} with the switches the 无活塞推动事件 and the
 * 强力活塞 need:
 * <ul>
 *   <li>{@code allowStickiness=false} disables all slime/honey analysis — connected structures
 *       are never formed through stickiness (抛射 impact/scrape).</li>
 *   <li>{@code destroyFragile} treats pushable-but-glass-sounding blocks as destroy.</li>
 *   <li>{@code destroyTnt} treats TNT blocks as destroy (they explode when destroyed).</li>
 *   <li>{@code convertTier} (强力活塞 1..3, 0 = off) turns push-resistant blocks into pushable
 *       ones weighted at 6/3/2 against the 12 budget instead of failing the resolve.</li>
 * </ul>
 *
 * <p>An alternate constructor roots the resolver at an explicit start cell with an explicit push
 * direction (拐推's bent pull starts at the head cell offset by the bend, not at piston+2).</p>
 */
public class ModPistonStructureResolver implements PdResolver {
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
    /** 强力活塞 tier: 0 = conversion off, 1..3 = an unpushable block counts as 6/3/2. */
    private final int convertTier;
    private final List<BlockPos> toPush = Lists.newArrayList();
    private final List<BlockPos> toDestroy = Lists.newArrayList();
    /** Running push budget: normal blocks cost 1, converted blocks cost their tier weight. */
    private int totalWeight;

    public ModPistonStructureResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending,
                                      boolean allowStickiness, boolean destroyFragile, boolean destroyTnt) {
        this(level, pistonPos, pistonDirection, extending, allowStickiness, destroyFragile, destroyTnt, 0);
    }

    public ModPistonStructureResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending,
                                      boolean allowStickiness, boolean destroyFragile, boolean destroyTnt, int convertTier) {
        this.level = level;
        this.pistonPos = pistonPos;
        this.pistonDirection = pistonDirection;
        this.extending = extending;
        this.allowStickiness = allowStickiness;
        this.destroyFragile = destroyFragile;
        this.destroyTnt = destroyTnt;
        this.convertTier = convertTier;
        if (extending) {
            this.pushDirection = pistonDirection;
            this.startPos = pistonPos.relative(pistonDirection);
        } else {
            this.pushDirection = pistonDirection.getOpposite();
            this.startPos = pistonPos.relative(pistonDirection, 2);
        }
    }

    /**
     * Explicit-start variant: the resolve begins at {@code startPos} pushing along
     * {@code pushDirection}; {@code excludePos} plays the role of the piston (blocks there are
     * never pulled into the structure).
     */
    public ModPistonStructureResolver(Level level, BlockPos startPos, Direction pushDirection, BlockPos excludePos, boolean allowStickiness) {
        this.level = level;
        this.pistonPos = excludePos;
        this.pistonDirection = pushDirection;
        this.extending = true;
        this.startPos = startPos;
        this.pushDirection = pushDirection;
        this.allowStickiness = allowStickiness;
        this.destroyFragile = false;
        this.destroyTnt = false;
        this.convertTier = 0;
    }

    public boolean resolve() {
        this.toPush.clear();
        this.toDestroy.clear();
        this.totalWeight = 0;
        BlockState startState = this.level.getBlockState(this.startPos);
        if (this.customDestroy(startState)) {
            this.toDestroy.add(this.startPos);
            return true;
        }
        if (!PistonBaseBlock.isPushable(startState, this.level, this.startPos, this.pushDirection, false, this.pistonDirection)
            && !this.canConvert(this.startPos, startState)) {
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

    /**
     * 强力活塞 conversion: a block that vanilla would refuse to push — push reaction BLOCK, the
     * hard-coded refusal of obsidian-family blocks (their reaction is NORMAL!), or an unbreakable
     * one such as bedrock — joins the structure at its tier weight instead of failing. Block
     * entities and pistons of any flavour are never converted (规划: 方块实体不变).
     */
    private boolean canConvert(BlockPos pos, BlockState state) {
        if (this.convertTier <= 0) {
            return false;
        }
        // Position-dependent vanilla rejections must stay rejections (world bounds, border).
        if (pos.getY() < dev.zcode.piston_diversified.PdHelpers.minBuildHeight(this.level)
            || pos.getY() > dev.zcode.piston_diversified.PdHelpers.maxBuildHeight(this.level)
            || !this.level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (this.pushDirection == Direction.DOWN && pos.getY() == dev.zcode.piston_diversified.PdHelpers.minBuildHeight(this.level)) {
            return false;
        }
        if (this.pushDirection == Direction.UP && pos.getY() == dev.zcode.piston_diversified.PdHelpers.maxBuildHeight(this.level)) {
            return false;
        }
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getBlock() instanceof PistonBaseBlock
            || state.getBlock() instanceof PistonHeadBlock
            || state.getBlock() instanceof MovingPistonBlock) {
            return false;
        }
        if (state.is(net.minecraft.world.level.block.Blocks.PISTON)
            || state.is(net.minecraft.world.level.block.Blocks.STICKY_PISTON)) {
            return false;
        }
        // destroy-on-push blocks are popped by the normal path, never converted
        if (state.getPistonPushReaction() == PushReaction.DESTROY) {
            return false;
        }
        // vanilla can already carry it: nothing to convert
        return !PistonBaseBlock.isPushable(state, this.level, pos, this.pushDirection, true, this.pushDirection);
    }

    /** Weight of one cell against the 12 budget: converted blocks cost their tier weight. */
    private int weight(BlockPos pos, BlockState state) {
        return this.canConvert(pos, state) ? this.tierWeight() : 1;
    }

    private int tierWeight() {
        return switch (this.convertTier) {
            case 1 -> 6;
            case 2 -> 3;
            default -> 2;
        };
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

        if (!PistonBaseBlock.isPushable(originState, this.level, originPos, this.pushDirection, false, direction)
            && !this.canConvert(originPos, originState)) {
            return true;
        }

        if (originPos.equals(this.pistonPos)) {
            return true;
        }

        if (this.toPush.contains(originPos)) {
            return true;
        }

        // pendingWeight mirrors the vanilla "i" counter (blocks about to join the line), but in
        // budget units: converted cells cost 6/3/2 instead of 1.
        int pendingWeight = this.weight(originPos, originState);
        int behindCount = 1;
        if (pendingWeight + this.totalWeight > MAX_PUSH_DEPTH) {
            return false;
        }

        while (this.isSticky(originState)) {
            BlockPos behindPos = originPos.relative(this.pushDirection.getOpposite(), behindCount);
            BlockState behindState = originState;
            originState = this.level.getBlockState(behindPos);
            if (originState.isAir()
                || !this.canStickToEachOther(behindState, originState)
                || !PistonBaseBlock.isPushable(originState, this.level, behindPos, this.pushDirection, false, this.pushDirection.getOpposite())
                    && !this.canConvert(behindPos, originState)
                || behindPos.equals(this.pistonPos)) {
                break;
            }

            pendingWeight += this.weight(behindPos, originState);
            behindCount++;
            if (pendingWeight + this.totalWeight > MAX_PUSH_DEPTH) {
                return false;
            }
        }

        int lineCount = 0;

        for (int k = behindCount - 1; k >= 0; k--) {
            BlockPos linePos = originPos.relative(this.pushDirection.getOpposite(), k);
            this.totalWeight += this.weight(linePos, this.level.getBlockState(linePos));
            this.toPush.add(linePos);
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
                && !this.canConvert(forwardPos, forwardState)) {
                return false;
            }
            if (forwardPos.equals(this.pistonPos)) {
                return false;
            }

            if (forwardState.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(forwardPos);
                return true;
            }

            if (this.totalWeight + this.weight(forwardPos, forwardState) > MAX_PUSH_DEPTH) {
                return false;
            }

            this.totalWeight += this.weight(forwardPos, forwardState);
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

    /** Total budget cost of {@link #getToPush()} after {@link #resolve()} (converted cells cost more). */
    public int totalWeight() {
        return this.totalWeight;
    }

    public List<BlockPos> getToPush() {
        return this.toPush;
    }

    public List<BlockPos> getToDestroy() {
        return this.toDestroy;
    }
}
