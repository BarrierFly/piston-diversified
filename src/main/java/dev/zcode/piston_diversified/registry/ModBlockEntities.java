package dev.zcode.piston_diversified.registry;

import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.entity.PickaxePistonBlockEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mod block entity types: the 镐 piston's carried pickaxe and the 马铃薯 piston's own moving
 * piston (carrying the flight structure record). Each is bound to exactly its own block so the
 * save/load round-trip keeps the modded subclass.
 */
public final class ModBlockEntities {
    public static final BlockEntityType<PickaxePistonBlockEntity> PICKAXE_PISTON = register(
        "pickaxe_piston", PickaxePistonBlockEntity::new, Set.of(ModBlocks.PICKAXE_PISTON)
    );

    private ModBlockEntities() {
    }

    /**
     * Vanilla's own supplier type is private on 1.21.x, so the factory is declared here with the
     * same shape and handed to the constructors as a (target-typed) method reference.
     */
    private interface EntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    private static <T extends BlockEntity> BlockEntityType<T> register(String name, EntityFactory<T> factory, Set<net.minecraft.world.level.block.Block> blocks) {
        BlockEntityType<T> type;
        //? if >=26.1 {
        // 26.x made the vanilla constructor and its supplier interface public
        type = new BlockEntityType<>(factory::create, blocks);
        //?} else {
        // older versions keep both private — Fabric's builder is the supported way in
        type = net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
            .<T>create(factory::create, blocks.toArray(new net.minecraft.world.level.block.Block[0]))
            .build();
        //?}
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PdHelpers.id(name), type);
    }

    public static void init() {
        // Static init registers everything.
    }
}