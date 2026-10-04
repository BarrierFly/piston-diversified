package dev.zcode.piston_diversified.logic;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The slice of the vanilla {@code PistonStructureResolver} API the mod's move execution needs.
 * Lets {@code ModPistonBaseBlock} run its (vanilla-copied) move flow against either the vanilla
 * resolver or a modded one (强力活塞's push-conversion resolver, 拐推's bent pull resolver, …).
 */
public interface PdResolver {
    boolean resolve();

    List<BlockPos> getToPush();

    List<BlockPos> getToDestroy();

    Direction getPushDirection();
}
