package dev.zcode.piston_diversified;

import dev.zcode.piston_diversified.logic.DyeConversions;
import dev.zcode.piston_diversified.registry.ModBlocks;
import dev.zcode.piston_diversified.registry.ModEntities;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 活塞多样化 (Piston Diversified) — mod v1 batch.
 *
 * <p>v1 contains: honey, projectile, chain (x2), loop, wind charge, silent, recoil,
 * end rod, skull, directional QC (x2), observer (x2), redstone end rod, long push,
 * weak, fast (x2). The remaining 11 pistons are planned for mod v2.</p>
 */
public class PistonDiversified implements ModInitializer {
    public static final String MOD_ID = "piston_diversified";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModEntities.init();
        DyeConversions.register();
        LOGGER.info("Piston Diversified loaded: {} piston variants", ModBlocks.variantCount());
    }
}
