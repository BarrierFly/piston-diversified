package dev.zcode.piston_diversified.entity;

import dev.zcode.piston_diversified.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.2 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

/**
 * 镐活塞's block entity: stores the pickaxe (material + enchantments) the piston was crafted
 * with. The stack rides losslessly between item and block form (方块和物品形式转换无损).
 */
public class PickaxePistonBlockEntity extends BlockEntity {
    public static final String PICKAXE_TAG = "pd_pickaxe";

    private ItemStack pickaxe = ItemStack.EMPTY;

    public PickaxePistonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PICKAXE_PISTON, pos, state);
    }

    public ItemStack getPickaxe() {
        return this.pickaxe;
    }

    public void setPickaxe(ItemStack pickaxe) {
        this.pickaxe = pickaxe == null ? ItemStack.EMPTY : pickaxe;
        this.setChanged();
    }

    //? if >=1.21.2 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.pickaxe.isEmpty()) {
            output.store("Pickaxe", ItemStack.CODEC, this.pickaxe);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.pickaxe = input.read("Pickaxe", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
    //?} else {
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.pickaxe.isEmpty()) {
            tag.put("Pickaxe", this.pickaxe.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Pickaxe", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            this.pickaxe = ItemStack.of(tag.getCompound("Pickaxe"));
        }
    }
    //?}
}
