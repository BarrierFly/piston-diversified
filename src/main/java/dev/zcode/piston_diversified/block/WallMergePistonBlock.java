package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 墙并活塞 / 墙并黏塞 — side-by-side same-facing pistons whose wall-post rods connect them.
 * While any base of the connected group carries a signal none of the heads retract; once every
 * base is unpowered, the first piston whose retract event runs coordinates the whole group:
 * everyone retracts the same tick, and the sticky variant pulls only if every member's pull
 * resolves and the summed structure size fits the summed limits (12 per sticky member) —
 * otherwise all members retract their heads without pulling (仅收回活塞头不拉方块).
 *
 * <p>Geometry: vanilla base, a wall-post head ({@link WallMergePistonHeadBlock}), plus a
 * {@link WallMergeRodBlock} nub poking half a block past the plate while retracted.</p>
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
        if (!(level instanceof ServerLevel serverLevel)) {
            super.executeRetract(level, pos, direction, state, id);
            return;
        }
        List<BlockPos> group = this.collectGroup(level, pos, direction);
        if (group.size() <= 1) {
            super.executeRetract(level, pos, direction, state, id);
            return;
        }

        boolean pullAllowed = this.computeGroupPullAllowed(serverLevel, group, direction);
        for (BlockPos memberPos : group) {
            BlockState memberState = level.getBlockState(memberPos);
            if (!(memberState.getBlock() instanceof WallMergePistonBlock member)
                || !memberState.getValue(EXTENDED)) {
                continue;
            }
            // every member retracts right now; its own queued retract event self-cancels later
            // because the block at its position has already changed by then
            member.executeRetract(serverLevel, memberPos, direction, memberState, id, pullAllowed);
            member.restoreRodNub(serverLevel, memberPos);
        }
    }

    /**
     * Every sticky member's pull must resolve individually and the summed structure sizes must
     * fit the summed limits (12 per sticky member); any failure means nobody pulls. Non-sticky
     * groups never pull.
     */
    private boolean computeGroupPullAllowed(ServerLevel level, List<BlockPos> group, Direction facing) {
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
            PistonStructureResolver resolver = new PistonStructureResolver(level, memberPos, facing, false);
            if (!resolver.resolve()) {
                return false; // a member alone cannot pull its structure: nobody pulls
            }
            total += resolver.getToPush().size();
        }
        return stickyMembers > 0 && total <= 12 * stickyMembers;
    }

    // ------------------------------------------------------------- rod nub lifecycle

    /**
     * The nub is part of the piston and is push-resistant like a moving piston, so it has to
     * leave the front cell before the extend pre-resolve looks at it. This runs before
     * {@link #handleExtend} and therefore also before the block event, which is the only place
     * where the cell can be cleared without a neighbour-update loop (flag 276).
     */
    @Override
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        this.clearRodNub(level, pos, direction);
        return super.resolveExtend(level, pos, direction);
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        this.clearRodNub(level, pos, direction);
        return false; // proceed with the vanilla push
    }

    private void clearRodNub(Level level, BlockPos pos, Direction direction) {
        BlockPos frontPos = pos.relative(direction);
        if (level.getBlockState(frontPos).getBlock() instanceof WallMergeRodBlock) {
            // flag 276: no neighbour updates, or checkIfExtend re-enters mid-extension and loops
            level.setBlock(frontPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 276);
        }
    }

    /** The nub belongs to the retracted piston: put it back once the head is home again. */
    private void restoreRodNub(Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() instanceof WallMergePistonBlock && !current.getValue(EXTENDED)) {
            this.updateRodNub(level, pos, current);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        this.updateRodNub(level, pos, state);
    }

    //? if <1.21.2 {
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        this.updateRodNub(level, pos, state);
    }
    //?} else {
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        this.updateRodNub(level, pos, state);
    }
    //?}

    /**
     * A retracted, unpowered wall-merge piston shows its rod nub; regenerate it when missing. A
     * powered one leaves the cell empty instead, so the extend pre-resolve sees free space (the
     * nub comes back on retract).
     */
    private void updateRodNub(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || state.getValue(EXTENDED)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        if (this.hasPowerSignal(level, pos, facing)) {
            return;
        }
        BlockPos frontPos = pos.relative(facing);
        if (level.getBlockState(frontPos).isAir()) {
            // flag 2: client-only, so placing the nub never bounces back into checkIfExtend
            level.setBlock(frontPos, ModBlocks.WALL_MERGE_ROD.defaultBlockState().setValue(WallMergeRodBlock.FACING, facing), 2);
        }
    }
}
