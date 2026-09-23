package dev.zcode.piston_diversified.logic;

import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 染料/水瓶转换链 (规划 v2 §三.5-7):
 * <ul>
 *   <li>青色染料 on 活塞 → 连锁型；on 黏塞 → 连锁型黏塞；on 循环型 → 连锁型。</li>
 *   <li>紫色染料 on 活塞 → 循环型；on 连锁型 → 循环型。</li>
 *   <li>水瓶 on 连锁型/连锁型黏塞 → 普通活塞/黏塞；on 循环型 → 普通活塞。</li>
 * </ul>
 * FACING/EXTENDED carry over; when extended, the front head is swapped to the matching head.
 */
public final class DyeConversions {
    private DyeConversions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(DyeConversions::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        Block target = pickTarget(stack, state);
        if (target == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
        boolean extended = state.getValue(dev.zcode.piston_diversified.block.ModPistonBaseBlock.EXTENDED);
        boolean shortHead = false;
        BlockState frontState = level.getBlockState(pos.relative(facing));
        if (extended && frontState.getBlock() instanceof PistonHeadBlock) {
            shortHead = frontState.getValue(PistonHeadBlock.SHORT);
        }

        BlockState newState = target.defaultBlockState()
            .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, facing)
            .setValue(dev.zcode.piston_diversified.block.ModPistonBaseBlock.EXTENDED, extended);
        level.setBlock(pos, newState, 3);
        if (extended) {
            boolean stickyBase = target == Blocks.STICKY_PISTON
                || target instanceof dev.zcode.piston_diversified.block.ModPistonBaseBlock modded && modded.pdIsSticky();
            BlockState headState = ModBlocks.headOf(target).defaultBlockState()
                .setValue(PistonHeadBlock.FACING, facing)
                .setValue(PistonHeadBlock.TYPE, stickyBase ? PistonType.STICKY : PistonType.DEFAULT)
                .setValue(PistonHeadBlock.SHORT, shortHead);
            // UPDATE_CLIENTS only: a full setBlock would run the removed head's
            // affectNeighborsAfterRemoval, and PistonHeadBlock destroys a fitting extended base
            // there — i.e. the base we just placed. The new head is supported by that base, so it
            // needs no neighbour/shape update of its own.
            level.setBlock(pos.relative(facing), headState, Block.UPDATE_CLIENTS);
        }

        consume(player, stack, hand);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    private static Block pickTarget(ItemStack stack, BlockState state) {
        if (PdHelpers.isItemNamed(stack, "cyan_dye")) {
            if (state.is(Blocks.PISTON) || state.is(ModBlocks.LOOP_PISTON)) {
                return ModBlocks.CHAIN_PISTON;
            }
            if (state.is(Blocks.STICKY_PISTON)) {
                return ModBlocks.CHAIN_STICKY_PISTON;
            }
            return null;
        }
        if (PdHelpers.isItemNamed(stack, "purple_dye")) {
            if (state.is(Blocks.PISTON) || state.is(ModBlocks.CHAIN_PISTON)) {
                return ModBlocks.LOOP_PISTON;
            }
            return null;
        }
        if (PdHelpers.isWaterBottle(stack)) {
            if (state.is(ModBlocks.CHAIN_PISTON) || state.is(ModBlocks.LOOP_PISTON)) {
                return Blocks.PISTON;
            }
            if (state.is(ModBlocks.CHAIN_STICKY_PISTON)) {
                return Blocks.STICKY_PISTON;
            }
            return null;
        }
        return null;
    }

    private static void consume(Player player, ItemStack stack, InteractionHand hand) {
        if (player.isCreative()) {
            return;
        }
        if (PdHelpers.isWaterBottle(stack)) {
            if (stack.getCount() == 1) {
                player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
            } else {
                stack.shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                    player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
                }
            }
        } else {
            stack.shrink(1);
        }
    }
}
