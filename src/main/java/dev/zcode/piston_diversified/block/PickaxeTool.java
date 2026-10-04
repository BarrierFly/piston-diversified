package dev.zcode.piston_diversified.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * The six pickaxe materials the 镐 piston can carry (drives its TOOL blockstate → plate texture).
 */
public enum PickaxeTool implements StringRepresentable {
    WOODEN("wooden"),
    STONE("stone"),
    IRON("iron"),
    GOLDEN("golden"),
    DIAMOND("diamond"),
    NETHERITE("netherite");

    public static final PickaxeTool DEFAULT = IRON;

    private final String name;

    PickaxeTool(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** Material derived from a pickaxe item's registry path ("golden_pickaxe" → GOLDEN). */
    public static PickaxeTool of(ItemStack stack) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!path.endsWith("_pickaxe")) {
            return null;
        }
        String prefix = path.substring(0, path.length() - "_pickaxe".length());
        for (PickaxeTool tool : values()) {
            if (tool.name.equals(prefix)) {
                return tool;
            }
        }
        return null;
    }
}
