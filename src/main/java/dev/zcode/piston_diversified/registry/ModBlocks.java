package dev.zcode.piston_diversified.registry;

import dev.zcode.piston_diversified.PdHelpers;
import dev.zcode.piston_diversified.block.ChainPistonBlock;
import dev.zcode.piston_diversified.block.EndRodPistonBlock;
import dev.zcode.piston_diversified.block.EndRodPistonHeadBlock;
import dev.zcode.piston_diversified.block.FastPistonBlock;
import dev.zcode.piston_diversified.block.HoneyPistonBlock;
import dev.zcode.piston_diversified.block.LongPushPistonBlock;
import dev.zcode.piston_diversified.block.LoopPistonBlock;
import dev.zcode.piston_diversified.block.ModPistonBaseBlock;
import dev.zcode.piston_diversified.block.ModPistonHeadBlock;
import dev.zcode.piston_diversified.block.ObserverPistonBlock;
import dev.zcode.piston_diversified.block.ProjectilePistonBlock;
import dev.zcode.piston_diversified.block.QcPistonBlock;
import dev.zcode.piston_diversified.block.RecoilPistonBlock;
import dev.zcode.piston_diversified.block.RecoilPistonHeadBlock;
import dev.zcode.piston_diversified.block.RedstoneEndRodPistonBlock;
import dev.zcode.piston_diversified.block.RedstoneEndRodPistonHeadBlock;
import dev.zcode.piston_diversified.block.SilentPistonBlock;
import dev.zcode.piston_diversified.block.SkullPistonBlock;
import dev.zcode.piston_diversified.block.SkullPistonHeadBlock;
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
 * Registers the 19 v1 piston variants (bases + heads). Bases are placed on the vanilla
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

    private static final Block[] BASES = {
        HONEY_PISTON, PROJECTILE_PISTON, CHAIN_PISTON, CHAIN_STICKY_PISTON, LOOP_PISTON,
        WIND_CHARGE_PISTON, SILENT_PISTON, RECOIL_PISTON, END_ROD_PISTON, SKULL_PISTON,
        QC_PISTON, QC_STICKY_PISTON, OBSERVER_PISTON, OBSERVER_STICKY_PISTON, REDSTONE_END_ROD_PISTON,
        LONG_PUSH_PISTON, WEAK_PISTON, FAST_PISTON, FAST_STICKY_PISTON
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
