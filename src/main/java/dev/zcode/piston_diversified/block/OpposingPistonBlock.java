package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 对推活塞 — a half of an opposing pair. It is immovable ({@link #pdIsMovableByOthers()}) and only
 * acts while a facing piston sits within 41 cells in front of it (the "opponent").
 *
 * <pre>
 * checkIfExtend:
 *   opponent absent            → ignore every signal (hasPowerSignal = false)
 *   opponent retracted, self out → do not schedule a retract
 * extend event:
 *   opponent absent / retracted → extend normally
 *   opponent extended          → hand over: ask the opponent to retract (refused while it is
 *                                powered); if it succeeded, extend afterwards
 * retract event:
 *   opponent present           → order the opponent to extend while ignoring its signal (still
 *                                blockable by an unpushable structure), then retract
 * </pre>
 */
public class OpposingPistonBlock extends ModPistonBaseBlock {
    private static final int SCAN_LENGTH = 41;

    /** Guards the mutual extend/retract hand-off so the pair cannot recurse. */
    private static final ThreadLocal<Boolean> COORDINATING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public OpposingPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.OPPOSING_PISTON_HEAD;
    }

    @Override
    public boolean pdIsMovableByOthers() {
        return false;
    }

    /** A signal only counts while an opposing piston is in front — otherwise this is inert. */
    @Override
    public boolean hasPowerSignal(Level level, BlockPos pos, Direction direction) {
        return findOpposing(level, pos, direction) != null && super.hasPowerSignal(level, pos, direction);
    }

    @Override
    protected void checkIfExtend(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        boolean powered = this.hasPowerSignal(level, pos, direction);
        BlockPos opponentPos = findOpposing(level, pos, direction);
        if (powered && !state.getValue(EXTENDED)) {
            if (this.resolveExtend(level, pos, direction) || this.extendEventWithoutResolve(level, pos, direction)) {
                this.sendExtendEvent(level, pos, direction);
            }
        } else if (!powered && state.getValue(EXTENDED) && this.canRetract(level, pos, state)) {
            // 对方收回、自己推出 → cannot schedule a retract (an already-scheduled one still runs)
            if (opponentPos != null && !level.getBlockState(opponentPos).getValue(EXTENDED)) {
                return;
            }
            int type = this.retractType(level, pos, direction, state);
            this.sendRetractEvent(level, pos, direction, type);
        }
    }

    @Override
    protected void executeExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        if (!level.isClientSide() && !coordinating()) {
            BlockPos opponentPos = findOpposing(level, pos, direction);
            if (opponentPos != null) {
                BlockState opponentState = level.getBlockState(opponentPos);
                if (opponentState.getBlock() instanceof OpposingPistonBlock opponent && opponentState.getValue(EXTENDED)) {
                    // 对面为伸出状态: this side defers; the opponent retracts first, then we extend
                    boolean retracted = runCoordinated(() -> opponent.forceRetractForOpposing(level, opponentPos, opponentState));
                    if (retracted) {
                        super.executeExtend(level, pos, direction, state);
                    }
                    return;
                }
            }
        }
        super.executeExtend(level, pos, direction, state);
    }

    @Override
    protected void executeRetract(Level level, BlockPos pos, Direction direction, BlockState state, int id, Boolean pullOverride) {
        if (!level.isClientSide() && !coordinating()) {
            BlockPos opponentPos = findOpposing(level, pos, direction);
            if (opponentPos != null) {
                BlockState opponentState = level.getBlockState(opponentPos);
                if (opponentState.getBlock() instanceof OpposingPistonBlock opponent && !opponentState.getValue(EXTENDED)) {
                    // 调对面执行无视信号的推出事件 (still blocked by an unpushable structure)
                    runCoordinated(() -> {
                        opponent.forceExtendIgnoringSignal(level, opponentPos, opponentState);
                        return Boolean.TRUE;
                    });
                }
            }
        }
        super.executeRetract(level, pos, direction, state, id, pullOverride);
    }

    /** True if the opponent was unpowered and its retract ran. */
    boolean forceRetractForOpposing(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        if (super.hasPowerSignal(level, pos, direction)) {
            return false; // 对面有信号则执行失败
        }
        this.executeRetract(level, pos, direction, state, 1);
        return true;
    }

    /** Runs an extend while ignoring the signal (the front structure still blocks it). */
    void forceExtendIgnoringSignal(Level level, BlockPos pos, BlockState state) {
        this.executeExtend(level, pos, state.getValue(FACING), state);
    }

    /** Nearest opposing piston (facing back at us) within 41 cells in front, or null. */
    static BlockPos findOpposing(Level level, BlockPos pos, Direction facing) {
        for (int step = 1; step <= SCAN_LENGTH; step++) {
            BlockPos candidate = pos.relative(facing, step);
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof OpposingPistonBlock && state.getValue(FACING) == facing.getOpposite()) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean coordinating() {
        return COORDINATING.get();
    }

    private static <T> T runCoordinated(java.util.function.Supplier<T> action) {
        boolean previous = COORDINATING.get();
        COORDINATING.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            COORDINATING.set(previous);
        }
    }
}
