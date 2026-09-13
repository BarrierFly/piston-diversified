package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.2 {
import net.minecraft.world.level.ScheduledTickAccess;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 侦测器活塞 / 侦测器黏塞 — ignores redstone entirely; detects block updates on the cell behind
 * the piston (the face carrying the observer face), then extends and retracts with observer
 * timing (2gt detection delay, 2gt active pulse).
 */
public class ObserverPistonBlock extends ModPistonBaseBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ObserverPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.OBSERVER_STICKY_PISTON_HEAD : ModBlocks.OBSERVER_PISTON_HEAD;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    protected boolean requireSignalToExtend() {
        return false;
    }

    @Override
    protected boolean reactsToNeighbors() {
        return false;
    }

    @Override
    protected BlockPos qcCell(BlockPos pos, Direction facing) {
        return pos.relative(facing); // unused: hasPowerSignal is overridden to false
    }

    @Override
    protected boolean hasPowerSignal(Level level, BlockPos pos, Direction direction) {
        return false;
    }

    //? if <1.21.2 {
    @Override
    public BlockState updateShape(
        BlockState state, Direction direction, BlockState neighborState,
        net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos
    ) {
        if (direction == state.getValue(FACING).getOpposite() && !state.getValue(POWERED)) {
            this.startPulse(level, pos);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    //?} else {
    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (direction == state.getValue(FACING).getOpposite() && !state.getValue(POWERED)) {
            this.startPulse(level, scheduledTickAccess, pos);
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }
    //?}

    //? if >=1.21.2 {
    private void startPulse(LevelReader level, ScheduledTickAccess ticks, BlockPos pos) {
        if (!level.isClientSide() && !ticks.getBlockTicks().hasScheduledTick(pos, this)) {
            ticks.scheduleTick(pos, this, 2);
        }
    }
    //?} else {
    private void startPulse(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }
    //?}

    /**
     * Pulse state machine (POWERED doubles as the refractory flag):
     * <ol>
     *   <li>detection (updateShape, back cell) → scheduleTick(2);</li>
     *   <li>tick !POWERED → POWERED=true, extend event, scheduleTick(2);</li>
     *   <li>tick POWERED+EXTENDED → retract event, scheduleTick(6) — POWERED stays on so the
     *       piston ignores the back-cell reactions its own extend/retract animation causes
     *       (the base flickers between conductor states while it is a moving piston, which
     *       otherwise re-triggers detection forever);</li>
     *   <li>tick POWERED+!EXTENDED (animation settled or extend was blocked) → disarm.</li>
     * </ol>
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        if (state.getValue(POWERED)) {
            if (state.getValue(EXTENDED)) {
                level.blockEvent(pos, this, 1, direction.get3DDataValue());
                level.scheduleTick(pos, this, 6);
            } else {
                level.setBlock(pos, state.setValue(POWERED, false), 2);
            }
        } else {
            level.setBlock(pos, state.setValue(POWERED, true), 2);
            level.scheduleTick(pos, this, 2);
            if (!state.getValue(EXTENDED)) {
                level.blockEvent(pos, this, 0, direction.get3DDataValue());
            }
        }
    }
}
