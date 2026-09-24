package dev.zcode.piston_diversified.logic;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.zcode.piston_diversified.PdHelpers;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * The 无活塞推动事件 (piston-less push event): resolves and executes a push with no piston or
 * piston head involved. Used by 抛射活塞 impact/scrape and by 后坐活塞's recoil push.
 *
 * <p>Execution mirrors a normal piston push: destroy {@code toDestroy} first (TNT explodes in
 * place), then place a moving piston carrying each pushed block. The moving piston performs the
 * landing — state refresh, neighbour updates, entity pushing — exactly like a vanilla piston
 * push, only without a piston base or head.</p>
 */
public final class PistonlessPush {
    private PistonlessPush() {
    }

    /** Outcome of a push: whether it ran, plus the states it destroyed (for impact feedback). */
    public record Result(boolean success, List<BlockState> destroyed) {
        static final Result FAILURE = new Result(false, List.of());
    }

    public static Result execute(ServerLevel level, BlockPos fromPos, Direction pushDirection,
                                 boolean allowStickiness, boolean destroyFragile, boolean destroyTnt) {
        ModPistonStructureResolver resolver = new ModPistonStructureResolver(
            level, fromPos, pushDirection, true, allowStickiness, destroyFragile, destroyTnt
        );
        if (!resolver.resolve()) {
            return Result.FAILURE;
        }

        List<BlockPos> toDestroy = resolver.getToDestroy();
        List<BlockPos> toPush = resolver.getToPush();
        List<BlockState> destroyedStates = Lists.newArrayList();

        // Destroy first; TNT is cleared before exploding — an explosion with the block still in
        // place would wasExplode into a second primed TNT (duplication). TNT caught in another
        // block's blast still primes naturally, vanilla-style.
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            BlockPos pos = toDestroy.get(i);
            BlockState state = level.getBlockState(pos);
            destroyedStates.add(state);
            BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            boolean tnt = state.is(Blocks.TNT);
            if (!tnt) {
                Block.dropResources(state, level, pos, be);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
            if (tnt) {
                PdHelpers.explodeTnt(level, pos);
            }
        }

        Map<BlockPos, BlockState> sources = Maps.newHashMap();
        List<BlockState> oldStates = Lists.newArrayList();
        for (BlockPos pos : toPush) {
            BlockState state = level.getBlockState(pos);
            oldStates.add(state);
            sources.put(pos, state);
        }

        // The moving piston overwrites the cell in front (which is the next block's source cell),
        // so drop those from the vacated set as we go; what is left is the tail that becomes air.
        for (int k = toPush.size() - 1; k >= 0; k--) {
            BlockPos sourcePos = toPush.get(k);
            BlockPos targetPos = sourcePos.relative(pushDirection);
            sources.remove(targetPos);
            BlockState movingState = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, pushDirection);
            level.setBlock(targetPos, movingState, 324);
            BlockEntity movingBe = MovingPistonBlock.newMovingBlockEntity(
                targetPos, movingState, oldStates.get(k), pushDirection, true, false
            );
            level.setBlockEntity(movingBe);
        }

        BlockState airState = Blocks.AIR.defaultBlockState();
        for (BlockPos clearedPos : sources.keySet()) {
            level.setBlock(clearedPos, airState, 82);
        }

        for (Entry<BlockPos, BlockState> entry : sources.entrySet()) {
            BlockPos clearedPos = entry.getKey();
            BlockState clearedState = entry.getValue();
            clearedState.updateIndirectNeighbourShapes(level, clearedPos, 2);
            airState.updateNeighbourShapes(level, clearedPos, 2);
            airState.updateIndirectNeighbourShapes(level, clearedPos, 2);
        }

        for (BlockPos pos : toPush) {
            level.updateNeighborsAt(pos, Blocks.AIR);
        }

        return new Result(true, destroyedStates);
    }
}
