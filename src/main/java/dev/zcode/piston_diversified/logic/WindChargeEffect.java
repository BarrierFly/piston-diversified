package dev.zcode.piston_diversified.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The wind charge's trigger interaction (风弹的交互效果), applied by 风弹活塞 when its head
 * finishes extending. On 1.20.5+ this is the vanilla {@code Level.ExplosionInteraction.TRIGGER}
 * explosion; on 1.19.4 (no wind charges) the same set of elements is triggered manually.
 */
public final class WindChargeEffect {
    private WindChargeEffect() {
    }

    public static void trigger(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        //? if >=1.20.5 {
        level.explode(null, center.x, center.y, center.z, 1.2F, false, net.minecraft.world.level.Level.ExplosionInteraction.TRIGGER);
        //?} else {
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6F, 1.2F);
        triggerManually(level, pos);
        //?}
    }

    //? if <1.20.5 {
    /** 1.19.4 fallback: mirror the vanilla onExplosionHit set (button/lever/door/trapdoor/gate/bell/candle/TNT). */
    private static void triggerManually(ServerLevel level, BlockPos center) {
        int r = 2;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof ButtonBlock button) {
                if (!state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                    button.press(state, level, pos);
                }
            } else if (block instanceof LeverBlock lever) {
                lever.pull(state, level, pos);
            } else if (block instanceof DoorBlock door) {
                if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                    door.setOpen(null, level, state, pos, !door.isOpen(state));
                }
            } else if (block instanceof TrapDoorBlock trapDoor) {
                if (!state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                    //? if <1.20.5 {
                    boolean open = state.getValue(TrapDoorBlock.OPEN);
                    level.setBlockAndUpdate(pos, state.setValue(TrapDoorBlock.OPEN, !open));
                    //?} else {
                    trapDoor.setOpen(null, level, state, pos, !trapDoor.isOpen(state));
                    //?}
                }
            } else if (block instanceof FenceGateBlock) {
                boolean open = state.getValue(FenceGateBlock.OPEN);
                level.setBlockAndUpdate(pos, state.setValue(FenceGateBlock.OPEN, !open));
            } else if (block instanceof BellBlock bell) {
                bell.attemptToRing(level, pos, null);
            } else if (block instanceof net.minecraft.world.level.block.AbstractCandleBlock) {
                if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                    net.minecraft.world.level.block.AbstractCandleBlock.extinguish(null, state, level, pos);
                }
            }
        }
    }
    //?}
}
