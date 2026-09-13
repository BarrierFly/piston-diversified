package dev.zcode.piston_diversified.registry;

import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.entity.ProjectileBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
//? if <26.1 {
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.world.entity.EntityDimensions;
//?}

public final class ModEntities {
    //? if >=26.1 {
    public static final EntityType<ProjectileBlockEntity> PROJECTILE_BLOCK = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        PdHelpers.entityTypeKey(PdHelpers.id("projectile_block")),
        EntityType.Builder.<ProjectileBlockEntity>of(ProjectileBlockEntity::new, MobCategory.MISC)
            .sized(0.98F, 0.98F)
            .build(PdHelpers.entityTypeKey(PdHelpers.id("projectile_block")))
    );
    //?} else if <1.20.5 {
    public static final EntityType<ProjectileBlockEntity> PROJECTILE_BLOCK = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        PdHelpers.id("projectile_block"),
        FabricEntityTypeBuilder.<ProjectileBlockEntity>create(MobCategory.MISC, ProjectileBlockEntity::new)
            .dimensions(EntityDimensions.fixed(0.98F, 0.98F))
            .build()
    );
    //?} else {
    public static final EntityType<ProjectileBlockEntity> PROJECTILE_BLOCK = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        PdHelpers.entityTypeKey(PdHelpers.id("projectile_block")),
        FabricEntityTypeBuilder.<ProjectileBlockEntity>create(MobCategory.MISC, ProjectileBlockEntity::new)
            .dimensions(EntityDimensions.fixed(0.98F, 0.98F))
            .build(PdHelpers.entityTypeKey(PdHelpers.id("projectile_block")))
    );
    //?}

    private ModEntities() {
    }

    public static void init() {
        // Static init registers everything.
    }
}
