package dev.zcode.piston_diversified.block;

import com.google.common.collect.Lists;
import dev.zcode.piston_diversified.PdHelpers;
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
 *   <li>{@link #canRetract()} — false = never retract (长推).</li>
 *   <li>{@link #reactsToNeighbors()} — false = ignore neighbor updates (侦测器).</li>
 *   <li>{@link #canPushBlocks()} — false = refuse to extend when anything pushable is in front (虚弱/风弹).</li>
 *   <li>{@link #marksMovingPistonsFast()} — mark created moving pistons as "fast" (快速).</li>
 *   <li>{@link #pullOnInstantRetract()} — pull the block back after a 0-tick instant retract (蜂蜜).</li>
 *   <li>{@link #handleExtend} — fully replace the extend action (抛射/后坐).</li>
 *   <li>{@link #afterExtendExecuted}/{@link #afterRetractExecuted} — post-event scheduling (循环/风弹).</li>
 *   <li>{@link #customizeHeadState} — adjust the placed head state (头颅).</li>
 * </ul>
 */
public abstract class ModPistonBaseBlock extends PistonBaseBlock {
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

    protected boolean cancelRetractIfPowered() {
        return true;
    }

    protected boolean canRetract() {
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
     * (抛射 launches the front block, 后坐 recoils the base) — the vanilla push is skipped.
     */
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        return false;
    }

    protected void afterExtendExecuted(ServerLevel level, BlockPos pos, Direction direction) {
    }

    protected void afterRetractExecuted(ServerLevel level, BlockPos pos, Direction direction) {
    }

    /** Adjust the head state created during extension (头颅 sets POWERED here). */
    protected BlockState customizeHeadState(BlockState headState, Direction direction) {
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
            if (new PistonStructureResolver(level, pos, direction, true).resolve()) {
                level.blockEvent(pos, this, 0, direction.get3DDataValue());
            }
        } else if (!bl && state.getValue(EXTENDED) && this.canRetract()) {
            BlockPos secondPos = pos.relative(direction, 2);
            BlockState secondState = level.getBlockState(secondPos);
            int type = 1;
            if (secondState.is(Blocks.MOVING_PISTON)
                && secondState.getValue(MovingPistonBlock.FACING) == direction
                && level.getBlockEntity(secondPos) instanceof PistonMovingBlockEntity movingBe
                && movingBe.isExtending()
                && (
                    movingBe.getProgress(0.0F) < 0.5F
                        || level.getGameTime() == movingBe.getLastTicked()
                        || ((ServerLevel) level).isHandlingTick()
                )) {
                type = 2;
            }

            level.blockEvent(pos, this, type, direction.get3DDataValue());
        }
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
            if (this.handleExtend(level, pos, direction, state)) {
                return true;
            }

            if (!this.moveBlocks(level, pos, direction, true)) {
                return false;
            }

            level.setBlock(pos, extendedState, 67);
            if (!this.isSilent()) {
                level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, PdHelpers.pdRandom(level).nextFloat() * 0.25F + 0.6F);
                level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(extendedState));
            }
            if (level instanceof ServerLevel serverLevel) {
                this.afterExtendExecuted(serverLevel, pos, direction);
            }
        } else if (id == 1 || id == 2) {
            BlockEntity frontBe = level.getBlockEntity(pos.relative(direction));
            if (frontBe instanceof PistonMovingBlockEntity movingBe) {
                movingBe.finalTick();
            }

            BlockState movingState = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, direction)
                .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
            level.setBlock(pos, movingState, 276);
            BlockEntity retractBe = MovingPistonBlock.newMovingBlockEntity(
                pos, movingState, this.defaultBlockState().setValue(FACING, Direction.from3DDataValue(param & 7)), direction, false, true
            );
            this.markFast(retractBe);
            level.setBlockEntity(retractBe);
            level.updateNeighborsAt(pos, movingState.getBlock());
            movingState.updateNeighbourShapes(level, pos, 2);
            if (this.sticky) {
                BlockPos secondPos = pos.offset(direction.getStepX() * 2, direction.getStepY() * 2, direction.getStepZ() * 2);
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
                    this.moveBlocks(level, pos, direction, false);
                } else if (!instantSecond) {
                    if (id != 1 && this.pullOnInstantRetract()) {
                        // 蜂蜜活塞: vanilla would drop the block here (type 2) — pull instead; if the
                        // pull fails the head is still removed, matching the vanilla end state.
                        this.moveBlocks(level, pos, direction, false);
                    } else if (id != 1
                        || secondState.isAir()
                        || !isPushable(secondState, level, secondPos, direction.getOpposite(), false, direction)
                        || secondState.getPistonPushReaction() != PushReaction.NORMAL
                            && !secondState.is(Blocks.PISTON)
                            && !secondState.is(Blocks.STICKY_PISTON)) {
                        level.removeBlock(pos.relative(direction), false);
                    } else {
                        this.moveBlocks(level, pos, direction, false);
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

        return true;
    }

    private void markFast(BlockEntity be) {
        if (this.marksMovingPistonsFast() && be instanceof dev.zcode.piston_diversified.duck.PistonDuck duck) {
            duck.pistonDiversified$setFast(true);
        }
    }

    private boolean moveBlocks(Level level, BlockPos pos, Direction facing, boolean extending) {
        BlockPos frontPos = pos.relative(facing);
        if (!extending && level.getBlockState(frontPos).is(Blocks.PISTON_HEAD)) {
            level.setBlock(frontPos, Blocks.AIR.defaultBlockState(), 276);
        }

        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, facing, extending);
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
        Direction moveDirection = extending ? facing : facing.getOpposite();
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
            BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, facing);
            level.setBlock(targetPos, movingState, 324);
            BlockEntity movingBe = MovingPistonBlock.newMovingBlockEntity(targetPos, movingState, oldStates.get(k), facing, extending, false);
            this.markFast(movingBe);
            level.setBlockEntity(movingBe);
            destroyedStates[i++] = pushedState;
        }

        if (extending) {
            PistonType headType = this.sticky ? PistonType.STICKY : PistonType.DEFAULT;
            BlockState headState = this.customizeHeadState(
                this.headBlock().defaultBlockState().setValue(PistonHeadBlock.FACING, facing).setValue(PistonHeadBlock.TYPE, headType),
                facing
            );
            BlockState baseMovingState = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, facing)
                .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
            map.remove(frontPos);
            level.setBlock(frontPos, baseMovingState, 324);
            BlockEntity headBe = MovingPistonBlock.newMovingBlockEntity(frontPos, baseMovingState, headState, facing, true, true);
            this.markFast(headBe);
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
            this.updateNeighbors(level, frontPos, Blocks.PISTON_HEAD, facing);
        }

        return true;
    }

    private void updateNeighbors(Level level, BlockPos pos, Block block, Direction pushDirection) {
        //? if >=1.21.2 {
        level.updateNeighborsAt(pos, block, ExperimentalRedstoneUtils.initialOrientation(level, pushDirection, null));
        //?} else {
        level.updateNeighborsAt(pos, block);
        //?}
    }
}
