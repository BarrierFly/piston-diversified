package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.duck.PistonDuck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

/**
 * 快速活塞 support (a moving piston flagged "fast" converts the tick its progress reaches 1.0)
 * and 马铃薯活塞 support (the flight record that keeps a launched structure gliding).
 *
 * <p>Both ride on the vanilla moving-piston entity instead of a separate block-entity type, so
 * the movement animation, entity pushing and landing stay exactly vanilla.</p>
 */
@Mixin(PistonMovingBlockEntity.class)
public class PistonMovingBlockEntityMixin implements PistonDuck {
    @Unique
    private boolean pistonDiversified$fast;
    @Unique
    private long[] pistonDiversified$flight = new long[0];
    @Unique
    private boolean pistonDiversified$flightPrimary;
    @Unique
    private boolean pistonDiversified$landsInWater;
    @Unique
    private boolean pistonDiversified$needsClientSync;

    @Override
    public void pistonDiversified$setFast(boolean fast) {
        this.pistonDiversified$fast = fast;
    }

    @Override
    public boolean pistonDiversified$isFast() {
        return this.pistonDiversified$fast;
    }

    @Override
    public void pistonDiversified$setFlight(long[] record, boolean primary, boolean landsInWater) {
        this.pistonDiversified$flight = record == null ? new long[0] : record;
        this.pistonDiversified$flightPrimary = primary;
        this.pistonDiversified$landsInWater = landsInWater;
    }

    @Override
    public long[] pistonDiversified$getFlight() {
        return this.pistonDiversified$flight;
    }

    @Override
    public boolean pistonDiversified$isFlightPrimary() {
        return this.pistonDiversified$flightPrimary;
    }

    @Override
    public boolean pistonDiversified$landsInWater() {
        return this.pistonDiversified$landsInWater;
    }

    @Override
    public void pistonDiversified$setNeedsClientSync(boolean needsSync) {
        this.pistonDiversified$needsClientSync = needsSync;
    }

    @Override
    public boolean pistonDiversified$needsClientSync() {
        return this.pistonDiversified$needsClientSync;
    }

    /**
     * A default instance (the one {@code MovingPistonBlockMixin} hands the client for a bare
     * {@code MOVING_PISTON} block) has no facing yet, and every consumer of it — collision shape,
     * render state, {@code getMovementDirection} — dereferences that. Give it DOWN so the entity is
     * merely empty instead of a null dereference until the server's update tag lands.
     */
    @Shadow
    private Direction direction;

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At("RETURN"))
    private void pistonDiversified$defaultFacing(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (this.direction == null) {
            this.direction = Direction.DOWN;
        }
    }

    /**
     * Until the entity has a moved state it is the placeholder {@code MovingPistonBlockMixin}
     * created, and ticking it would run it to completion in two ticks and replace the block with
     * air — the server's update tag is one packet behind the block update, so the placeholder does
     * briefly exist. Waiting is also the safe reading: an entity that never receives data stays as
     * invisible and collision-free as a missing one. Client only: a server-side entity always has
     * a real moved state, and if it ever did not, vanilla's own "empty piston becomes air" is the
     * right answer.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void pistonDiversified$skipPlaceholder(Level level, BlockPos pos, BlockState state,
                                                         PistonMovingBlockEntity be, CallbackInfo ci) {
        if (level.isClientSide() && be.getMovedState().isAir()) {
            ci.cancel();
        }
    }

    // (the block-state validation the vanilla entity type performs lives on BlockEntity; nothing
    //  here needs widening — see BlockEntityMixin for the one thing BlockEntity does need)

    @Inject(method = "tick", at = @At("TAIL"))
    private static void pistonDiversified$afterTick(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity be, CallbackInfo ci) {
        PistonDuck duck = (PistonDuck) be;
        if (duck.pistonDiversified$isFast() && !level.isClientSide()) {
            // Only convert right at the moment progress reached 1.0 this tick.
            if (((PistonMovingBlockEntityAccessor) be).pistonDiversified$getProgressO() < 1.0F
                && ((PistonMovingBlockEntityAccessor) be).pistonDiversified$getProgress() >= 1.0F) {
                pistonDiversified$placeFinal(level, pos, be);
                return;
            }
        }

        // 马铃薯活塞: the pushed cell lands on vanilla's own schedule. Vanilla's tick body resolves
        // the block from progressO >= 1.0 — one tick AFTER progress reaches 1.0 — and that tick is
        // what makes landing order-safe: by then every sibling of the same flight step has finished
        // its slide, so whichever cell lands first, its support reads either an already-landed
        // block or a sibling piston at progress 1.0 (final position, live shape). Landing any
        // earlier puts the landing inside the window where siblings are mid-flight — a blob can
        // carry a block resting on another member, and asking then reads a piston parked half a
        // cell away, which drops the rider. (The old "deterministic early landing" was built on
        // the belief that the server never reaches vanilla's third moving-piston tick; the hook it
        // replaced gated on !be.isRemoved() at TAIL, which vanilla's own landing sets mid-tick —
        // the tick arrived fine, the gate was blind to it.) By this TAIL hook vanilla has already
        // landed the cell, so only mod bookkeeping is left: restore waterlogging and queue the
        // next flight step from the leading cell.
        if (duck.pistonDiversified$getFlight().length > 0
            && be.isExtending()
            && !level.isClientSide()
            && ((PistonMovingBlockEntityAccessor) be).pistonDiversified$getProgressO() >= 1.0F
            && !level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
            pistonDiversified$landPotato(level, pos, be, duck);
        }
    }

    /**
     * 到位有水恢复含水（每个被推的格子）+ 首格在身后登记延迟 1gt 的无活塞推出事件（悬浮飞行）。
     * 到位无水且不可无水的方块走原版落地逻辑（破坏掉落）。
     */
    @Unique
    private static void pistonDiversified$landPotato(Level level, BlockPos pos, PistonMovingBlockEntity be, PistonDuck duck) {
        BlockState landed = level.getBlockState(pos);
        boolean wantsWater = duck.pistonDiversified$landsInWater();
        if (landed.hasProperty(BlockStateProperties.WATERLOGGED)
            && landed.getValue(BlockStateProperties.WATERLOGGED) != wantsWater) {
            level.setBlock(pos, landed.setValue(BlockStateProperties.WATERLOGGED, wantsWater), 3);
        }
        if (duck.pistonDiversified$isFlightPrimary()
            && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            dev.zcode.piston_diversified.logic.PotatoFlightQueue.add(
                serverLevel, pos, be.getDirection(), duck.pistonDiversified$getFlight(), landed.getBlock()
            );
        }
    }

    /**
     * The fast piston's early conversion: the tick its progress reaches 1.0 the flying cell becomes
     * the carried block, one tick earlier than vanilla's own landing (which resolves from
     * progressO). That head start is the whole point of the fast piston — a vanilla-schedule
     * landing would put its blocks down a tick late. Everything else here is vanilla's own landing
     * sequence verbatim: resolve the carried state against the neighbours up front (a block they
     * reject is destroyed with its drops instead of vanishing), strip waterlogging (vanilla never
     * lands a waterlogged state; the potato restores its own afterwards), then the neighbour
     * notification. The potato does NOT come through here anymore — it lands on vanilla's schedule
     * and vanilla's tick body does the landing; see {@code afterTick}.
     */
    @Unique
    private static void pistonDiversified$placeFinal(Level level, BlockPos pos, PistonMovingBlockEntity be) {
        level.removeBlockEntity(pos);
        be.setRemoved();
        if (level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
            BlockState moved = be.getMovedState();
            BlockState finalState = Block.updateFromNeighbourShapes(moved, level, pos);
            if (finalState.isAir()) {
                level.setBlock(pos, moved, 340);
                Block.updateOrDestroy(moved, finalState, level, pos, 3);
            } else {
                if (finalState.hasProperty(BlockStateProperties.WATERLOGGED) && finalState.getValue(BlockStateProperties.WATERLOGGED)) {
                    finalState = finalState.setValue(BlockStateProperties.WATERLOGGED, false);
                }

                level.setBlock(pos, finalState, 67);
                //? if >=1.21.2 {
                level.neighborChanged(
                    pos, finalState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(level, be.getPushDirection(), null)
                );
                //?} else {
                level.neighborChanged(pos, finalState.getBlock(), pos);
                //?}
            }
        }
    }

    //? if >=1.21.2 {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void pistonDiversified$saveFlags(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("pistonDiversifiedFast", this.pistonDiversified$fast);
        this.pistonDiversified$saveFlight(output);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void pistonDiversified$loadFlags(ValueInput input, CallbackInfo ci) {
        this.pistonDiversified$fast = input.getBooleanOr("pistonDiversifiedFast", false);
        this.pistonDiversified$loadFlight(input);
    }

    /** The value API has no long-array codec, so the record travels as a list of longs. */
    @Unique
    private void pistonDiversified$saveFlight(ValueOutput output) {
        if (this.pistonDiversified$flight.length > 0) {
            output.store("PdFlight", com.mojang.serialization.Codec.LONG.listOf(),
                java.util.Arrays.stream(this.pistonDiversified$flight).boxed().toList());
            output.putBoolean("PdFlightPrimary", this.pistonDiversified$flightPrimary);
            output.putBoolean("PdFlightWater", this.pistonDiversified$landsInWater);
        }
    }

    @Unique
    private void pistonDiversified$loadFlight(ValueInput input) {
        this.pistonDiversified$flight = input.read("PdFlight", com.mojang.serialization.Codec.LONG.listOf())
            .orElse(java.util.List.of())
            .stream()
            .mapToLong(Long::longValue)
            .toArray();
        this.pistonDiversified$flightPrimary = input.getBooleanOr("PdFlightPrimary", false);
        this.pistonDiversified$landsInWater = input.getBooleanOr("PdFlightWater", false);
    }
    //?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void pistonDiversified$saveFlags(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("pistonDiversifiedFast", this.pistonDiversified$fast);
        if (this.pistonDiversified$flight.length > 0) {
            tag.putLongArray("PdFlight", this.pistonDiversified$flight);
            tag.putBoolean("PdFlightPrimary", this.pistonDiversified$flightPrimary);
            tag.putBoolean("PdFlightWater", this.pistonDiversified$landsInWater);
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void pistonDiversified$loadFlags(CompoundTag tag, CallbackInfo ci) {
        this.pistonDiversified$fast = tag.getBoolean("pistonDiversifiedFast");
        this.pistonDiversified$flight = tag.getLongArray("PdFlight");
        this.pistonDiversified$flightPrimary = tag.getBoolean("PdFlightPrimary");
        this.pistonDiversified$landsInWater = tag.getBoolean("PdFlightWater");
    }
    //?}
}