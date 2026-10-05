package dev.zcode.piston_diversified.block;

import com.google.common.collect.Lists;
import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.logic.PdResolver;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
//?}

/**
 * Shared base for every Piston Diversified piston. The body is a faithful copy of the vanilla
 * {@code PistonBaseBlock} extend/retract flow, refactored so that variants can change behavior
 * through small protected hooks instead of copying the whole flow again.
 *
 * <p>Hooks (all default to vanilla behavior):
 * <ul>
 *   <li>{@link #headBlock()} — which head block this piston places.</li>
 *   <li>{@link #isSilent()} — no sound / no game events (静音活塞).</li>
 *   <li>{@link #qcCell} — which cell the quasi-connectivity check reads (随朝向QC).</li>
 *   <li>{@link #extraPowerSignal} — additional "count as powered" condition (连锁型).</li>
 *   <li>{@link #requireSignalToExtend()} — false = extend events run without a signal (侦测器/长推).</li>
 *   <li>{@link #cancelRetractIfPowered()} — false = retract events run even while powered (循环型).</li>
 *   <li>{@link #canRetract} — false = never retract (长推; 重力 without a head).</li>
 *   <li>{@link #reactsToNeighbors()} — false = ignore neighbor updates (侦测器).</li>
 *   <li>{@link #canPushBlocks()} — false = refuse to extend when anything pushable is in front (虚弱/风弹).</li>
 *   <li>{@link #marksMovingPistonsFast()} — mark created moving pistons as "fast" (快速).</li>
 *   <li>{@link #pullOnInstantRetract()} — pull the block back after a 0-tick instant retract (蜂蜜).</li>
 *   <li>{@link #handleExtend} — fully replace the extend action (抛射/后坐/拐推/镐).</li>
 *   <li>{@link #createResolver} — swap the structure resolver used by the extend pre-check and
 *       every move (强力 conversion resolver; 拐推 uses it for its bent retract pull).</li>
 *   <li>{@link #retractPullSource} — which cell the sticky retract pulls from (拐推: the head cell
 *       offset by the bend instead of piston+2).</li>
 *   <li>{@link #sendExtendEvent}/{@link #sendRetractEvent} — how extend/retract decisions are
 *       scheduled (0t计划刻 replaces block events with 0gt scheduled ticks).</li>
 *   <li>{@link #afterExtendExecuted}/{@link #afterRetractExecuted} — post-event scheduling (循环/风弹).</li>
 *   <li>{@link #customizeHeadState} — adjust the placed head state (头颅/镐/拐推).</li>
 * </ul>
 */
public abstract class ModPistonBaseBlock extends PistonBaseBlock {
    /**
     * Block-update flags for placing a moving piston / rod so the CLIENT actually gets it.
     *
     * <p>Vanilla's own piston uses 324 (= INVISIBLE + MOVE_BY_PISTON + SKIP_BLOCK_ENTITY_SIDEEFFECTS)
     * and 276 (= INVISIBLE + KNOWN_SHAPE + SKIP_BLOCK_ENTITY_SIDEEFFECTS) — neither carries
     * bit 2, UPDATE_CLIENTS. Vanilla gets away with it because {@code PistonMovingBlockEntity} is
     * animated through the block-event broadcast, which the vanilla trigger path sends for every
     * extend/retract. A piston whose move runs outside that path (a scheduled tick, a tick chain,
     * a piston-less push) would place its moving piston with no client notification at all:
     * {@code Level.setBlock} only calls {@code sendBlockUpdated} when {@code (flags & 2) != 0}.
     * Without it the block never reaches the client.</p>
     *
     * <p><strong>Bit 2 only gets the block, never the block entity.</strong>
     * {@code BlockEntity#getUpdatePacket} returns null and vanilla's
     * {@code PistonMovingBlockEntity} does not override it, so {@code ChunkHolder} sends nothing
     * even once the block is marked — and {@code MovingPistonBlock} is
     * {@code RenderShape#INVISIBLE}. A moving piston the client did not build itself is
     * therefore invisible however loudly we mark the chunk. The variants that have to rely on the
     * server instead of a client replay say so with
     * {@code PistonDuck#pistonDiversified$setNeedsClientSync}, which makes
     * {@code PistonMovingBlockEntityMixin} send the update tag after all.</p>
     *
     * <p>Everything else is left alone: the neighbour updates and side effects stay suppressed
     * exactly as upstream intends.</p>
     */
    public static final int SYNC_MOVING_PISTON = 324 | Block.UPDATE_CLIENTS;
    /** Same for the retract animation and for rods/heads placed by a tick chain. */
    public static final int SYNC_RETRACT = 276 | Block.UPDATE_CLIENTS;

    /**
     * Vacating a cell without letting anything react to it: the client still gets the block, but no
     * neighbour update, no shape update and no {@code onPlace} run. Raw 786 = 2 (UPDATE_CLIENTS) |
     * 16 (UPDATE_KNOWN_SHAPE) | 256 (SKIP_BLOCK_ENTITY_SIDEEFFECTS) | 512 (SKIP_ON_PLACE); the last
     * one is unnamed in 1.19.4's mappings but has meant "skip onPlace" there too.
     *
     * <p>Vanilla empties the vacated cells with flag 82, which is safe there only because a piston
     * structure is a straight line — no vacated cell is ever the support of another block that is
     * still standing. The 马铃薯活塞's structure is a blob, so it can be: {@code BaseTorchBlock}
     * (and every other "rests on the block below" block) drops to air from a shape update, and
     * since the originals are all still in place while the cells are emptied, a torch riding a
     * block was destroyed by the very first push. Callers that clear a whole structure at once
     * must use this and run their own shape updates once every cell has been replaced.</p>
     */
    public static final int SILENT_CLEAR = 786;

    /**
     * Block-event id for the client mirror of a move the server drove outside the block-event
     * channel (a scheduled tick, a tick chain). Vanilla never needs it because its pistons always
     * send a block event and the client replays the move locally — which is what gives the moving
     * pistons their progress-0 start and therefore their full slide. Without the replay the
     * client only learns about a finished piston from the block-entity packet and the pushed
     * blocks read as snapping into place.
     *
     * <p>Note that the replay only helps if it reaches the client <em>before</em> the server's
     * block updates. {@code ServerLevel.tick} runs block events after {@code chunkSource.tick()},
     * so anything this channel is used for must move the world <em>inside</em> the block event,
     * never before it — see {@link ScheduledTickPistonBlock}, which therefore makes its
     * scheduled tick only decide and lets the block event execute.</p>
     */
    public static final int MIRROR_EXTEND = 10;
    public static final int MIRROR_RETRACT = 11;

    protected final boolean sticky;

    protected ModPistonBaseBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
        this.sticky = sticky;
    }

    // ------------------------------------------------------------------ hooks

    /** The head block this piston places in front of itself. */
    public abstract Block headBlock();

    /** Public stickiness probe (vanilla keeps the field private). */
    public boolean pdIsSticky() {
        return this.sticky;
    }

    protected boolean isSilent() {
        return false;
    }

    /** Cell the QC (quasi-connectivity) check reads; vanilla is the cell above the piston. */
    protected BlockPos qcCell(BlockPos pos, Direction facing) {
        return pos.above();
    }

    /** Extra "powered" condition OR-ed into the signal check (e.g. a piston head aiming at us). */
    protected boolean extraPowerSignal(Level level, BlockPos pos, Direction facing) {
        return false;
    }

    protected boolean requireSignalToExtend() {
        return true;
    }

    /**
     * Send the extend event even when the vanilla pre-resolve declares the push impossible
     * (后坐: an unpushable front block must still trigger the recoil attempt — handleExtend
     * decides what actually happens).
     *
     * <p>Answer it for the fronts the variant really wants, not unconditionally: extending leaves
     * a moving piston (the head) in front of the base while the base state is still
     * {@code EXTENDED=false}, and the neighbour update that goes with it re-enters
     * {@link #checkIfExtend}. The vanilla pre-resolve swallows that re-entry because a moving
     * piston cannot be pushed; answering yes here would turn it into a second extend event.
     */
    protected boolean extendEventWithoutResolve(Level level, BlockPos pos, Direction direction) {
        return false;
    }

    protected boolean cancelRetractIfPowered() {
        return true;
    }

    protected boolean canRetract(Level level, BlockPos pos, BlockState state) {
        return true;
    }

    protected boolean reactsToNeighbors() {
        return true;
    }

    protected boolean canPushBlocks() {
        return true;
    }

    protected boolean marksMovingPistonsFast() {
        return false;
    }

    /**
     * Vanilla's 0-tick retract (瞬推) final-ticks the second cell and leaves the block there.
     * If true, the piston additionally performs a full retract move, pulling that block back (蜂蜜).
     */
    protected boolean pullOnInstantRetract() {
        return false;
    }

    /**
     * Runs before the vanilla extend move. Return true if the variant handled the extension itself
     * (抛射 launches the front block, 后坐 recoils the base, 拐推 pushes the front sideways, 镐
     * mines the front block) — the vanilla push is skipped when true; returning false continues
     * with the vanilla push.
     */
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        return false;
    }

    /**
     * The structure resolver for the extend pre-check and every move/pull. Return null for the
     * vanilla resolver. 强力 swaps in the push-conversion resolver; 拐推 returns its bent pull
     * resolver for retracts ({@code extending=false}). {@code baseState} is the piston's own
     * captured state — during a retract move the block at {@code pos} is already a moving
     * piston, so the state cannot be re-read from the level.
     */
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        return null;
    }

    /** Cell whose contents the sticky retract pulls backwards (vanilla: piston + 2). */
    protected BlockPos retractPullSource(BlockPos pos, Direction direction, BlockState state) {
        return pos.relative(direction, 2);
    }

    /**
     * How an extend decision reaches execution. Vanilla sends a block event; the 0t计划刻 piston
     * schedules a 0gt tick instead (the decision itself is re-derived from live signals later).
     */
    protected void sendExtendEvent(Level level, BlockPos pos, Direction direction) {
        level.blockEvent(pos, this, 0, direction.get3DDataValue());
    }

    /** How a retract decision reaches execution (see {@link #sendExtendEvent}). */
    protected void sendRetractEvent(Level level, BlockPos pos, Direction direction, int type) {
        level.blockEvent(pos, this, type, direction.get3DDataValue());
    }

    /**
     * Ask the client to replay a move the server just made outside the block-event channel.
     * Send it BEFORE the server's own block writes so the client builds its moving pistons
     * first; the server's updates then confirm them, exactly as a vanilla piston does.
     */
    protected void sendMirrorEvent(Level level, BlockPos pos, Direction direction, boolean extending) {
        if (!level.isClientSide()) {
            level.blockEvent(pos, this, extending ? MIRROR_EXTEND : MIRROR_RETRACT, direction.get3DDataValue());
        }
    }

    /** Client replay of a server-driven extend. Overridden by variants with a custom move. */
    protected void mirrorExtendOnClient(Level level, BlockPos pos, Direction direction, BlockState state) {
        this.executeExtend(level, pos, direction, state);
    }

    /** Client replay of a server-driven retract. Overridden by variants with a custom move. */
    protected void mirrorRetractOnClient(Level level, BlockPos pos, Direction direction, BlockState state) {
        this.executeRetract(level, pos, direction, state, 1);
    }

    protected void afterExtendExecuted(ServerLevel level, BlockPos pos, Direction direction) {
    }

    protected void afterRetractExecuted(ServerLevel level, BlockPos pos, Direction direction) {
    }

    /** Adjust the head state created during extension (拐推 copies the bend, 镐 copies the tool). */
    protected BlockState customizeHeadState(BlockState headState, Direction direction, BlockState baseState) {
        return headState;
    }

    // ------------------------------------------------------- copied vanilla flow

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            this.checkIfExtend(level, pos, state);
        }
    }

    //? if <1.21.2 {
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide() && this.reactsToNeighbors()) {
            this.checkIfExtend(level, pos, state);
        }
    }
    //?} else {
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && this.reactsToNeighbors()) {
            this.checkIfExtend(level, pos, state);
        }
    }
    //?}

    //? if <1.21.2 {
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    //?} else {
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    //?}
        if (!oldState.is(state.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) == null && this.reactsToNeighbors()) {
                this.checkIfExtend(level, pos, state);
            }
        }
    }

    private void checkIfExtend(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        boolean bl = this.hasPowerSignal(level, pos, direction);
        if (bl && !state.getValue(EXTENDED)) {
            if (this.resolveExtend(level, pos, direction) || this.extendEventWithoutResolve(level, pos, direction)) {
                this.sendExtendEvent(level, pos, direction);
            }
        } else if (!bl && state.getValue(EXTENDED) && this.canRetract(level, pos, state)) {
            int type = this.retractType(level, pos, direction, state);
            this.sendRetractEvent(level, pos, direction, type);
        }
    }

    /** Vanilla pre-resolve for the extend event, routed through {@link #createResolver}. */
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        PdResolver resolver = this.createResolver(level, pos, direction, true, null);
        if (resolver != null) {
            return resolver.resolve();
        }
        return new PistonStructureResolver(level, pos, direction, true).resolve();
    }

    /**
     * 1 = a settled retract, 2 = the extend animation is still in flight at the pull source
     * (瞬推 handling). Kept in step with {@link #retractPullSource}.
     */
    protected int retractType(Level level, BlockPos pos, Direction direction, BlockState state) {
        BlockPos secondPos = this.retractPullSource(pos, direction, state);
        BlockState secondState = level.getBlockState(secondPos);
        if (secondState.is(Blocks.MOVING_PISTON)
            && secondState.getValue(MovingPistonBlock.FACING) == direction
            && level.getBlockEntity(secondPos) instanceof PistonMovingBlockEntity movingBe
            && movingBe.isExtending()
            && (
                movingBe.getProgress(0.0F) < 0.5F
                    || level.getGameTime() == movingBe.getLastTicked()
                    || ((ServerLevel) level).isHandlingTick()
            )) {
            return 2;
        }
        return 1;
    }

    protected boolean hasPowerSignal(Level level, BlockPos pos, Direction direction) {
        for (Direction side : Direction.values()) {
            if (side != direction && level.hasSignal(pos.relative(side), side)) {
                return true;
            }
        }

        if (level.hasSignal(pos, Direction.DOWN)) {
            return true;
        }

        BlockPos qcPos = this.qcCell(pos, direction);
        for (Direction side : Direction.values()) {
            if (side != Direction.DOWN && level.hasSignal(qcPos.relative(side), side)) {
                return true;
            }
        }

        return this.extraPowerSignal(level, pos, direction);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        Direction direction = state.getValue(FACING);
        BlockState extendedState = state.setValue(EXTENDED, true);
        if (id == MIRROR_EXTEND || id == MIRROR_RETRACT) {
            // Only the client acts on this: the server has already made the move and must not
            // repeat it. Replaying it here is what gives the client progress-0 moving pistons.
            if (level.isClientSide()) {
                if (id == MIRROR_EXTEND) {
                    this.mirrorExtendOnClient(level, pos, direction, state);
                } else {
                    this.mirrorRetractOnClient(level, pos, direction, state);
                }
            }
            return true;
        }
        if (!level.isClientSide()) {
            boolean bl = this.hasPowerSignal(level, pos, direction);
            if (bl && (id == 1 || id == 2) && this.cancelRetractIfPowered()) {
                level.setBlock(pos, extendedState, 2);
                return false;
            }

            if (!bl && id == 0 && this.requireSignalToExtend()) {
                return false;
            }
        }

        if (id == 0) {
            this.executeExtend(level, pos, direction, state);
        } else if (id == 1 || id == 2) {
            this.executeRetract(level, pos, direction, state, id);
        }

        return true;
    }

    /**
     * The extend event body (vanilla push + variant hooks), shared by {@link #triggerEvent} and
     * the 0t计划刻 piston's tick execution.
     */
    protected void executeExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        BlockState extendedState = state.setValue(EXTENDED, true);
        if (this.handleExtend(level, pos, direction, state)) {
            return;
        }

        if (!this.moveBlocks(level, pos, direction, true, state)) {
            return;
        }

        level.setBlock(pos, extendedState, 67);
        if (!this.isSilent()) {
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, PdHelpers.pdRandom(level).nextFloat() * 0.25F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(extendedState));
        }
        if (level instanceof ServerLevel serverLevel) {
            this.afterExtendExecuted(serverLevel, pos, direction);
        }
    }

    /**
     * The retract event body (vanilla retract incl. the sticky pull), shared by
     * {@link #triggerEvent} and the 0t计划刻 piston's tick execution.
     */
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id) {
        this.executeRetract(level, pos, direction, state, id, null);
    }

    /**
     * Retract with an optional pull veto/mandate: {@code pullOverride} = null keeps the vanilla
     * pull decision, TRUE forces the sticky pull, FALSE forbids it (墙并 coordinated retracts
     * hand every member the group's verdict).
     */
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id, Boolean pullOverride) {
        // A piston head is push-resistant, so any resolver that runs while it still sits in front
        // of the base fails on it. Vanilla's retract therefore clears the head before resolving;
        // variants that pre-check their pull (递推黏塞, 墙并黏塞) must clear it themselves too.
        this.clearHeadCell(level, pos, direction);
        BlockState movingState = Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(MovingPistonBlock.FACING, direction)
            .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
        level.setBlock(pos, movingState, SYNC_RETRACT);
        // Preserve the current state's extra properties (the observer piston's POWERED) into
        // the restored base — defaultBlockState() would reset them when the retract animation
        // ends, re-arming state machines that rely on them surviving the cycle.
        BlockState restoredBase = state
            .setValue(EXTENDED, false)
            .setValue(FACING, direction);
        BlockEntity retractBe = MovingPistonBlock.newMovingBlockEntity(pos, movingState, restoredBase, direction, false, true);
        this.markFast(retractBe);
        markClientSync(retractBe);
        level.setBlockEntity(retractBe);
        level.updateNeighborsAt(pos, movingState.getBlock());
        movingState.updateNeighbourShapes(level, pos, 2);
        if (this.sticky) {
            BlockPos secondPos = this.retractPullSource(pos, direction, state);
            BlockState secondState = level.getBlockState(secondPos);
            boolean instantSecond = false;
            if (secondState.is(Blocks.MOVING_PISTON)
                && level.getBlockEntity(secondPos) instanceof PistonMovingBlockEntity secondBe
                && secondBe.getDirection() == direction
                && secondBe.isExtending()) {
                secondBe.finalTick();
                instantSecond = true;
            }

            if (instantSecond && this.pullOnInstantRetract()) {
                // 蜂蜜活塞: the second cell just solidified — pull it back instead of leaving it behind.
                this.moveBlocks(level, pos, direction, false, state);
            } else if (!instantSecond) {
                boolean mayPull;
                if (pullOverride != null) {
                    mayPull = pullOverride; // coordinated retract: the group already decided
                } else {
                    mayPull = id == 1
                        && !secondState.isAir()
                        && isPushable(secondState, level, secondPos, direction.getOpposite(), false, direction)
                        && (secondState.getPistonPushReaction() == PushReaction.NORMAL
                            || secondState.is(Blocks.PISTON)
                            || secondState.is(Blocks.STICKY_PISTON));
                }
                if (id != 1 && this.pullOnInstantRetract()) {
                    // 蜂蜜活塞: vanilla would drop the block here (type 2) — pull instead; if the
                    // pull fails the head is still removed, matching the vanilla end state.
                    this.moveBlocks(level, pos, direction, false, state);
                } else if (!mayPull) {
                    level.removeBlock(pos.relative(direction), false);
                } else {
                    this.moveBlocks(level, pos, direction, false, state);
                }
            }
        } else {
            level.removeBlock(pos.relative(direction), false);
        }

        if (!this.isSilent()) {
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, PdHelpers.pdRandom(level).nextFloat() * 0.15F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(movingState));
        }
        if (level instanceof ServerLevel serverLevel) {
            this.afterRetractExecuted(serverLevel, pos, direction);
        }
    }

    private void markFast(BlockEntity be) {
        if (this.marksMovingPistonsFast() && be instanceof dev.zcode.piston_diversified.duck.PistonDuck duck) {
            duck.pistonDiversified$setFast(true);
        }
    }

    /**
     * Every moving piston this mod creates is flagged so the server sends its block entity to the
     * client. Most moves reach the client through the block event, where the client builds the
     * entity itself and the packet only re-syncs a progress it already has; the ones that cannot
     * (递推/重力 的 tick 链, 墙并 的成组收回) have nothing else, and without this they are invisible
     * blocks over there. See {@code BlockEntityMixin}.
     */
    public static void markClientSync(BlockEntity be) {
        if (be instanceof dev.zcode.piston_diversified.duck.PistonDuck duck) {
            duck.pistonDiversified$setNeedsClientSync(true);
        }
    }

    private boolean moveBlocks(Level level, BlockPos pos, Direction facing, boolean extending, BlockState baseState) {
        PdResolver custom = this.createResolver(level, pos, facing, extending, baseState);
        if (custom != null) {
            return this.moveBlocksResolved(level, pos, facing, extending, custom, baseState);
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, facing, extending);
        return this.moveBlocksResolved(level, pos, facing, extending, wrap(resolver), baseState);
    }

    /**
     * Clears the head sitting in front of a retracting base, if there is one. Vanilla does this
     * with flag 276 (no neighbour updates) at the head of its retract move; a signal-emitting head
     * still needs its second-order update burst, hence the extra hook.
     *
     * <p>{@code SYNC_RETRACT}, not vanilla's 276: 276 carries no {@code UPDATE_CLIENTS}, so the
     * client is never told about this clearing. The later {@code removeBlock(pos.relative(dir))}
     * cannot make up for it either — by then the cell already holds air server-side, so its
     * {@code setBlock} is a no-op and sends nothing. Every retract therefore left a stale head
     * block on the client (假活塞头) that no later packet ever replaced.</p>
     */
    protected void clearHeadCell(Level level, BlockPos pos, Direction direction) {
        BlockPos frontPos = pos.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        if (frontState.getBlock() instanceof PistonHeadBlock) {
            // instanceof, not a registry check: our modded heads must clear the way too,
            // otherwise the vanilla resolver treats them as obstacles and the pull fails.
            level.setBlock(frontPos, Blocks.AIR.defaultBlockState(), SYNC_RETRACT);
            if (frontState.getBlock() instanceof ModPistonHeadBlock modHead) {
                modHead.pdAfterHeadRemovedWithoutUpdate(level, frontPos, frontState);
            }
        }
    }

    /** The move body, parameterised over the resolver (vanilla copy of {@code moveBlocks}). */
    protected boolean moveBlocksResolved(Level level, BlockPos pos, Direction facing, boolean extending, PdResolver resolver, BlockState baseState) {
        if (!extending) {
            this.clearHeadCell(level, pos, facing);
        }

        if (!resolver.resolve()) {
            return false;
        }
        if (!this.canPushBlocks() && !resolver.getToPush().isEmpty()) {
            // 虚弱/风弹: refuse to move anything pushable; only destroyable blocks may be cleared.
            return false;
        }

        Map<BlockPos, BlockState> map = Maps.newHashMap();
        List<BlockPos> toPush = resolver.getToPush();
        List<BlockState> oldStates = Lists.newArrayList();

        for (BlockPos pushedPos : toPush) {
            BlockState pushedState = level.getBlockState(pushedPos);
            oldStates.add(pushedState);
            map.put(pushedPos, pushedState);
        }

        List<BlockPos> toDestroy = resolver.getToDestroy();
        BlockState[] destroyedStates = new BlockState[toPush.size() + toDestroy.size()];
        // The retract direction comes from the resolver, not from the piston: 拐推's sticky pull
        // runs along the bend (head+bend → head), which is perpendicular to the piston facing.
        Direction moveDirection = extending ? facing : resolver.getPushDirection();
        int i = 0;

        for (int j = toDestroy.size() - 1; j >= 0; j--) {
            BlockPos destroyPos = toDestroy.get(j);
            BlockState destroyState = level.getBlockState(destroyPos);
            BlockEntity destroyBe = destroyState.hasBlockEntity() ? level.getBlockEntity(destroyPos) : null;
            Block.dropResources(destroyState, level, destroyPos, destroyBe);
            if (!destroyState.is(BlockTags.FIRE) && level.isClientSide()) {
                level.levelEvent(2001, destroyPos, Block.getId(destroyState));
            }

            level.setBlock(destroyPos, Blocks.AIR.defaultBlockState(), 18);
            level.gameEvent(GameEvent.BLOCK_DESTROY, destroyPos, GameEvent.Context.of(destroyState));
            destroyedStates[i++] = destroyState;
        }

        for (int k = toPush.size() - 1; k >= 0; k--) {
            BlockPos pushedPos = toPush.get(k);
            BlockState pushedState = level.getBlockState(pushedPos);
            BlockPos targetPos = pushedPos.relative(moveDirection);
            map.remove(targetPos);
            // The moving piston carries the axis it actually travels along, which for a retract is
            // the resolver's push direction — 拐推's sticky pull runs along the bend, not the
            // piston axis. The block used to land in the right cell but animate as if sliding in
            // along the piston face. For every other piston moveDirection.getOpposite() == facing,
            // so this is vanilla's behaviour unchanged.
            Direction beDirection = extending ? facing : moveDirection.getOpposite();
            BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, beDirection);
            level.setBlock(targetPos, movingState, SYNC_MOVING_PISTON);
            BlockEntity movingBe = MovingPistonBlock.newMovingBlockEntity(targetPos, movingState, oldStates.get(k), beDirection, extending, false);
            this.markFast(movingBe);
            markClientSync(movingBe);
            level.setBlockEntity(movingBe);
            destroyedStates[i++] = pushedState;
        }

        if (extending) {
            BlockPos frontPos = pos.relative(facing);
            PistonType headType = this.sticky ? PistonType.STICKY : PistonType.DEFAULT;
            BlockState headState = this.customizeHeadState(
                this.headBlock().defaultBlockState().setValue(PistonHeadBlock.FACING, facing).setValue(PistonHeadBlock.TYPE, headType),
                facing,
                baseState
            );
            BlockState baseMovingState = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, facing)
                .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
            map.remove(frontPos);
            level.setBlock(frontPos, baseMovingState, SYNC_MOVING_PISTON);
            BlockEntity headBe = MovingPistonBlock.newMovingBlockEntity(frontPos, baseMovingState, headState, facing, true, true);
            this.markFast(headBe);
            markClientSync(headBe);
            level.setBlockEntity(headBe);
        }

        BlockState airState = Blocks.AIR.defaultBlockState();

        for (BlockPos clearedPos : map.keySet()) {
            level.setBlock(clearedPos, airState, 82);
        }

        for (Entry<BlockPos, BlockState> entry : map.entrySet()) {
            BlockPos clearedPos = entry.getKey();
            BlockState clearedState = entry.getValue();
            clearedState.updateIndirectNeighbourShapes(level, clearedPos, 2);
            airState.updateNeighbourShapes(level, clearedPos, 2);
            airState.updateIndirectNeighbourShapes(level, clearedPos, 2);
        }

        i = 0;

        for (int l = toDestroy.size() - 1; l >= 0; l--) {
            BlockState destroyedState = destroyedStates[i++];
            BlockPos destroyPos = toDestroy.get(l);
            if (level instanceof ServerLevel serverLevel) {
                //? if >=1.20.3 {
                destroyedState.affectNeighborsAfterRemoval(serverLevel, destroyPos, false);
                //?}
            }

            destroyedState.updateIndirectNeighbourShapes(level, destroyPos, 2);
            this.updateNeighbors(level, destroyPos, destroyedState.getBlock(), resolver.getPushDirection());
        }

        for (int m = toPush.size() - 1; m >= 0; m--) {
            this.updateNeighbors(level, toPush.get(m), destroyedStates[i++].getBlock(), resolver.getPushDirection());
        }

        if (extending) {
            this.updateNeighbors(level, pos.relative(facing), Blocks.PISTON_HEAD, facing);
        }

        return true;
    }

    private static PdResolver wrap(PistonStructureResolver resolver) {
        return new PdResolver() {
            @Override
            public boolean resolve() {
                return resolver.resolve();
            }

            @Override
            public List<BlockPos> getToPush() {
                return resolver.getToPush();
            }

            @Override
            public List<BlockPos> getToDestroy() {
                return resolver.getToDestroy();
            }

            @Override
            public Direction getPushDirection() {
                return resolver.getPushDirection();
            }
        };
    }

    private void updateNeighbors(Level level, BlockPos pos, Block block, Direction pushDirection) {
        //? if >=1.21.2 {
        level.updateNeighborsAt(pos, block, ExperimentalRedstoneUtils.initialOrientation(level, pushDirection, null));
        //?} else {
        level.updateNeighborsAt(pos, block);
        //?}
    }
}
