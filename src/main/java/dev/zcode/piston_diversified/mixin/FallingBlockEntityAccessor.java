package dev.zcode.piston_diversified.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private falling-block state so {@code ProjectileBlockEntity} can be built subclassed. */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityAccessor {
    @Accessor("blockState")
    BlockState pistonDiversified$getBlockState();

    @Accessor("blockState")
    void pistonDiversified$setBlockState(BlockState state);

    @Accessor("cancelDrop")
    boolean pistonDiversified$getCancelDrop();
}
