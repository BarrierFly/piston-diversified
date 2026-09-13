package dev.zcode.piston_diversified.mixin;

import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the progress fields for the fast conversion check. */
@Mixin(PistonMovingBlockEntity.class)
public interface PistonMovingBlockEntityAccessor {
    @Accessor("progress")
    float pistonDiversified$getProgress();

    @Accessor("progressO")
    float pistonDiversified$getProgressO();
}
