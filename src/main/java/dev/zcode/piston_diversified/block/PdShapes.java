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

    /**
     * The 拐推 head's arm: a 4x4 bar running along {@code facing} from {@code from} to {@code to},
     * measured from the facing tip like everything else here. It is nudged against the plate
     * ({@code plateOn} is that BEND) — centred, it would miss an east/west/up/down plate by 2px and
     * the head would read as a plate floating beside a bar. {@code gen_assets.bent_head_model}
     * shifts the generated model the same way, and the two agree on every BEND a piston can hold.
     */
    public static VoxelShape arm(Direction facing, Direction plateOn, double from, double to) {
        double xMin = 6.0;
        double xMax = 10.0;
        double yMin = 6.0;
        double yMax = 10.0;
        if (plateOn == Direction.EAST) {
            xMin = 8.0;
            xMax = 12.0;
        } else if (plateOn == Direction.WEST) {
            xMin = 4.0;
            xMax = 8.0;
        } else if (plateOn == Direction.UP) {
            yMin = 8.0;
            yMax = 12.0;
        } else if (plateOn == Direction.DOWN) {
            yMin = 4.0;
            yMax = 8.0;
        }
        return switch (facing) {
            case NORTH -> Block.box(xMin, yMin, from, xMax, yMax, to);
            case SOUTH -> Block.box(xMin, yMin, 16.0 - to, xMax, yMax, 16.0 - from);
            case EAST -> Block.box(16.0 - to, yMin, 6.0, 16.0 - from, yMax, 10.0);
            case WEST -> Block.box(from, yMin, 6.0, to, yMax, 10.0);
            case UP -> Block.box(xMin, 16.0 - to, 6.0, xMax, 16.0 - from, 10.0);
            case DOWN -> Block.box(xMin, from, 6.0, xMax, to, 10.0);
        };
    }
}
