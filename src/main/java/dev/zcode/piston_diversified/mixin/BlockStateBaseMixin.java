package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.logic.PdUpdateBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.20.3 {
import net.minecraft.server.level.ServerLevel;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Collects shape updates (PP) and extra updates into the active {@link PdUpdateBatch}.
 *
 * <p>Every {@code updateNeighbourShapes} / {@code updateIndirectNeighbourShapes} call funnels
 * through these four-argument overloads (the three-argument versions delegate here). Catching them
 * at the state level is what makes the moving-piston placement's shape triple — impossible to
 * intercept at the piston's own call site, since {@code setBlock} emits it — land in the batch.
 * {@code affectNeighborsAfterRemoval} likewise covers both the piston's explicit stage-tail call
 * and the 1.21+ implicit call inside the chunk write.</p>
 */
@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public class BlockStateBaseMixin {
    @Inject(method = "updateNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V",
        at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferNeighbourShapes(LevelAccessor level, BlockPos pos, int flags, int recursionLeft, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordPP(PdUpdateBatch.Kind.PP_NEIGHBOUR, pos);
            ci.cancel();
        }
    }

    @Inject(method = "updateIndirectNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V",
        at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferIndirectNeighbourShapes(LevelAccessor level, BlockPos pos, int flags, int recursionLeft, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordPP(PdUpdateBatch.Kind.PP_INDIRECT, pos);
            ci.cancel();
        }
    }

    //? if >=1.20.3 {
    @Inject(method = "affectNeighborsAfterRemoval(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$deferExtra(ServerLevel level, BlockPos pos, boolean movedByPiston, CallbackInfo ci) {
        if (PdUpdateBatch.isActive()) {
            PdUpdateBatch.recordExtra(pos, (BlockState) (Object) this, movedByPiston);
            ci.cancel();
        }
    }
    //?}
}
