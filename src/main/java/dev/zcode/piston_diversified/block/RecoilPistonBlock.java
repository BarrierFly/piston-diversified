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
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;

/**
 * 后坐活塞 — cannot push: with a free front it extends normally; when the front cell holds an
 * obstruction (a block whose piston push behaviour is pushable or push-resistant) the base recoils
 * one cell backwards, the old base cell becomes the head, and whatever sat behind the base is
 * pushed (or destroyed) further back.
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
    protected boolean extendEventWithoutResolve(Level level, BlockPos pos, Direction direction) {
        // An obstruction fails the vanilla pre-resolve, so the event has to be sent anyway or the
        // recoil never happens — but only for a real obstruction. Firing it unconditionally also
        // fires it for the moving piston this piston has just put in front of itself (see the hook
        // javadoc), and handleExtend would read that as "head in front" and recoil.
        return this.frontIsBlocked(level, pos, direction);
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        if (!this.frontIsBlocked(level, pos, direction)) {
            return false; // free or popped front — the vanilla push extends forward
        }

        if (level.isClientSide()) {
            return true;
        }

        // Recoil: everything from the cell behind the base backwards is pushed one cell further
        // back, then the base slides into that cell. The resolver starts its push line at the
        // cell in front of the position it is given, so pass the base's own cell — passing the
        // behind cell would shove the SECOND cell back even when the first is empty (and would
        // overwrite the first cell's block instead of pushing it).
        if (!PistonlessPush.execute((net.minecraft.server.level.ServerLevel) level, pos, direction.getOpposite(), true, false, false)) {
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

    /**
     * Whether the cell in front stops the piston: it holds a block whose piston push behaviour is
     * pushable (NORMAL / PUSH_ONLY) or push-resistant (BLOCK). Air, fluids and destroy-on-push
     * (popped) blocks do not stop it, and neither does the piston's own head while it is still
     * sliding out — that cell is transiently a moving piston of this very extension.
     */
    private boolean frontIsBlocked(Level level, BlockPos pos, Direction direction) {
        BlockPos frontPos = pos.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        if (frontState.isAir() || !frontState.getFluidState().isEmpty()) {
            return false;
        }
        if (this.isOwnExtensionInFlight(level, frontPos, frontState, direction)) {
            return false;
        }

        PushReaction reaction = frontState.getPistonPushReaction();
        return reaction == PushReaction.NORMAL
            || reaction == PushReaction.PUSH_ONLY
            || reaction == PushReaction.BLOCK;
    }

    /** The head of this piston is still travelling out of the front cell. */
    private boolean isOwnExtensionInFlight(Level level, BlockPos frontPos, BlockState frontState, Direction direction) {
        return frontState.is(Blocks.MOVING_PISTON)
            && frontState.getValue(MovingPistonBlock.FACING) == direction
            && level.getBlockEntity(frontPos) instanceof PistonMovingBlockEntity movingBe
            && movingBe.isExtending();
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
