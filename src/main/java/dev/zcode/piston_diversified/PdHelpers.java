package dev.zcode.piston_diversified;

//? if <1.21.11 {
import net.minecraft.resources.ResourceLocation;
//?} else {
import net.minecraft.resources.Identifier;
//?}

/** Small cross-version helpers (the only stonecutter guards besides block/ObserverPistonBlock). */
public final class PdHelpers {
    private PdHelpers() {
    }

    //? if <1.20.5 {
    public static ResourceLocation id(String path) {
        return new ResourceLocation(PistonDiversified.MOD_ID, path);
    }
    //?} else if <1.21.11 {
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PistonDiversified.MOD_ID, path);
    }
    //?} else {
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PistonDiversified.MOD_ID, path);
    }
    //?}

    /** True if the stack's item is registered under the given path (dye item names are stable even where classes changed). */
    public static boolean isItemNamed(net.minecraft.world.item.ItemStack stack, String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(path);
    }

    /** True if the stack is a water bottle. */
    public static boolean isWaterBottle(net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(net.minecraft.world.item.Items.POTION)) {
            return false;
        }
        //? if >=1.20.5 {
        net.minecraft.world.item.alchemy.PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        //?} else {
        return net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack) == net.minecraft.world.item.alchemy.Potions.WATER;
        //?}
    }

    /** Whether the entity drops gamerule is enabled (GameRules moved packages across versions). */
    public static boolean entityDropsEnabled(net.minecraft.server.level.ServerLevel level) {
        //? if <1.21.11 {
        return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS);
        //?} else {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS);
        //?}
    }

    /** ResourceKey of the vanilla redstone creative tab (CreativeModeTabs fields differ across versions). */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> redstoneTabKey() {
        //? if >=1.20.5 {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, id("redstone_blocks"));
        //?} else {
        return null;
        //?}
    }

    /** Registry key for the projectile entity (ResourceKey.create needs the version id type). */
    //? if <1.21.11 {
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.entity.EntityType<?>> entityTypeKey(ResourceLocation id) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id);
    }
    //?} else {
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.entity.EntityType<?>> entityTypeKey(Identifier id) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id);
    }
    //?}

    /** Drops the block item for an entity (spawnAtLocation signatures differ across versions). */
    public static void spawnBlockDrop(net.minecraft.world.entity.Entity entity, net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.block.Block block) {
        //? if >=1.20.5 {
        entity.spawnAtLocation(level, block);
        //?} else {
        entity.spawnAtLocation(new net.minecraft.world.item.ItemStack(block));
        //?}
    }

    /** Level's random source (field vs accessor differs across versions). */
    public static net.minecraft.util.RandomSource pdRandom(net.minecraft.world.level.Level level) {
        //? if >=26.1 {
        return level.getRandom();
        //?} else {
        return level.random;
        //?}
    }

    /** Vanilla-TNT-style explosion in place (radius 4, destructive interaction). */
    public static void explodeTnt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        level.explode(null, center.x, center.y, center.z, 4.0F, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
    }
}
