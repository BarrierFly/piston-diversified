package dev.zcode.piston_diversified.logic;

import dev.zcode.piston_diversified.block.HoodedPistonBlock;
import dev.zcode.piston_diversified.block.HoodedPistonHeadBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sticky-connection rules for the 带罩(黏)活塞. Shared by {@link ModPistonStructureResolver} and
 * {@code PistonStructureResolverMixin} so the hood behaves the same whichever resolver runs.
 *
 * <ul>
 *   <li>罩面隔断 (带罩活塞, and any non-sticky hood): a hood face never forms a sticky connection,
 *       in either direction — slime/honey against the hood does not glue the hood into a structure,
 *       and the hood does not glue onto anything.</li>
 *   <li>五面黏 (带罩黏塞): the hood's five outer faces are sticky exactly like the piston's own
 *       pull face. Retracted the hood sits on the base (base cell sticky), extended it sits on the
 *       head (head cell sticky).</li>
 *   <li>Two hoods never stick to each other; a sticky hood against plain slime/honey does.</li>
 * </ul>
 */
public final class HoodStickiness {
    private HoodStickiness() {
    }

    /** True if the state is a 带罩(黏)活塞 base or head (whether or not it is sticky). */
    public static boolean isHood(BlockState state) {
        return state.getBlock() instanceof HoodedPistonBlock || state.getBlock() instanceof HoodedPistonHeadBlock;
    }

    /**
     * True if this particular hood face is sticky. The base carries the hood while retracted and
     * the head carries it while extended, so the base's EXTENDED bit decides.
     */
    public static boolean isStickyHood(BlockState state) {
        if (state.getBlock() instanceof HoodedPistonHeadBlock head) {
            return head.pdIsSticky();
        }
        if (state.getBlock() instanceof HoodedPistonBlock base) {
            return base.pdIsSticky() && !state.getValue(PistonBaseBlock.EXTENDED);
        }
        return false;
    }

    /** Slime/honey/sticky-hood — anything that acts as a sticky cell. */
    public static boolean isSticky(BlockState state) {
        return state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK) || isStickyHood(state);
    }

    /** Hood-aware replacement for the vanilla {@code canStickToEachOther}. */
    public static boolean canStickToEachOther(BlockState state1, BlockState state2) {
        boolean hood1 = isHood(state1);
        boolean hood2 = isHood(state2);
        // hood vs hood never sticks; a non-sticky hood face is a full barrier
        if (hood1 && hood2) {
            return false;
        }
        if (hood1 && !isStickyHood(state1)) {
            return false;
        }
        if (hood2 && !isStickyHood(state2)) {
            return false;
        }
        // vanilla honey/slime mutual exclusion
        if (state1.is(Blocks.HONEY_BLOCK) && state2.is(Blocks.SLIME_BLOCK)) {
            return false;
        }
        if (state1.is(Blocks.SLIME_BLOCK) && state2.is(Blocks.HONEY_BLOCK)) {
            return false;
        }
        return isSticky(state1) || isSticky(state2);
    }
}
