package dev.zcode.piston_diversified.duck;

/**
 * Extra data carried on a vanilla {@code PistonMovingBlockEntity} by the mod:
 * <ul>
 *   <li>the 快速活塞 "fast" flag;</li>
 *   <li>the 马铃薯活塞 flight record (the cells making up the flying structure), whether this
 *       cell leads it, and whether it landed in water (to restore waterlogging).</li>
 * </ul>
 *
 * <p>Implemented onto {@code PistonMovingBlockEntity} itself, so moving pistons keep ticking
 * through the vanilla machinery — a separate block-entity type would have to be wired into the
 * vanilla ticker by hand, and vanilla's own entity type only accepts its own block.</p>
 */
public interface PistonDuck {
    void pistonDiversified$setFast(boolean fast);

    boolean pistonDiversified$isFast();

    void pistonDiversified$setFlight(long[] record, boolean primary, boolean landsInWater);

    long[] pistonDiversified$getFlight();

    boolean pistonDiversified$isFlightPrimary();

    boolean pistonDiversified$landsInWater();
}