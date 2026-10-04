package dev.zcode.piston_diversified.registry;

import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.block.ChainPistonBlock;
import dev.zcode.piston_diversified.block.EndRodPistonBlock;
import dev.zcode.piston_diversified.block.EndRodPistonHeadBlock;
import dev.zcode.piston_diversified.block.FastPistonBlock;
import dev.zcode.piston_diversified.block.GravityPistonBlock;
import dev.zcode.piston_diversified.block.GravityPistonHeadBlock;
import dev.zcode.piston_diversified.block.HoneyPistonBlock;
import dev.zcode.piston_diversified.block.LongPushPistonBlock;
import dev.zcode.piston_diversified.block.LoopPistonBlock;
import dev.zcode.piston_diversified.block.ModPistonBaseBlock;
import dev.zcode.piston_diversified.block.ModPistonHeadBlock;
import dev.zcode.piston_diversified.block.ObserverPistonBlock;
import dev.zcode.piston_diversified.block.PickaxePistonBlock;
import dev.zcode.piston_diversified.block.PickaxePistonHeadBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import dev.zcode.piston_diversified.block.PotatoPistonBlock;
import dev.zcode.piston_diversified.block.ProjectilePistonBlock;
import dev.zcode.piston_diversified.block.QcPistonBlock;
import dev.zcode.piston_diversified.block.RecoilPistonBlock;
import dev.zcode.piston_diversified.block.RecoilPistonHeadBlock;
import dev.zcode.piston_diversified.block.RecursivePistonBlock;
import dev.zcode.piston_diversified.block.RecursivePistonHeadBlock;
import dev.zcode.piston_diversified.block.RecursivePistonRodBlock;
import dev.zcode.piston_diversified.block.RedstoneEndRodPistonBlock;
import dev.zcode.piston_diversified.block.RedstoneEndRodPistonHeadBlock;
import dev.zcode.piston_diversified.block.ScheduledTickPistonBlock;
import dev.zcode.piston_diversified.block.SilentPistonBlock;
import dev.zcode.piston_diversified.block.SkullPistonBlock;
import dev.zcode.piston_diversified.block.SkullPistonHeadBlock;
import dev.zcode.piston_diversified.block.StrongPistonBlock;
import dev.zcode.piston_diversified.block.TurnPushPistonBlock;
import dev.zcode.piston_diversified.block.TurnPushPistonHeadBlock;
import dev.zcode.piston_diversified.block.WallMergePistonBlock;
import dev.zcode.piston_diversified.block.WallMergePistonHeadBlock;
import dev.zcode.piston_diversified.block.WallMergeRodBlock;
import dev.zcode.piston_diversified.block.WeakPistonBlock;
import dev.zcode.piston_diversified.block.WeakPistonHeadBlock;
import dev.zcode.piston_diversified.block.WindChargePistonBlock;
//? if <26.1 {
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
//?} else {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//?}
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registers every piston variant (bases + heads). Bases are placed on the vanilla
 * redstone creative tab, after the vanilla pistons.
 */
public final class ModBlocks {
    // ---- v1 bases ----
    public static final Block HONEY_PISTON = registerBase("honey_piston", p -> new HoneyPistonBlock(p), baseProperties());
    public static final Block PROJECTILE_PISTON = registerBase("projectile_piston", p -> new ProjectilePistonBlock(p), baseProperties());
    public static final Block CHAIN_PISTON = registerBase("chain_piston", p -> new ChainPistonBlock(false, p), baseProperties());
    public static final Block CHAIN_STICKY_PISTON = registerBase("chain_sticky_piston", p -> new ChainPistonBlock(true, p), baseProperties());
    public static final Block LOOP_PISTON = registerBase("loop_piston", p -> new LoopPistonBlock(p), baseProperties());
    public static final Block WIND_CHARGE_PISTON = registerBase("wind_charge_piston", p -> new WindChargePistonBlock(p), baseProperties());
    public static final Block SILENT_PISTON = registerBase("silent_piston", p -> new SilentPistonBlock(p), baseProperties());
    public static final Block RECOIL_PISTON = registerBase("recoil_piston", p -> new RecoilPistonBlock(p), baseProperties());
    public static final Block END_ROD_PISTON = registerBase("end_rod_piston", p -> new EndRodPistonBlock(p), endRodProperties(15));
    public static final Block SKULL_PISTON = registerBase("skull_piston", p -> new SkullPistonBlock(p), baseProperties());
    public static final Block QC_PISTON = registerBase("qc_piston", p -> new QcPistonBlock(false, p), baseProperties());
    public static final Block QC_STICKY_PISTON = registerBase("qc_sticky_piston", p -> new QcPistonBlock(true, p), baseProperties());
    public static final Block OBSERVER_PISTON = registerBase("observer_piston", p -> new ObserverPistonBlock(false, p), baseProperties());
    public static final Block OBSERVER_STICKY_PISTON = registerBase("observer_sticky_piston", p -> new ObserverPistonBlock(true, p), baseProperties());
    public static final Block REDSTONE_END_ROD_PISTON = registerBase("redstone_end_rod_piston", p -> new RedstoneEndRodPistonBlock(p), endRodProperties(8));
    public static final Block LONG_PUSH_PISTON = registerBase("long_push_piston", p -> new LongPushPistonBlock(p), baseProperties());
    public static final Block WEAK_PISTON = registerBase("weak_piston", p -> new WeakPistonBlock(p), baseProperties());
    public static final Block FAST_PISTON = registerBase("fast_piston", p -> new FastPistonBlock(false, p), baseProperties());
    public static final Block FAST_STICKY_PISTON = registerBase("fast_sticky_piston", p -> new FastPistonBlock(true, p), baseProperties());

    // ---- v2 bases ----
    public static final Block POTATO_PISTON = registerBase("potato_piston", p -> new PotatoPistonBlock(p), baseProperties());
    public static final Block PICKAXE_PISTON = registerBase("pickaxe_piston", p -> new PickaxePistonBlock(p), baseProperties());
    public static final Block RECURSIVE_PISTON = registerBase("recursive_piston", p -> new RecursivePistonBlock(false, p), baseProperties());
    public static final Block RECURSIVE_STICKY_PISTON = registerBase("recursive_sticky_piston", p -> new RecursivePistonBlock(true, p), baseProperties());
    public static final Block TURN_PUSH_PISTON = registerBase("turn_push_piston", p -> new TurnPushPistonBlock(false, p), baseProperties());
    public static final Block TURN_PUSH_STICKY_PISTON = registerBase("turn_push_sticky_piston", p -> new TurnPushPistonBlock(true, p), baseProperties());
    public static final Block WALL_MERGE_PISTON = registerBase("wall_merge_piston", p -> new WallMergePistonBlock(false, p), baseProperties());
    public static final Block WALL_MERGE_STICKY_PISTON = registerBase("wall_merge_sticky_piston", p -> new WallMergePistonBlock(true, p), baseProperties());
    public static final Block ST_PISTON = registerBase("st_piston", p -> new ScheduledTickPistonBlock(false, p), baseProperties());
    public static final Block ST_STICKY_PISTON = registerBase("st_sticky_piston", p -> new ScheduledTickPistonBlock(true, p), baseProperties());
    public static final Block GRAVITY_PISTON = registerBase("gravity_piston", p -> new GravityPistonBlock(p), baseProperties());
    public static final Block STRONG_PISTON_1 = registerBase("strong_piston_1", p -> new StrongPistonBlock(1, p), baseProperties());
    public static final Block STRONG_PISTON_2 = registerBase("strong_piston_2", p -> new StrongPistonBlock(2, p), baseProperties());
    public static final Block STRONG_PISTON_3 = registerBase("strong_piston_3", p -> new StrongPistonBlock(3, p), baseProperties());
    // structural parts (递推杆 / 墙并杆) — registered without an item
    public static final Block RECURSIVE_PISTON_ROD = registerPart("recursive_piston_rod", p -> new RecursivePistonRodBlock(p));
    public static final Block WALL_MERGE_ROD = registerPart("wall_merge_rod", p -> new WallMergeRodBlock(p));

    // ---- heads (no items) ----
    public static final Block HONEY_PISTON_HEAD = registerHead("honey_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block PROJECTILE_PISTON_HEAD = registerHead("projectile_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block CHAIN_PISTON_HEAD = registerHead("chain_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block CHAIN_STICKY_PISTON_HEAD = registerHead("chain_sticky_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block LOOP_PISTON_HEAD = registerHead("loop_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block WIND_CHARGE_PISTON_HEAD = registerHead("wind_charge_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block SILENT_PISTON_HEAD = registerHead("silent_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block RECOIL_PISTON_HEAD = registerHead("recoil_piston_head", p -> new RecoilPistonHeadBlock(p), headProperties());
    public static final Block END_ROD_PISTON_HEAD = registerHead("end_rod_piston_head", p -> new EndRodPistonHeadBlock(p), headProperties(15));
    public static final Block SKULL_PISTON_HEAD = registerHead("skull_piston_head", p -> new SkullPistonHeadBlock(p), headProperties());
    public static final Block QC_PISTON_HEAD = registerHead("qc_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block QC_STICKY_PISTON_HEAD = registerHead("qc_sticky_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block OBSERVER_PISTON_HEAD = registerHead("observer_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block OBSERVER_STICKY_PISTON_HEAD = registerHead("observer_sticky_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block REDSTONE_END_ROD_PISTON_HEAD = registerHead("redstone_end_rod_piston_head", p -> new RedstoneEndRodPistonHeadBlock(p), headProperties(8));
    public static final Block LONG_PUSH_PISTON_HEAD = registerHead("long_push_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block WEAK_PISTON_HEAD = registerHead("weak_piston_head", p -> new WeakPistonHeadBlock(p), headProperties());
    public static final Block FAST_PISTON_HEAD = registerHead("fast_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block FAST_STICKY_PISTON_HEAD = registerHead("fast_sticky_piston_head", p -> new ModPistonHeadBlock(p), headProperties());

    // ---- v2 heads ----
    public static final Block POTATO_PISTON_HEAD = registerHead("potato_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block PICKAXE_PISTON_HEAD = registerHead("pickaxe_piston_head", p -> new PickaxePistonHeadBlock(p), headProperties());
    public static final Block RECURSIVE_PISTON_HEAD = registerHead("recursive_piston_head", p -> new RecursivePistonHeadBlock(p), headProperties());
    public static final Block RECURSIVE_STICKY_PISTON_HEAD = registerHead("recursive_sticky_piston_head", p -> new RecursivePistonHeadBlock(p), headProperties());
    public static final Block TURN_PUSH_PISTON_HEAD = registerHead("turn_push_piston_head", p -> new TurnPushPistonHeadBlock(p), headProperties());
    public static final Block TURN_PUSH_STICKY_PISTON_HEAD = registerHead("turn_push_sticky_piston_head", p -> new TurnPushPistonHeadBlock(p), headProperties());
    public static final Block WALL_MERGE_PISTON_HEAD = registerHead("wall_merge_piston_head", p -> new WallMergePistonHeadBlock(p), headProperties());
    public static final Block WALL_MERGE_STICKY_PISTON_HEAD = registerHead("wall_merge_sticky_piston_head", p -> new WallMergePistonHeadBlock(p), headProperties());
    public static final Block ST_PISTON_HEAD = registerHead("st_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block ST_STICKY_PISTON_HEAD = registerHead("st_sticky_piston_head", p -> new ModPistonHeadBlock(p), headProperties());
    public static final Block GRAVITY_PISTON_HEAD = registerHead("gravity_piston_head", p -> new GravityPistonHeadBlock(p), headProperties());
    public static final Block STRONG_PISTON_HEAD = registerHead("strong_piston_head", p -> new ModPistonHeadBlock(p), headProperties());

    private static final Block[] BASES = {
        HONEY_PISTON, PROJECTILE_PISTON, CHAIN_PISTON, CHAIN_STICKY_PISTON, LOOP_PISTON,
        WIND_CHARGE_PISTON, SILENT_PISTON, RECOIL_PISTON, END_ROD_PISTON, SKULL_PISTON,
        QC_PISTON, QC_STICKY_PISTON, OBSERVER_PISTON, OBSERVER_STICKY_PISTON, REDSTONE_END_ROD_PISTON,
        LONG_PUSH_PISTON, WEAK_PISTON, FAST_PISTON, FAST_STICKY_PISTON,
        POTATO_PISTON, PICKAXE_PISTON, RECURSIVE_PISTON, RECURSIVE_STICKY_PISTON,
        TURN_PUSH_PISTON, TURN_PUSH_STICKY_PISTON, WALL_MERGE_PISTON, WALL_MERGE_STICKY_PISTON,
        ST_PISTON, ST_STICKY_PISTON, GRAVITY_PISTON,
        STRONG_PISTON_1, STRONG_PISTON_2, STRONG_PISTON_3
    };

    private ModBlocks() {
    }

    private static final Object REDSTONE_TAB_KEY = PdHelpers.redstoneTabKey();

    public static void init() {
        //? if >=26.1 {
        CreativeModeTabEvents.modifyOutputEvent(
            (net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) REDSTONE_TAB_KEY
        ).register(entries -> {
            for (Block base : BASES) {
                entries.accept(base);
            }
        });
        //?} else {
        //? if >=1.20.5 {
        ItemGroupEvents.modifyEntriesEvent(
            (net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) REDSTONE_TAB_KEY
        ).register(entries -> {
            for (Block base : BASES) {
                entries.accept(base);
            }
        });
        //?} else {
        ItemGroupEvents.modifyEntriesEvent(
            net.minecraft.world.item.CreativeModeTabs.REDSTONE_BLOCKS
        ).register(entries -> {
            for (Block base : BASES) {
                entries.accept(base);
            }
        });
        //?}
        //?}
    }

    public static int variantCount() {
        return BASES.length;
    }

    /** The matching head block for dye conversions of extended pistons. */
    public static Block headOf(Block base) {
        if (base instanceof ModPistonBaseBlock modded) {
            return modded.headBlock();
        }
        return net.minecraft.world.level.block.Blocks.PISTON_HEAD;
    }

    private static Block registerBase(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Block block = registerBlock(name, factory, properties);
        //? if >=1.21.10 {
        // Newer versions require the item id on Item.Properties before construction (mirrors Blocks.setId).
        net.minecraft.resources.ResourceKey<Item> itemKey = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.ITEM, PdHelpers.id(name)
        );
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
        //?} else {
        Registry.register(BuiltInRegistries.ITEM, PdHelpers.id(name), new BlockItem(block, new Item.Properties()));
        //?}
        return block;
    }

    private static Block registerHead(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        return registerBlock(name, factory, properties);
    }

    /** Structural part (递推杆 / 墙并杆 / 马铃薯移塞): registered without an item. */
    private static Block registerPart(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        return registerBlock(name, factory, movingPistonProperties());
    }

    /** The potato moving piston mirrors the vanilla one's properties (unbreakable, immovable). */
    private static BlockBehaviour.Properties movingPistonProperties() {
        //? if <1.20 {
        return BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.PISTON)
            .strength(-1.0F)
            .dynamicShape()
            .noLootTable()
            .noOcclusion();
        //?} else {
        return BlockBehaviour.Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.STONE)
            .forceSolidOn()
            .strength(-1.0F)
            .dynamicShape()
            .noLootTable()
            .noOcclusion()
            .isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false)
            .isViewBlocking((s, l, p) -> false)
            .pushReaction(PushReaction.BLOCK);
        //?}
    }

    private static Block registerBlock(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        //? if >=1.21.2 {
        // Newer versions require the registry id on the properties before construction.
        net.minecraft.resources.ResourceKey<Block> key = PdHelpers.blockKey(name);
        Block block = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        return block;
        //?} else {
        Block block = factory.apply(properties);
        Registry.register(BuiltInRegistries.BLOCK, PdHelpers.id(name), block);
        return block;
        //?}
    }

    private static BlockBehaviour.Properties baseProperties() {
        //? if <1.20 {
        return BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.PISTON)
            .strength(1.5F);
        //?} else {
        return BlockBehaviour.Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.STONE)
            .strength(1.5F)
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> !state.getValue(ModPistonBaseBlock.EXTENDED))
            .isViewBlocking((state, level, pos) -> !state.getValue(ModPistonBaseBlock.EXTENDED))
            .pushReaction(PushReaction.BLOCK);
        //?}
    }

    private static BlockBehaviour.Properties headProperties() {
        return headProperties(0);
    }

    private static BlockBehaviour.Properties headProperties(int light) {
        BlockBehaviour.Properties props;
        //? if <1.20 {
        props = BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.PISTON)
            .strength(1.5F)
            .noLootTable();
        //?} else {
        props = BlockBehaviour.Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.STONE)
            .strength(1.5F)
            .noLootTable()
            .pushReaction(PushReaction.BLOCK);
        //?}
        if (light > 0) {
            props = props.lightLevel(state -> light);
        }
        return props;
    }

    private static BlockBehaviour.Properties endRodProperties(int light) {
        BlockBehaviour.Properties props = baseProperties().lightLevel(state -> state.getValue(ModPistonBaseBlock.EXTENDED) ? 0 : light);
        return props;
    }
}
