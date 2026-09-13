package dev.zcode.piston_diversified.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shape helpers for the custom-geometry variants, using the same convention as the vanilla
 * piston head: distance is measured from the block's outer (tip) face along {@code facing}
 * towards the piston base, so 0..4 is the plate at the tip and 16..20 would poke into the
 * previous (base) cell.
 */
public final class PdShapes {
    private PdShapes() {
    }

    /** Box spanning {@code from}..{@code to} pixels along {@code facing}, cross-section {@code crossMin}..{@code crossMax} on the other axes. */
    public static VoxelShape along(Direction facing, double from, double to, double crossMin, double crossMax) {
        return switch (facing) {
            case DOWN -> Block.box(crossMin, from, crossMin, crossMax, to, crossMax);
            case UP -> Block.box(crossMin, 16 - to, crossMin, crossMax, 16 - from, crossMax);
            case NORTH -> Block.box(crossMin, crossMin, from, crossMax, crossMax, to);
            case SOUTH -> Block.box(crossMin, crossMin, 16 - to, crossMax, crossMax, 16 - from);
            case WEST -> Block.box(from, crossMin, crossMin, to, crossMax, crossMax);
            case EAST -> Block.box(16 - to, crossMin, crossMin, 16 - from, crossMax, crossMax);
        };
    }

    /** Full-width slab spanning {@code from}..{@code to} pixels along {@code facing}. */
    public static VoxelShape slab(Direction facing, double from, double to) {
        return along(facing, from, to, 0, 16);
    }

    /** Rod with the given cross-section width, centered, spanning {@code from}..{@code to} along {@code facing}. */
    public static VoxelShape rod(Direction facing, double from, double to, double width) {
        double min = 8 - width / 2;
        return along(facing, from, to, min, 8 + width / 2);
    }
}
