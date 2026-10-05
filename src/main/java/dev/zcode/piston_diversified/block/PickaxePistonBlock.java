package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.entity.PickaxePistonBlockEntity;
import dev.zcode.piston_diversified.logic.PickaxeData;
import dev.zcode.piston_diversified.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
//? if <1.20.5 {
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
//?} else {
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 镐活塞 — never moves blocks. When it extends, the front block (if any) must be pickaxe-mineable
 * with the carried pickaxe: it is then mined instantly with that pickaxe's enchantments (silky
 * touch / fortune apply, no durability), and the head slides out. Anything else in front fails
 * the extension; liquids are simply displaced like a vanilla piston does.
 */
public class PickaxePistonBlock extends ModPistonBaseBlock implements EntityBlock {
    public static final EnumProperty<PickaxeTool> TOOL = EnumProperty.create("tool", PickaxeTool.class);

    public PickaxePistonBlock(Properties properties) {
        super(false, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(TOOL, PickaxeTool.NETHERITE));
    }

    @Override
    public Block headBlock() {
        return ModBlocks.PICKAXE_PISTON_HEAD;
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOOL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PickaxePistonBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PickaxePistonBlockEntity be) {
            ItemStack pickaxe = PickaxeData.get(stack, level.registryAccess());
            if (pickaxe.isEmpty()) {
                pickaxe = PickaxePistonBlockEntity.defaultPickaxe(); // no carry data: keep the default
            }
            be.setPickaxe(pickaxe);
            PickaxeTool tool = PickaxeTool.of(pickaxe);
            if (tool != null && state.getValue(TOOL) != tool) {
                level.setBlock(pos, state.setValue(TOOL, tool), 2);
            }
        }
    }

    /** The dropped item re-carries the pickaxe data (方块 → 物品无损). */
    //? if <1.20.5 {
    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder params) {
        BlockEntity be = params.getParameter(LootContextParams.BLOCK_ENTITY);
    //?} else {
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
    //?}
        ItemStack out = new ItemStack(this);
        if (be instanceof PickaxePistonBlockEntity pickaxeBe) {
            // 1.19.4's loot builder hands out a ServerLevel already; newer ones a plain Level
            //? if <1.20.5 {
            PickaxeData.set(out, pickaxeBe.getPickaxe(), ((net.minecraft.server.level.ServerLevel) params.getLevel()).registryAccess());
            //?} else {
            if (params.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                PickaxeData.set(out, pickaxeBe.getPickaxe(), serverLevel.registryAccess());
            }
            //?}
        }
        return List.of(out);
    }

    @Override
    protected BlockState customizeHeadState(BlockState headState, Direction direction, BlockState baseState) {
        return headState.setValue(PickaxePistonHeadBlock.TOOL, baseState.getValue(TOOL));
    }

    // ------------------------------------------------------------- mining extension

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        BlockPos frontPos = pos.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        if (frontState.isAir() || !frontState.getFluidState().isEmpty()) {
            return false; // vanilla empty push; liquids are displaced like a piston does
        }
        if (level.isClientSide()) {
            return true; // the server mines and extends; block updates animate the client
        }

        ItemStack pickaxe = level.getBlockEntity(pos) instanceof PickaxePistonBlockEntity be
            ? be.getPickaxe()
            : ItemStack.EMPTY;
        if (pickaxe.isEmpty() || !this.canMine(frontState, pickaxe)) {
            return true; // 推出失败: the wrong tool cannot move the front block
        }

        BlockEntity frontBe = frontState.hasBlockEntity() ? level.getBlockEntity(frontPos) : null;
        Block.dropResources(frontState, level, frontPos, frontBe, null, pickaxe);
        level.removeBlock(frontPos, false);
        level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, frontPos,
            net.minecraft.world.level.gameevent.GameEvent.Context.of(frontState));
        return false; // front cleared — the vanilla move slides the head out
    }

    /**
     * 合适挖掘工具: the block must be pickaxe-mineable at all AND the carried pickaxe's tier
     * must be sufficient for drops (a wooden pick does not "suit" iron ore).
     */
    private boolean canMine(BlockState state, ItemStack pickaxe) {
        return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            && state.getPistonPushReaction() == PushReaction.NORMAL
            && pickaxe.isCorrectToolForDrops(state);
    }
}
