package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.PdGamerules;
import dev.zcode.piston_diversified.logic.PotatoStructureResolver;
import dev.zcode.piston_diversified.logic.PotatoPushLogic;
import dev.zcode.piston_diversified.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 马铃薯活塞 — selects its moving structure with the Floatater rules
 * ({@link PotatoStructureResolver}) instead of the piston line rules, moves it with its own
 * moving pistons, and keeps flying: the leading cell's landing queues a piston-less push of the
 * intersection of the recorded structure and a freshly selected one, so an unobstructed
 * structure keeps gliding through the air (悬浮飞行). Push budget: the potatoPushLimit gamerule
 * (default 32) non-fluid cells. Not sticky.
 */
public class PotatoPistonBlock extends ModPistonBaseBlock {
    public PotatoPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.POTATO_PISTON_HEAD;
    }

    @Override
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return super.resolveExtend(level, pos, direction);
        }
        return this.selectStructure(serverLevel, pos, direction) != null;
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        if (level.isClientSide()) {
            // Mirror the decision so the client slides the head out instead of animating a vanilla
            // forward push the server never performs: clear the cells the server carries away and
            // place the same source moving piston for the head.
            PotatoStructureResolver clientResolver =
                new PotatoStructureResolver(level, pos, direction, PdGamerules.POTATO_PUSH_LIMIT_DEFAULT);
            BlockPos frontPos = pos.relative(direction);
            if (clientResolver.resolve(frontPos)) {
                PotatoPushLogic.clearStructure(level, frontPos, direction, clientResolver);
                this.placeHead(level, pos, direction);
                level.setBlock(pos, state.setValue(EXTENDED, true), 67);
            }
            return true;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        PotatoStructureResolver resolver = this.selectStructure(serverLevel, pos, direction);
        if (resolver == null) {
            return true; // 推不动就不动
        }
        List<PotatoStructureResolver.Member> members = resolver.getMembers();
        BlockPos frontPos = pos.relative(direction);
        long[] record = members.isEmpty() ? new long[0] : PotatoStructureResolver.record(members, members.get(0).pos());
        // Move the structure FIRST, then empty the front cell for the head. Clearing it up front
        // deleted a front destroy-on-push block (torch, grass, …) outright with no drop, which is
        // why the potato piston looked like it swallowed POP blocks instead of popping them.
        if (!PotatoPushLogic.executePush(serverLevel, members, resolver.getToDestroy(), direction, record)) {
            return true; // 推不动就不动 — the structure could not move a full cell, nothing changed
        }
        level.setBlock(frontPos, Blocks.AIR.defaultBlockState(), 82);
        this.placeHead(serverLevel, pos, direction);
        level.setBlock(pos, state.setValue(EXTENDED, true), 67);
        if (!this.isSilent()) {
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.PISTON_EXTEND,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F,
                dev.zcode.piston_diversified.PdHelpers.pdRandom(level).nextFloat() * 0.25F + 0.6F);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_ACTIVATE, pos,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(state));
        }
        this.afterExtendExecuted(serverLevel, pos, direction);
        return true;
    }

    /** Structure selection rooted at the cell in front of the piston, or null when it fails. */
    private PotatoStructureResolver selectStructure(ServerLevel level, BlockPos pos, Direction direction) {
        PotatoStructureResolver resolver = new PotatoStructureResolver(
            level, pos, direction, PdGamerules.potatoPushLimit(level)
        );
        if (resolver.resolve(pos.relative(direction))) {
            return resolver;
        }
        return null;
    }

    /** Sliding the (bent-free) head out as a source moving piston, like the vanilla move does. */
    private void placeHead(Level level, BlockPos pos, Direction direction) {
        BlockPos frontPos = pos.relative(direction);
        BlockState headState = ModBlocks.POTATO_PISTON_HEAD
            .defaultBlockState()
            .setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING, direction)
            .setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.TYPE,
                net.minecraft.world.level.block.state.properties.PistonType.DEFAULT);
        BlockState movingState = net.minecraft.world.level.block.Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.FACING, direction);
        level.setBlock(frontPos, movingState, ModPistonBaseBlock.SYNC_MOVING_PISTON);
        net.minecraft.world.level.block.entity.BlockEntity be =
            net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(
                frontPos, movingState, headState, direction, true, true);
        level.setBlockEntity(be);
    }
}