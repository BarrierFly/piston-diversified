package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 长推活塞 — extends once as soon as it is placed (with or without signal), pushes normally
 * and never retracts.
 */
public class LongPushPistonBlock extends ModPistonBaseBlock {
    public LongPushPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.LONG_PUSH_PISTON_HEAD;
    }

    @Override
    protected boolean canRetract() {
        return false;
    }

    @Override
    protected boolean requireSignalToExtend() {
        return false;
    }

    /**
     * Always "powered": the piston extends on any block update while retracted — so after being
     * placed against an obstacle it still extends once that obstacle is cleared (规划: 放置直接推出,
     * 正常推方块, 永不收回).
     */
    @Override
    protected boolean hasPowerSignal(Level level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide() && !state.getValue(EXTENDED)) {
            level.blockEvent(pos, this, 0, state.getValue(FACING).get3DDataValue());
        }
    }
}
