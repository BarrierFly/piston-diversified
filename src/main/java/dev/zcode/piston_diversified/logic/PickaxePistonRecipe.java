package dev.zcode.piston_diversified.logic;

import dev.zcode.piston_diversified.registry.ModBlocks;
import dev.zcode.piston_diversified.registry.ModRecipes;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
//? if >=26.1 {
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeType;
//?}

/**
 * 活塞 + 任意镐 → 镐活塞, carrying the crafting pickaxe's material and enchantments into the
 * result. A special (custom) recipe: the output depends on the input stack, so it cannot be a
 * plain shapeless JSON recipe. The serializer wiring is version-specific (see {@code ModRecipes}).
 */
public class PickaxePistonRecipe extends CustomRecipe {
    //? if >=26.1 {
    /**
     * 26.x dropped the registry-access parameter from {@code assemble}, so {@code matches} —
     * which always runs first on the same server thread — stashes the level for it. A recipe
     * instance is a registry singleton, so the stash is per-thread (and cleared on read) instead
     * of a field: a future cross-thread recipe preview would otherwise read the wrong level.
     */
    private static final ThreadLocal<Level> LEVEL_FOR_ASSEMBLE = new ThreadLocal<>();
    //?}

    //? if <1.21.2 {
    public PickaxePistonRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }
    //?} else if <26.1 {
    public PickaxePistonRecipe(CraftingBookCategory category) {
        super(category);
    }
    //?} else {
    public PickaxePistonRecipe() {
        super();
    }
    //?}

    /** Exactly two stacks: one piston, one pickaxe. */
    private boolean matches(List<ItemStack> stacks) {
        boolean piston = false;
        ItemStack pickaxe = ItemStack.EMPTY;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.PISTON) && !piston) {
                piston = true;
            } else if (pickaxe.isEmpty() && stack.is(net.minecraft.tags.ItemTags.PICKAXES)) {
                pickaxe = stack;
            } else {
                return false;
            }
        }
        return piston && !pickaxe.isEmpty();
    }

    private ItemStack assemble(List<ItemStack> stacks, net.minecraft.core.HolderLookup.Provider registries) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.PICKAXES)) {
                ItemStack result = new ItemStack(ModBlocks.PICKAXE_PISTON);
                PickaxeData.set(result, stack.copy(), registries);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    //? if <1.21.2 {
    // CraftingContainer moved out of world.item.crafting in 1.20.1
    @Override
    public boolean matches(net.minecraft.world.inventory.CraftingContainer container, Level level) {
        return this.matches(itemsOf(container));
    }

    @Override
    public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer container, net.minecraft.core.RegistryAccess registries) {
        return this.assemble(itemsOf(container), registries);
    }

    private static List<ItemStack> itemsOf(net.minecraft.world.inventory.CraftingContainer container) {
        List<ItemStack> stacks = new java.util.ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) {
            stacks.add(container.getItem(i));
        }
        return stacks;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }
    //?} else if <26.1 {
    @Override
    public boolean matches(net.minecraft.world.item.crafting.CraftingInput input, Level level) {
        return this.matches(input.items());
    }

    @Override
    public ItemStack assemble(net.minecraft.world.item.crafting.CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return this.assemble(input.items(), registries);
    }
    //?} else {
    @Override
    public boolean matches(net.minecraft.world.item.crafting.CraftingInput input, Level level) {
        LEVEL_FOR_ASSEMBLE.set(level);
        return this.matches(input.items());
    }

    @Override
    public ItemStack assemble(net.minecraft.world.item.crafting.CraftingInput input) {
        Level level = LEVEL_FOR_ASSEMBLE.get();
        LEVEL_FOR_ASSEMBLE.remove();
        if (level == null) {
            return ItemStack.EMPTY;
        }
        return this.assemble(input.items(), level.registryAccess());
    }

    @Override
    public RecipeType<CraftingRecipe> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_REDSTONE;
    }
    //?}

    @Override
    public RecipeSerializer<PickaxePistonRecipe> getSerializer() {
        return ModRecipes.PICKAXE_PISTON_SERIALIZER;
    }
}
