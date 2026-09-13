package dev.zcode.piston_diversified.logic;

import com.google.common.collect.Lists;
import dev.zcode.piston_diversified.PdHelpers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The 无活塞推动事件 (piston-less push event): resolves and executes a push with no piston or
 * piston head involved. Used by 抛射活塞 impact/scrape and by 后坐活塞's recoil push.
 *
 * <p>Execution: destroy {@code toDestroy} first (TNT explodes in place), then teleport every
 * pushed block one step along the push direction — no moving pistons, no entity pushing.</p>
 */
public final class PistonlessPush {
    private PistonlessPush() {
    }

    public static boolean execute(ServerLevel level, BlockPos fromPos, Direction pushDirection,
                                  boolean allowStickiness, boolean destroyFragile, boolean destroyTnt) {
        ModPistonStructureResolver resolver = new ModPistonStructureResolver(
            level, fromPos, pushDirection, true, allowStickiness, destroyFragile, destroyTnt
        );
        if (!resolver.resolve()) {
            return false;
        }

        List<BlockPos> toDestroy = resolver.getToDestroy();
        List<BlockPos> toPush = resolver.getToPush();
        List<BlockState> pushedStates = Lists.newArrayList();
        List<CompoundTag> pushedBeTags = Lists.newArrayList();

        for (BlockPos pos : toPush) {
            BlockState state = level.getBlockState(pos);
            pushedStates.add(state);
            pushedBeTags.add(state.hasBlockEntity() ? captureBe(level, pos, state) : null);
        }

        // Destroy first, TNT blocks exploding in place.
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            BlockPos pos = toDestroy.get(i);
            BlockState state = level.getBlockState(pos);
            BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            if (state.is(net.minecraft.world.level.block.Blocks.TNT)) {
                PdHelpers.explodeTnt(level, pos);
            } else {
                Block.dropResources(state, level, pos, be);
            }
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(state));
        }

        // Clear sources, then place far-to-near so nothing overwrites a not-yet-moved block.
        for (BlockPos pos : toPush) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
        }
        for (int i = toPush.size() - 1; i >= 0; i--) {
            BlockPos target = toPush.get(i).relative(pushDirection);
            BlockState state = pushedStates.get(i);
            level.setBlock(target, state, 3);
            CompoundTag beTag = pushedBeTags.get(i);
            if (beTag != null && state.hasBlockEntity()) {
                BlockEntity freshBe = level.getBlockEntity(target);
                if (freshBe != null) {
                    applyBeTag(level, freshBe, beTag);
                    freshBe.setChanged();
                }
            }
            level.updateNeighborsAt(target, state.getBlock());
        }

        for (BlockPos pos : toPush) {
            level.updateNeighborsAt(pos, net.minecraft.world.level.block.Blocks.AIR);
        }

        return true;
    }

    private static CompoundTag captureBe(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        //? if >=1.21.2 {
        try (net.minecraft.util.ProblemReporter.ScopedCollector collector =
                 new net.minecraft.util.ProblemReporter.ScopedCollector(dev.zcode.piston_diversified.multiver.MultiverLogger.LOGGER)) {
            net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                collector.forChild(be.problemPath()), level.registryAccess()
            );
            be.saveCustomOnly(out);
            return out.buildResult();
        }
        //?} else {
        return be.saveWithoutMetadata();
        //?}
    }

    private static void applyBeTag(ServerLevel level, BlockEntity be, CompoundTag tag) {
        //? if >=1.21.2 {
        try (net.minecraft.util.ProblemReporter.ScopedCollector collector =
                 new net.minecraft.util.ProblemReporter.ScopedCollector(dev.zcode.piston_diversified.multiver.MultiverLogger.LOGGER)) {
            be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                collector.forChild(be.problemPath()), level.registryAccess(), tag
            ));
        }
        //?} else {
        be.load(tag);
        //?}
    }
}
