package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 墙并活塞 / 墙并黏塞 — side-by-side same-facing pistons whose wall-post rods connect them.
 * While any base of the connected group carries a signal none of the heads retract; once every
 * base is unpowered, the first piston whose retract event runs coordinates the whole group:
 * everyone retracts the same tick, and the sticky variant pulls only if every member's pull
 * resolves and the summed structure size fits the summed limits (12 per sticky member) —
 * otherwise all members retract their heads without pulling (仅收回活塞头不拉方块).
 *
 * <p>Geometry: the retracted base itself shows a wall-centre-post rod poking half a block past
 * the plate (收回时超出活塞盖半格) — model and collision both belong to the base block, reaching
 * into the front cell without occupying it. The extended head carries the matching wall-post rod
 * ({@link WallMergePistonHeadBlock}), so the posts form a continuous wall in both states.</p>
 */
public class WallMergePistonBlock extends ModPistonBaseBlock {
    private static final int GROUP_LIMIT = 64;

    public WallMergePistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.WALL_MERGE_STICKY_PISTON_HEAD : ModBlocks.WALL_MERGE_PISTON_HEAD;
    }

    /** Retracted shapes per facing: full base plus the wall post reaching 8px past the front face. */
    private static VoxelShape retractedShape(Direction facing) {
        return Shapes.or(Shapes.block(), PdShapes.rod(facing, -8, 0, 8));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(EXTENDED)) {
            return super.getShape(state, level, pos, context);
        }
        return retractedShape(state.getValue(FACING));
    }

    // ------------------------------------------------------------- group logic

    /** Same-facing wall-merge pistons side by side (perpendicular to the facing), BFS from here. */
    public List<BlockPos> collectGroup(Level level, BlockPos pos, Direction facing) {
        List<BlockPos> group = new ArrayList<>();
        group.add(pos.immutable());
        for (int i = 0; i < group.size() && group.size() < GROUP_LIMIT; i++) {
            BlockPos current = group.get(i);
            for (Direction side : Direction.values()) {
                if (side.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos next = current.relative(side);
                if (group.contains(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() instanceof WallMergePistonBlock
                    && nextState.getValue(FACING) == facing
                    && group.size() < GROUP_LIMIT) {
                    group.add(next);
                }
            }
        }
        return group;
    }

    /** Any connected base still powered → no member may retract. */
    private boolean anyGroupMemberPowered(Level level, BlockPos pos, Direction facing) {
        for (BlockPos member : this.collectGroup(level, pos, facing)) {
            BlockState memberState = level.getBlockState(member);
            if (memberState.getBlock() instanceof WallMergePistonBlock memberPiston
                && memberPiston.hasPowerSignal(level, member, facing)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canRetract(Level level, BlockPos pos, BlockState state) {
        return !this.anyGroupMemberPowered(level, pos, state.getValue(FACING));
    }

    // ------------------------------------------------------------- coordinated retract

    @Override
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id) {
        List<BlockPos> group = this.collectGroup(level, pos, direction);
        if (group.size() <= 1) {
            super.executeRetract(level, pos, direction, state, id);
            return;
        }

        boolean pullAllowed = this.computeGroupPullAllowed(level, group, direction);
        for (BlockPos memberPos : group) {
            BlockState memberState = level.getBlockState(memberPos);
            if (!(memberState.getBlock() instanceof WallMergePistonBlock member)
                || !memberState.getValue(EXTENDED)) {
                continue;
            }
            // Everyone retracts right now, on the server AND on the client: the client used to
            // bail out to a single-piston retract, which left every other group member's head
            // standing there forever (残留活塞头). It reaches the same verdict below, so both
            // sides agree. Its own queued retract event self-cancels later — the cell is a moving
            // piston by then.
            member.executeRetract(level, memberPos, direction, memberState, id, pullAllowed);
        }
    }

    /**
     * Every sticky member's pull must resolve individually and the summed structure sizes must
     * fit the summed limits (12 per sticky member); any failure means nobody pulls. Non-sticky
     * groups never pull.
     *
     * <p>Runs on both sides: the client has to reach the same verdict or it would animate a pull
     * the server refused (or the other way round). Heads are cleared first because a piston head
     * is push-resistant and would fail every member's resolve.</p>
     */
    private boolean computeGroupPullAllowed(Level level, List<BlockPos> group, Direction facing) {
        if (!this.sticky) {
            return false;
        }
        int stickyMembers = 0;
        int total = 0;
        for (BlockPos memberPos : group) {
            BlockState memberState = level.getBlockState(memberPos);
            if (!(memberState.getBlock() instanceof WallMergePistonBlock member) || !member.sticky) {
                continue;
            }
            stickyMembers++;
            this.clearHeadCell(level, memberPos, facing);
            PistonStructureResolver resolver = new PistonStructureResolver(level, memberPos, facing, false);
            if (!resolver.resolve()) {
                return false; // a member alone cannot pull its structure: nobody pulls
            }
            total += resolver.getToPush().size();
        }
        return stickyMembers > 0 && total <= 12 * stickyMembers;
    }
}
