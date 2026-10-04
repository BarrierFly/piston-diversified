package dev.zcode.piston_diversified.registry;

import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.logic.PickaxePistonRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * The 镐 piston's custom recipe serializer — the only mod recipe whose output depends on the
 * input's data (the pickaxe rides along), so it cannot be a JSON shapeless recipe. The serializer
 * plumbing moved twice across the supported versions; each gets its own construction.
 */
public final class ModRecipes {
    //? if <1.21.2 {
    public static final RecipeSerializer<PickaxePistonRecipe> PICKAXE_PISTON_SERIALIZER = Registry.register(
        BuiltInRegistries.RECIPE_SERIALIZER,
        PdHelpers.id("pickaxe_piston"),
        new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(PickaxePistonRecipe::new)
    );
    //?} else if <26.1 {
    public static final RecipeSerializer<PickaxePistonRecipe> PICKAXE_PISTON_SERIALIZER = Registry.register(
        BuiltInRegistries.RECIPE_SERIALIZER,
        PdHelpers.id("pickaxe_piston"),
        new net.minecraft.world.item.crafting.CustomRecipe.Serializer<>(PickaxePistonRecipe::new)
    );
    //?} else {
    private static final com.mojang.serialization.MapCodec<PickaxePistonRecipe> PICKAXE_PISTON_CODEC =
        com.mojang.serialization.MapCodec.unit(new PickaxePistonRecipe());
    private static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PickaxePistonRecipe> PICKAXE_PISTON_STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.unit(new PickaxePistonRecipe());
    public static final RecipeSerializer<PickaxePistonRecipe> PICKAXE_PISTON_SERIALIZER = Registry.register(
        BuiltInRegistries.RECIPE_SERIALIZER,
        PdHelpers.id("pickaxe_piston"),
        new net.minecraft.world.item.crafting.RecipeSerializer<>(PICKAXE_PISTON_CODEC, PICKAXE_PISTON_STREAM_CODEC)
    );
    //?}

    private ModRecipes() {
    }

    public static void init() {
        // Static init registers everything.
    }
}
