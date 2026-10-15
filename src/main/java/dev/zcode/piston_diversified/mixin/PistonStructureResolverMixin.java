package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.logic.HoodStickiness;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches the vanilla {@code PistonStructureResolver} the hood connection rules, so a vanilla or
 * modded piston pushing a 带罩(黏)活塞 sees the same 罩面隔断 / 五面黏 as the 带罩 piston's own
 * resolver ({@link dev.zcode.piston_diversified.logic.ModPistonStructureResolver}). The two
 * methods are static and private; both are replaced wholesale by {@link HoodStickiness}, which
 * reproduces the vanilla slime/honey behaviour exactly for every non-hood state.
 */
@Mixin(net.minecraft.world.level.block.piston.PistonStructureResolver.class)
public class PistonStructureResolverMixin {
    @Inject(method = "isSticky(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private static void pistonDiversified$isSticky(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(HoodStickiness.isSticky(state));
    }

    @Inject(method = "canStickToEachOther(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private static void pistonDiversified$canStickToEachOther(BlockState state1, BlockState state2, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(HoodStickiness.canStickToEachOther(state1, state2));
    }
}
