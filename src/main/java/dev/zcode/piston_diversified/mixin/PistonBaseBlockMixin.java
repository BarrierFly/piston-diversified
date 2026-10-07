package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.block.ModPistonBaseBlock;
import dev.zcode.piston_diversified.block.ModPistonHeadBlock;
import dev.zcode.piston_diversified.block.RecursivePistonRodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Push behaviour of the modded piston family, aligned with the vanilla pistons: an extended modded
 * base is immovable (原版伸出活塞不可推), a retracted one behaves like a retracted vanilla piston
 * (可推), and the modded heads/rods are always push-resistant.
 *
 * <p>Vanilla hardcodes its own two pistons in {@code isPushable} ({@code Blocks.PISTON} /
 * {@code Blocks.STICKY_PISTON}); every other block is judged by its {@code PushReaction} and
 * block-entity status, which misclassifies ours on both ends — 1.19.4 read an extended modded base
 * as pushable (leaving dangling heads), while on the newer versions the base's
 * {@code pushReaction(BLOCK)} also made the retracted state immovable. Cancelling here covers every
 * caller: the vanilla and mod structure resolvers both route their pushability checks through this
 * method. The {@code pushReaction(BLOCK)} on the newer base/rod properties stays as a fallback for
 * anything reading the reaction directly.</p>
 */
@Mixin(PistonBaseBlock.class)
public class PistonBaseBlockMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void pistonDiversified$isPushable(BlockState state, Level level, BlockPos pos,
                                                      Direction movementDirection, boolean allowDestroy,
                                                      Direction pistonFacing,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof ModPistonBaseBlock) {
            cir.setReturnValue(!state.getValue(ModPistonBaseBlock.EXTENDED));
        } else if (state.getBlock() instanceof ModPistonHeadBlock head && !head.pdIsPushable()) {
            cir.setReturnValue(false);
        } else if (state.getBlock() instanceof RecursivePistonRodBlock) {
            cir.setReturnValue(false);
        }
    }
}
