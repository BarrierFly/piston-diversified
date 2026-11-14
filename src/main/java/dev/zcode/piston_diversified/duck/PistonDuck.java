package dev.zcode.piston_diversified.duck;

/**
 * Extra data carried on a vanilla {@code PistonMovingBlockEntity} by the mod:
 * <ul>
 *   <li>the 快速活塞 "fast" flag;</li>
 *   <li>the 马铃薯活塞 flight record (the cells making up the flying structure), whether this
 *       cell leads it, and whether it landed in water (to restore waterlogging);</li>
 *   <li>the 镐活塞's carried pickaxe — vanilla moving pistons carry only a block state, and the
 *       landing recreates a default block entity, so the stack must ride along and be written
 *       back once the cell lands (see {@code PistonMovingBlockEntityMixin});</li>
 *   <li>the 客户端同步 flag — whether this moving piston still needs its block entity sent to the
 *       client (see {@code PistonMovingBlockEntityMixin#getUpdatePacket}).</li>
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

    /** Mark this moving piston as one the client cannot replay on its own. */
    void pistonDiversified$setNeedsClientSync(boolean needsSync);

    boolean pistonDiversified$needsClientSync();

    /** The 镐活塞 stack this flying cell must write back into its block entity when it lands. */
    void pistonDiversified$setCarriedPickaxe(net.minecraft.world.item.ItemStack stack);

    net.minecraft.world.item.ItemStack pistonDiversified$getCarriedPickaxe();
}