package dev.zcode.piston_diversified.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.2 {
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

/**
 * 快速活塞 support: moving pistons flagged "fast" convert to their final block the tick the
 * movement progress reaches 1.0, instead of waiting one more tick for {@code progressO >= 1.0}.
 */
@Mixin(PistonMovingBlockEntity.class)
public class PistonMovingBlockEntityMixin implements PistonDuck {
    @Unique
    private boolean pistonDiversified$fast;

    @Override
    public void pistonDiversified$setFast(boolean fast) {
        this.pistonDiversified$fast = fast;
    }

    @Override
    public boolean pistonDiversified$isFast() {
        return this.pistonDiversified$fast;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void pistonDiversified$convertEarly(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity be, CallbackInfo ci) {
        if (!((PistonDuck) be).pistonDiversified$isFast() || level.isClientSide()) {
            return;
        }

        // Only convert right at the moment progress reached 1.0 this tick.
        if (((PistonMovingBlockEntityAccessor) be).pistonDiversified$getProgressO() < 1.0F
            && ((PistonMovingBlockEntityAccessor) be).pistonDiversified$getProgress() >= 1.0F) {
            pistonDiversified$placeFinal(level, pos, be);
        }
    }

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
    private void pistonDiversified$saveFast(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("pistonDiversifiedFast", this.pistonDiversified$fast);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void pistonDiversified$loadFast(ValueInput input, CallbackInfo ci) {
        this.pistonDiversified$fast = input.getBooleanOr("pistonDiversifiedFast", false);
    }
    //?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void pistonDiversified$saveFast(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("pistonDiversifiedFast", this.pistonDiversified$fast);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void pistonDiversified$loadFast(CompoundTag tag, CallbackInfo ci) {
        this.pistonDiversified$fast = tag.getBoolean("pistonDiversifiedFast");
    }
    //?}
}
