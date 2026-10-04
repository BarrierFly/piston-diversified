package dev.zcode.piston_diversified;

import dev.zcode.piston_diversified.logic.DyeConversions;
import dev.zcode.piston_diversified.logic.PotatoFlightQueue;
import dev.zcode.piston_diversified.registry.ModBlockEntities;
import dev.zcode.piston_diversified.registry.ModBlocks;
import dev.zcode.piston_diversified.registry.ModEntities;
import dev.zcode.piston_diversified.registry.ModRecipes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 活塞多样化 (Piston Diversified) — mod v1 + v2 batch.
 *
 * <p>v1: honey, projectile, chain (x2), loop, wind charge, silent, recoil, end rod, skull,
 * directional QC (x2), observer (x2), redstone end rod, long push, weak, fast (x2).</p>
 * <p>v2: potato, pickaxe, recursive (x2), turn push (x2), wall merge (x2), 0-tick (x2), gravity,
 * strong (x3 tiers).</p>
 */
public class PistonDiversified implements ModInitializer {
    public static final String MOD_ID = "piston_diversified";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModRecipes.init();
        DyeConversions.register();
        ImpactDebugCommand.register();
        // the potato piston's flight events live in the mod's own queue (block-independent)
        ServerTickEvents.END_SERVER_TICK.register(server -> PotatoFlightQueue.tick(server.overworld()));
        LOGGER.info("Piston Diversified loaded: {} piston variants", ModBlocks.variantCount());
    }
}