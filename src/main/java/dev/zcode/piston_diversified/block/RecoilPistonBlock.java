package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.logic.PistonlessPush;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;

/**
 * 后坐活塞 — cannot push: with a free front it extends normally; when the front cell holds a
 * block (pushable or not) the base recoils one cell backwards, the old base cell becomes the
 * head, and whatever sat behind the base is pushed (or destroyed) further back.
 */
public class RecoilPistonBlock extends ModPistonBaseBlock {
    public RecoilPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.RECOIL_PISTON_HEAD;
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        BlockPos frontPos = pos.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        boolean frontFree = frontState.isAir()
            || !frontState.getFluidState().isEmpty()
            || frontState.getPistonPushReaction() == PushReaction.DESTROY;
        if (frontFree) {
            return false; // normal extend through the base flow
        }

        if (level.isClientSide()) {
            return true;
        }

        // Recoil: the cell behind the base is pushed one further back, then the base slides there.
        BlockPos behindPos = pos.relative(direction.getOpposite());
        if (!PistonlessPush.execute((net.minecraft.server.level.ServerLevel) level, behindPos, direction.getOpposite(), true, false, false)) {
            return true; // cannot recoil: no extension at all
        }

        BlockState headState = ModBlocks.RECOIL_PISTON_HEAD
            .defaultBlockState()
            .setValue(PistonHeadBlock.FACING, direction)
            .setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT);
        level.setBlock(pos, headState, 3);
        this.placeRecoilBase(level, pos, direction);
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, dev.zcode.piston_diversified.PdHelpers.pdRandom(level).nextFloat() * 0.25F + 0.6F);
        level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(state));
        return true;
    }

    private void placeRecoilBase(Level level, BlockPos pos, Direction direction) {
        BlockPos newBasePos = pos.relative(direction.getOpposite());
        BlockState baseState = this.defaultBlockState().setValue(FACING, direction).setValue(EXTENDED, true);
        BlockState movingState = Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(MovingPistonBlock.FACING, direction)
            .setValue(MovingPistonBlock.TYPE, PistonType.DEFAULT);
        level.setBlock(newBasePos, movingState, 324);
        BlockEntity movingBe = MovingPistonBlock.newMovingBlockEntity(newBasePos, movingState, baseState, direction, false, false);
        level.setBlockEntity(movingBe);
    }
}
