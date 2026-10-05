package dev.zcode.piston_diversified.logic;

import com.google.common.collect.Lists;
import dev.zcode.piston_diversified.block.ModPistonBaseBlock;
import dev.zcode.piston_diversified.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 马铃薯活塞's structure selection (the Floatater rules from af2024, adapted). BFS from the
 * cell in front of the (real or virtual) piston:
 * <ol>
 *   <li>an irreplaceable block in front of a pushed block is pushed;</li>
 *   <li>blocks whose interaction shapes touch are pushed — piston bases only connect through
 *       their plate face, the other five faces never join this way;</li>
 *   <li>blocks glued through a sticky face are pushed: sticky-piston plate faces, slime and
 *       honey on all faces (honey pistons count as honey); air and fluids included.</li>
 * </ol>
 * Mod rules on top: air never joins and stops propagation; the initiating potato piston is
 * invisible to the scan; block entities never join through rules 2/3 (rule 1 destroys popped
 * ones and fails on the rest); slime×honey contact of different kinds disconnects rules 2/3;
 * rule 3 may pull fluids along. Unpushable members (obsidian…) fail the push (推不动就不动).
 * Hard cap 1024 cells; the push budget (potatoPushLimit gamerule, default 32) counts non-fluid
 * cells.
 */
public final class PotatoStructureResolver {
    private static final int HARD_CELL_CAP = 1024;

    /** How a structure member travels (and what happens to the cell it vacates). */
    public enum MemberKind {
        /** An ordinary block: travels as-is, its vacated cell becomes air. */
        NORMAL,
        /** Waterlogged block: travels waterless, its vacated cell keeps water. */
        WATERLOGGED,
        /** A fluid pulled in by rule 3: travels as fluid, its vacated cell is emptied. */
        FLUID,
    }

    public record Member(BlockPos pos, BlockState state, MemberKind kind) {
    }

    private final Level level;
    private final Direction pushDirection;
    private final BlockPos initiator;
    private final int limit;
    private final List<Member> members = Lists.newArrayList();
    private final List<BlockPos> toDestroy = Lists.newArrayList();
    private final Set<BlockPos> memberSet = new HashSet<>();
    private boolean failed;
    private String failReason = "";

    public PotatoStructureResolver(Level level, BlockPos initiator, Direction pushDirection, int limit) {
        this.level = level;
        this.initiator = initiator;
        this.pushDirection = pushDirection;
        this.limit = limit;
    }

    /**
     * Selects the structure starting at {@code startPos} (the cell in front of the piston). An
     * empty structure is a success (plain empty push); {@code false} means the push fails.
     */
    public boolean resolve(BlockPos startPos) {
        this.members.clear();
        this.memberSet.clear();
        this.toDestroy.clear();
        this.failed = false;
        this.failReason = "";

        BlockState startState = this.level.getBlockState(startPos);
        if (startPos.equals(this.initiator) || this.canReplace(startState)) {
            return true; // empty structure: plain empty push
        }

        int usedBudget = 0;
        List<BlockPos> queue = new ArrayList<>();
        Set<BlockPos> enqueued = new HashSet<>();
        queue.add(startPos);
        enqueued.add(startPos);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < queue.size(); index++) {
            BlockPos pos = queue.get(index);
            BlockState state = this.level.getBlockState(pos);
            if (this.failed) {
                return false;
            }

            MemberKind kind;
            if (this.canReplace(state)) {
                kind = MemberKind.FLUID; // fluid cell pulled in by rule 3
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED)) {
                kind = MemberKind.WATERLOGGED; // travels waterless, leaves water behind
            } else {
                // rule 1 carries unpushable blocks too (obsidian, bedrock, ...) — an
                // irreplaceable block in front of a pushed block simply joins the structure.
                // Destroy-on-push blocks join the same way: the structure is carried whole, so a
                // torch in front travels with the rest instead of being popped out of it.
                kind = MemberKind.NORMAL;
            }

            this.memberSet.add(pos);
            this.members.add(new Member(pos, state, kind));
            if (kind != MemberKind.FLUID) {
                usedBudget++;
                if (usedBudget > this.limit) {
                    return this.fail("structure exceeds potatoPushLimit (" + this.limit + ")");
                }
            }
            if (this.members.size() > HARD_CELL_CAP) {
                return this.fail("structure exceeds the hard cell cap");
            }

            VoxelShape shape = state.getShape(this.level, pos);
            for (Direction side : Direction.values()) {
                cursor.setWithOffset(pos, side);
                BlockPos neighborPos = cursor.immutable();
                if (enqueued.contains(neighborPos) || this.failed) {
                    continue;
                }
                BlockState neighborState = this.level.getBlockState(neighborPos);
                if (this.connects(pos, state, shape, side, neighborPos, neighborState)) {
                    enqueued.add(neighborPos);
                    queue.add(neighborPos);
                }
            }
        }
        return !this.failed;
    }

    /** Whether the neighbour joins the structure through one of the three rules. */
    private boolean connects(BlockPos pos, BlockState state, VoxelShape shape, Direction side,
                             BlockPos neighborPos, BlockState neighborState) {
        if (neighborPos.equals(this.initiator)) {
            return false; // the initiating potato piston itself never joins
        }
        if (neighborState.isAir()) {
            return false; // air never joins and blocks propagation
        }

        boolean hasBlockEntity = neighborState.hasBlockEntity();
        if (side == this.pushDirection && !this.canReplace(neighborState)) {
            // rule 1: an irreplaceable block in front of a pushed block is pushed — except block
            // entities, which are destroyed (popped) or stop the push outright
            if (hasBlockEntity) {
                if (neighborState.getPistonPushReaction() == PushReaction.DESTROY) {
                    this.toDestroy.add(neighborPos);
                } else {
                    this.fail("block entity blocks the structure front: " + neighborState.getBlock());
                }
                return false;
            }
            if (this.outOfWorld(neighborPos)) {
                return false;
            }
            return true;
        }

        if (hasBlockEntity) {
            return false; // rules 2/3 never pick up block entities (侧/后方连上则忽略)
        }

        boolean stickyGlued = this.isStickyFace(state, side) || this.isStickyFace(neighborState, side.getOpposite());
        boolean mixedSlimeHoney = this.slimeHoneyClash(state, side, neighborState);

        if (stickyGlued && !mixedSlimeHoney) {
            // rule 3: sticky faces glue anything, air and fluids included
            if (this.outOfWorld(neighborPos)) {
                return false;
            }
            return true;
        }

        // rule 2: interaction shapes touching — piston bases only connect via their plate face
        if (mixedSlimeHoney) {
            return false;
        }
        if (this.isPistonBaseWithExcludedFace(state, side)
            || this.isPistonBaseWithExcludedFace(neighborState, side.getOpposite())) {
            return false;
        }
        VoxelShape neighborShape = neighborState.getShape(this.level, neighborPos);
        return this.areShapesConnected(shape, neighborShape, side);
    }

    /**
     * af2024's interaction-shape test: the two cells' touching faces must overlap. Written on
     * raw AABBs because {@code Shapes.getFaceShape} only exists on the newest versions.
     */
    private boolean areShapesConnected(VoxelShape shape, VoxelShape neighborShape, Direction side) {
        if (shape.isEmpty() || neighborShape.isEmpty()) {
            return false;
        }
        List<net.minecraft.world.phys.AABB> mine = shape.toAabbs();
        List<net.minecraft.world.phys.AABB> theirs = neighborShape.toAabbs();
        for (net.minecraft.world.phys.AABB a : mine) {
            for (net.minecraft.world.phys.AABB b : theirs) {
                if (faceOverlap(a, b, side)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final float FACE_EPS = 1.0E-5F;

    /**
     * Whether box {@code a}'s {@code side} face touches box {@code b}'s opposite face. Both AABBs
     * are in their own cell's local space (0..1): the touch means a's far face sits at the shared
     * cell boundary and b's near face sits at the same boundary from the other side.
     */
    private static boolean faceOverlap(net.minecraft.world.phys.AABB a, net.minecraft.world.phys.AABB b, Direction side) {
        boolean touching = switch (side) {
            case EAST -> a.maxX > 1.0F - FACE_EPS && b.minX < FACE_EPS;
            case WEST -> a.minX < FACE_EPS && b.maxX > 1.0F - FACE_EPS;
            case UP -> a.maxY > 1.0F - FACE_EPS && b.minY < FACE_EPS;
            case DOWN -> a.minY < FACE_EPS && b.maxY > 1.0F - FACE_EPS;
            case SOUTH -> a.maxZ > 1.0F - FACE_EPS && b.minZ < FACE_EPS;
            case NORTH -> a.minZ < FACE_EPS && b.maxZ > 1.0F - FACE_EPS;
        };
        if (!touching) {
            return false;
        }
        // the contact patch must overlap on the two tangential axes (strict: edge-touch is not overlap)
        return switch (side.getAxis()) {
            case X -> a.minY < b.maxY && a.maxY > b.minY && a.minZ < b.maxZ && a.maxZ > b.minZ;
            case Y -> a.minX < b.maxX && a.maxX > b.minX && a.minZ < b.maxZ && a.maxZ > b.minZ;
            default -> a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY;
        };
    }

    /** Out-of-world cells can never be carried; the front walk then reports them as blockers. */
    private boolean outOfWorld(BlockPos pos) {
        return pos.getY() < dev.zcode.piston_diversified.PdHelpers.minBuildHeight(this.level)
            || pos.getY() > dev.zcode.piston_diversified.PdHelpers.maxBuildHeight(this.level)
            || !this.level.getWorldBorder().isWithinBounds(pos);
    }

    /** Piston bases join rule 2 only through their plate face (盖面), never the other five. */
    private boolean isPistonBaseWithExcludedFace(BlockState state, Direction face) {
        if (!(state.getBlock() instanceof PistonBaseBlock)) {
            return false;
        }
        return state.getValue(DirectionalBlock.FACING) != face;
    }

    /** Sticky-side rule 3 faces: sticky piston plates, slime and honey everywhere. */
    private boolean isStickyFace(BlockState state, Direction face) {
        Block block = state.getBlock();
        if (block == Blocks.SLIME_BLOCK || block == Blocks.HONEY_BLOCK) {
            return true;
        }
        if (block instanceof ModPistonBaseBlock modded && modded.pdIsSticky()) {
            return state.getValue(DirectionalBlock.FACING) == face;
        }
        return state.is(Blocks.STICKY_PISTON) && state.getValue(DirectionalBlock.FACING) == face;
    }

    /** True when the two touching faces are sticky of different kinds (slime×honey never binds). */
    private boolean slimeHoneyClash(BlockState state, Direction side, BlockState neighborState) {
        int a = this.honeySlimeKind(state, side);
        int b = this.honeySlimeKind(neighborState, side.getOpposite());
        return a != 0 && b != 0 && a != b;
    }

    /** 0 = none, 1 = slime-kind face, 2 = honey-kind face (honey piston plates count as honey). */
    private int honeySlimeKind(BlockState state, Direction face) {
        Block block = state.getBlock();
        if (block == Blocks.SLIME_BLOCK) {
            return 1;
        }
        if (block == Blocks.HONEY_BLOCK || block == ModBlocks.HONEY_PISTON) {
            return 2;
        }
        if (block instanceof ModPistonBaseBlock modded && modded.pdIsSticky()
            && state.getValue(DirectionalBlock.FACING) == face && block == ModBlocks.HONEY_PISTON) {
            return 2;
        }
        return 0;
    }

    private boolean canReplace(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    private boolean fail(String reason) {
        this.failed = true;
        this.failReason = reason;
        return false;
    }

    public boolean isFailed() {
        return this.failed;
    }

    public String getFailReason() {
        return this.failReason;
    }

    public List<Member> getMembers() {
        return this.members;
    }

    public List<BlockPos> getToDestroy() {
        return this.toDestroy;
    }

    public Set<BlockPos> getMemberSet() {
        return this.memberSet;
    }

    /** The structure record: every member as an offset relative to {@code origin}, packed. */
    public long[] record(BlockPos origin) {
        return record(this.members, origin);
    }

    /** Same packing for an arbitrary member list (used when a flight step re-records). */
    public static long[] record(List<Member> members, BlockPos origin) {
        long[] record = new long[members.size()];
        for (int i = 0; i < members.size(); i++) {
            record[i] = packRelative(origin, members.get(i).pos());
        }
        return record;
    }

    public static long packRelative(BlockPos origin, BlockPos pos) {
        int x = pos.getX() - origin.getX() + 512;
        int y = pos.getY() - origin.getY() + 512;
        int z = pos.getZ() - origin.getZ() + 512;
        return ((long) x << 22) | ((long) y << 12) | ((long) z << 2);
    }

    public static BlockPos unpackRelative(BlockPos origin, long packed) {
        int x = ((int) (packed >> 22) & 0x3FF) - 512;
        int y = ((int) (packed >> 12) & 0x3FF) - 512;
        int z = ((int) (packed >> 2) & 0x3FF) - 512;
        return origin.offset(x, y, z);
    }
}
