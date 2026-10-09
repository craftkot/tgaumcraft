package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.List;
import java.util.Set;

/**
 * Exact BETA26 getBonusTags rule: any item exposing an OreDictionary dyeBlack,
 * dyeRed, ..., dyeWhite name gets Aspect.SENSES merged to at least 5.
 * Minecraft 1.13+ flattened dye items; modern variants retain this rule.
 */
public final class LegacyDyeAspectBonuses {
    private LegacyDyeAspectBonuses() {}

    private static final Set<String> VANILLA_DYE_ITEMS = Set.of(
            "minecraft:ink_sac", "minecraft:red_dye", "minecraft:green_dye",
            "minecraft:cocoa_beans", "minecraft:lapis_lazuli",
            "minecraft:purple_dye", "minecraft:cyan_dye",
            "minecraft:light_gray_dye", "minecraft:gray_dye",
            "minecraft:pink_dye", "minecraft:lime_dye",
            "minecraft:yellow_dye", "minecraft:light_blue_dye",
            "minecraft:magenta_dye", "minecraft:orange_dye",
            "minecraft:bone_meal",
            // Post-flattening colored equivalents of the old dye item.
            "minecraft:black_dye", "minecraft:brown_dye",
            "minecraft:blue_dye", "minecraft:white_dye"
    );

    public static List<String> modernTagNames() {
        return List.of(
                "c:dyes/black", "c:dyes/red", "c:dyes/green", "c:dyes/brown",
                "c:dyes/blue", "c:dyes/purple", "c:dyes/cyan",
                "c:dyes/light_gray", "c:dyes/gray", "c:dyes/pink",
                "c:dyes/lime", "c:dyes/yellow", "c:dyes/light_blue",
                "c:dyes/magenta", "c:dyes/orange", "c:dyes/white"
        );
    }

    public static AspectListData apply(AspectRegistry registry, AspectListData base, String itemId,
                                       boolean taggedAsDye) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(base, "base");
        AspectListData out = base.copy();
        if (taggedAsDye || VANILLA_DYE_ITEMS.contains(itemId)) {
            out.merge(registry.SENSES, 5);
        }
        return out;
    }
}
