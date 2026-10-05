package dev.zcode.piston_diversified.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}

/**
 * Head of the 重力活塞 — survives without its base (不再依赖底座存在) and, when it is not facing
 * straight down, falls like sand whenever the cell below cannot support it: a sideways-pushed
 * head detaches into a vanilla falling block and lands wherever it lands. Downward heads never
 * fall — the cell "below" them is the cell the telescoping chain is about to extend into.
 */
public class GravityPistonHeadBlock extends ModPistonHeadBlock {
    public GravityPistonHeadBlock(Properties properties) {
        super(properties);
    }

    /** The fallen head must be able to exist anywhere — it never depends on a base. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /** 活塞头消失（下落/破坏）不得连带破坏底座 — the base just goes headless. */
    //? if >=1.20.3 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // no base destruction
    }
    //?} else {
    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // no base destruction; skip the vanilla head behaviour entirely
    }
    //?}

    //? if <1.21.2 {
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        this.scheduleFallCheck(level, pos);
    }
    //?} else {
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        this.scheduleFallCheck(level, pos);
    }
    //?}

    //? if <1.21.2 {
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, movedByPiston);
        this.scheduleFallCheck(level, pos);
    }
    //?} else {
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        this.scheduleFallCheck(level, pos);
    }
    //?}

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        if (facing != Direction.DOWN
            && pos.getY() > PdMinBuildHeight(level)
            && FallingBlock.isFree(level.getBlockState(pos.below()))) {
            // converts the cell to air and spawns a vanilla falling block carrying the head
            // state; on landing it places itself again (canSurvive is always true) or drops
            FallingBlockEntity.fall(level, pos, state);
        }
    }

    /** Level.getMinY() only exists from 1.20 on; older versions say getMinBuildHeight(). */
    private static int PdMinBuildHeight(Level level) {
        //? if <1.20 {
        return level.getMinBuildHeight();
        //?} else {
        return level.getMinY();
        //?}
    }

    private void scheduleFallCheck(Level level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }
}
