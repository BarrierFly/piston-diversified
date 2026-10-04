package dev.zcode.piston_diversified.logic;

import dev.zcode.piston_diversified.entity.PickaxePistonBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//?}

/**
 * The 镐活塞's carried pickaxe, riding on the block item: stored under {@code pd_pickaxe} inside
 * the item's custom data (components on 1.20.5+, root NBT before that) and mirrored into
 * {@link PickaxePistonBlockEntity} on placement — block ↔ item conversions lose nothing.
 */
public final class PickaxeData {
    private PickaxeData() {
    }

    public static void set(ItemStack target, ItemStack pickaxe, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        CustomData existing = target.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.put(PickaxePistonBlockEntity.PICKAXE_TAG, encodePickaxe(pickaxe, registries));
        target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?} else {
        CompoundTag tag = target.getOrCreateTag();
        tag.put(PickaxePistonBlockEntity.PICKAXE_TAG, encodePickaxe(pickaxe, registries));
        //?}
    }

    public static ItemStack get(ItemStack source, HolderLookup.Provider registries) {
        CompoundTag root;
        //? if >=1.20.5 {
        CustomData custom = source.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return ItemStack.EMPTY;
        }
        root = custom.copyTag();
        //?} else {
        root = source.getTag();
        if (root == null) {
            return ItemStack.EMPTY;
        }
        //?}
        if (!dev.zcode.piston_diversified.PdHelpers.hasCompound(root, PickaxePistonBlockEntity.PICKAXE_TAG)) {
            return ItemStack.EMPTY;
        }
        //? if <1.21.2 {
        return decodePickaxe(root.getCompound(PickaxePistonBlockEntity.PICKAXE_TAG), registries);
        //?} else {
        return decodePickaxe(root.getCompoundOrEmpty(PickaxePistonBlockEntity.PICKAXE_TAG), registries);
        //?}
    }

    /** ItemStack has no plain save() on 1.21.x — go through its codec with registry-aware ops. */
    private static net.minecraft.nbt.Tag encodePickaxe(ItemStack pickaxe, HolderLookup.Provider registries) {
        //? if <1.21.2 {
        return ItemStack.CODEC.encodeStart(ops(registries), pickaxe).getOrThrow(false, message -> {
        });
        //?} else {
        return ItemStack.CODEC.encodeStart(ops(registries), pickaxe).getOrThrow();
        //?}
    }

    private static ItemStack decodePickaxe(net.minecraft.nbt.Tag tag, HolderLookup.Provider registries) {
        return ItemStack.CODEC.parse(ops(registries), tag).result().orElse(ItemStack.EMPTY);
    }

    private static net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops(HolderLookup.Provider registries) {
        return net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries);
    }
}
