package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.duck.PistonDuck;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the server send the block-entity update for a moving piston it drove itself.
 *
 * <p>{@code BlockEntity#getUpdatePacket} returns {@code null} and vanilla's
 * {@code PistonMovingBlockEntity} does not override it, so {@code ChunkHolder#broadcastBlockEntity}
 * never sends anything for a piston. Vanilla does not need it: the client replays the piston's own
 * block event and builds the moving pistons itself, which is also what gives them their
 * progress-0 start.</p>
 *
 * <p>Every move that does <em>not</em> go through a block event — 马铃薯活塞's flight step, the
 * 无活塞 pushes behind 抛射/后坐/拐推, the tick chains — has no such replay. There the moving piston
 * reaches the client as an invisible block with no entity (bit 2 in {@code SYNC_MOVING_PISTON}
 * marks the block only) and simply is not drawn. The variants mark those entities with
 * {@link PistonDuck#pistonDiversified$setNeedsClientSync} and this sends their update tag, so the
 * client finally has something to render.</p>
 *
 * <p>Timing is the other half: a moving piston created before its level's
 * {@code chunkSource.tick} is broadcast in that same round, and {@code saveAdditional} writes
 * {@code progressO} — still 0, so the client gets the full slide. Created after that (the flight
 * step used to run at end-of-tick), the packet would carry 0.5 and the slide is halved.</p>
 */
@Mixin(BlockEntity.class)
public class BlockEntityMixin {
    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void pistonDiversified$sendReplaylessMove(CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        if ((Object) this instanceof PistonDuck duck && duck.pistonDiversified$needsClientSync()) {
            cir.setReturnValue(ClientboundBlockEntityDataPacket.create((BlockEntity) (Object) this));
        }
    }
}