package dev.zcode.piston_diversified.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the client build the block entity for a {@code Blocks#MOVING_PISTON} it is told about.
 *
 * <p>Vanilla's {@code MovingPistonBlock#newBlockEntity} returns {@code null}, which is fine there:
 * the client only ever learns about a moving piston by replaying the piston's own block event, and
 * that replay builds the entity itself. As soon as a move happens <em>outside</em> that channel
 * (马铃薯活塞's flight step, a piston-less push, a tick chain) the client gets the block update and
 * nothing else — the block is {@code RenderShape#INVISIBLE} and has no entity, so the push is
 * simply not drawn. And it is not enough for the server to start sending the entity data either:
 * {@code BlockGetter#getBlockEntity(pos, type)} only finds an entity that already exists, so
 * {@code ClientPacketListener#handleBlockEntityData} drops the packet and the cell stays empty.</p>
 *
 * <p>Returning an instance closes both halves. Until the server's update tag arrives it is inert
 * (no moved state, ticking suppressed — see {@code PistonMovingBlockEntityMixin}), which is exactly
 * what an absent entity used to be, so nothing else observes the difference.</p>
 */
@Mixin(MovingPistonBlock.class)
public class MovingPistonBlockMixin {
    @Inject(method = "newBlockEntity", at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$newBlockEntity(BlockPos pos, BlockState state,
                                                 CallbackInfoReturnable<BlockEntity> cir) {
        cir.setReturnValue(new PistonMovingBlockEntity(pos, state));
    }
}