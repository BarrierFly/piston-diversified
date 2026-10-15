package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.ModPistonStructureResolver;
import dev.zcode.piston_diversified.logic.PdResolver;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 带罩活塞 / 带罩黏塞 — a piston wearing a five-plate hood (前言 wall + four side walls).
 *
 * <p>Geometry (2026-10-11 定稿): the hood is a whole-cell shell; the base is the remaining core
 * column, so the retracted base is visually a full block and the extended base is an 8×8×12 core
 * ({@link #getShape}). The hood sits on the base while retracted and on the head while extended.
 * The collision boxes match the models — nothing leaves its own cell.</p>
 *
 * <p>Non-sticky: 罩面隔断 — the hood faces never form sticky connections (see
 * {@code HoodStickiness}). Sticky: 五面黏 — the hood's five outer faces are sticky in both states,
 * implemented by the resolver's forced-hood branching and the sticky-hood rule, so the four side
 * neighbours of the hood cell travel with the move (the structure may be discontinuous).</p>
 */
public class HoodedPistonBlock extends ModPistonBaseBlock {
    public HoodedPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.HOODED_STICKY_PISTON_HEAD : ModBlocks.HOODED_PISTON_HEAD;
    }

    /**
     * Retracted the core column plus the hood make a full block; extended only the 8×8×12 core
     * remains (front 4px cut, the four side rails cut), so entities can stand in the side slots.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(EXTENDED)) {
            Direction facing = state.getValue(FACING);
            return PdShapes.along(facing, 4, 16, 4, 12);
        }
        return Shapes.block();
    }

    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        // fiveFaceHood only for the sticky variant: its hood pulls its side neighbours as well
        return new ModPistonStructureResolver(level, pos, direction, extending, true, false, false, 0, this.sticky);
    }
}
