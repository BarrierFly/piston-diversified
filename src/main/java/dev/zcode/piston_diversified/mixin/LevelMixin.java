package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.block.PickaxePistonBlock;
import dev.zcode.piston_diversified.duck.PistonDuck;
import dev.zcode.piston_diversified.entity.PickaxePistonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 镐活塞: attach the carried pickaxe stack to a moving piston created for a pickaxe piston cell.
 *
 * <p>Vanilla's {@code moveBlocks} builds moving pistons from the pushed block state alone — no
 * block entity data ever rides along (vanilla simply refuses to push block-entity blocks it cares
 * about). When the cell lands, {@code LevelChunk} builds a fresh default block entity and the
 * carried pickaxe would silently reset to the default netherite pickaxe. Every creator — the
 * vanilla {@code PistonBaseBlock}, the mod's {@code moveBlocksResolved}, the potato flight and the
 * piston-less push — ends in {@code Level#setBlockEntity}, so one hook here covers them all: at
 * that moment the source cell still holds the original block with its entity (sources are cleared
 * only afterwards, back-to-front), so the stack can be read straight off it.</p>
 *
 * <p>Only non-source moving pistons qualify: a source piston is the retracting base itself (the
 * slide direction would point away from where the data lives), and the extending head carries a
 * head state, never a pickaxe piston. The reverse of the entity's travel direction locates the
 * source cell — for a retract pull the entity's facing is the piston's, so the direction flips.</p>
 */
@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void pistonDiversified$carryPickaxe(BlockEntity be, CallbackInfo ci) {
        if (!(be instanceof PistonMovingBlockEntity pmbe)
            || pmbe.isSourcePiston()
            || ((Level) (Object) this).isClientSide()) {
            return;
        }
        BlockState moved = pmbe.getMovedState();
        if (moved == null || !(moved.getBlock() instanceof PickaxePistonBlock)) {
            return;
        }
        Level self = (Level) (Object) this;
        Direction slide = pmbe.isExtending() ? pmbe.getDirection() : pmbe.getDirection().getOpposite();
        BlockPos source = pmbe.getBlockPos().relative(slide.getOpposite());
        if (self.getBlockState(source).getBlock() instanceof PickaxePistonBlock
            && self.getBlockEntity(source) instanceof PickaxePistonBlockEntity pickaxeBe) {
            ((PistonDuck) pmbe).pistonDiversified$setCarriedPickaxe(pickaxeBe.getPickaxe());
        }
    }
}
