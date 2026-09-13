package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Head block that fits every {@link PistonBaseBlock} (vanilla or modded) instead of only the two
 * vanilla pistons. Required so modded pistons can keep a head in front of them, and so dye
 * conversions can swap the head of an extended vanilla piston.
 */
public class ModPistonHeadBlock extends PistonHeadBlock {
    public ModPistonHeadBlock(Properties properties) {
        super(properties);
    }

    /** Heads fit any piston base facing the same way while it is extended. */
    protected boolean isFittingBase(BlockState headState, BlockState baseState) {
        return baseState.getBlock() instanceof PistonBaseBlock
            && baseState.getValue(PistonBaseBlock.EXTENDED)
            && baseState.getValue(FACING) == headState.getValue(FACING);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState behind = level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
        return this.isFittingBase(state, behind)
            || behind.is(Blocks.MOVING_PISTON) && behind.getValue(MovingPistonBlock.FACING) == state.getValue(FACING);
    }

    //? if <1.21.2 {
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        //? if <1.20.5 {
        if (!level.isClientSide() && player.getAbilities().instabuild) {
        //?} else {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
        //?}
            BlockPos behindPos = pos.relative(state.getValue(FACING).getOpposite());
            if (this.isFittingBase(state, level.getBlockState(behindPos))) {
                level.destroyBlock(behindPos, false);
            }
        }

        super.playerWillDestroy(level, pos, state, player);
    }
    //?} else {
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            BlockPos behindPos = pos.relative(state.getValue(FACING).getOpposite());
            if (this.isFittingBase(state, level.getBlockState(behindPos))) {
                level.destroyBlock(behindPos, false);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }
    //?}

    //? if >=1.20.3 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockPos behindPos = pos.relative(state.getValue(FACING).getOpposite());
        if (this.isFittingBase(state, level.getBlockState(behindPos))) {
            level.destroyBlock(behindPos, true);
        }
    }
    //?}

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
