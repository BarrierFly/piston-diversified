package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.logic.PdUpdateBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects neighbour updates (NC) into the active {@link PdUpdateBatch} instead of dispatching
 * them. Covers both the piston's explicit {@code updateNeighborsAt} calls and the ones vanilla's
 * {@code setBlock} emits for flag 1 (1.21+, {@code updateNeighborsAt}; 1.19.4, {@code blockUpdated}).
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    //? if >=1.21.2 {
    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
        at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferNeighborsOriented(BlockPos pos, Block block, Orientation orientation, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordNC(pos, block, orientation);
            ci.cancel();
        }
    }

    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
        at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordNC(pos, block, null);
            ci.cancel();
        }
    }
    //?} else {
    @Inject(method = "updateNeighborsAt", at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordNC(pos, block, null);
            ci.cancel();
        }
    }

    @Inject(method = "blockUpdated", at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferBlockUpdated(BlockPos pos, Block block, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordNC(pos, block, null);
            ci.cancel();
        }
    }
    //?}
}
