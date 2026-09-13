package dev.zcode.piston_diversified.mixin;

/** Fast flag for 快速活塞 moving pistons (implemented onto {@code PistonMovingBlockEntity}). */
public interface PistonDuck {
    void pistonDiversified$setFast(boolean fast);

    boolean pistonDiversified$isFast();
}
