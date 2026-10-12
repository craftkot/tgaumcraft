package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/**
 * Exact EnumDyeColor iteration and BETA26 nitor_%s block registry names.
 * "silver" and "lightblue" retain the old 1.12 names while modern dye
 * ingredient identifiers are "light_gray" and "light_blue".
 *
 * BETA26 ConfigRecipes uses ConfigAspects.dyes[15-i] to choose the
 * matching OreDictionary dye for each EnumDyeColor in this order.
 */
public final class LegacyNitorColors {
    private LegacyNitorColors() {}

    public record Variant(String legacy, String modernDye, int particleRgb) {
        public String blockId() { return "thaumcraft:nitor_" + legacy; }
        public String dyeId() { return "minecraft:" + modernDye + "_dye"; }
        public String recipeId() { return "thaumcraft:nitordye_" + legacy; }
    }

    private static final List<Variant> ORIGINAL = List.of(
            new Variant("white", "white", 0xF9FFFE),
            new Variant("orange", "orange", 0xF9801D),
            new Variant("magenta", "magenta", 0xC74EBD),
            new Variant("lightblue", "light_blue", 0x3AB3DA),
            new Variant("yellow", "yellow", 0xFED83D),
            new Variant("lime", "lime", 0x80C71F),
            new Variant("pink", "pink", 0xF38BAA),
            new Variant("gray", "gray", 0x474F52),
            new Variant("silver", "light_gray", 0x9D9D97),
            new Variant("cyan", "cyan", 0x169C9C),
            new Variant("purple", "purple", 0x8932B8),
            new Variant("blue", "blue", 0x3C44AA),
            new Variant("brown", "brown", 0x835432),
            new Variant("green", "green", 0x5E7C16),
            new Variant("red", "red", 0xB02E26),
            new Variant("black", "black", 0x1D1D21)
    );

    public static List<Variant> variants() { return ORIGINAL; }

    public static Variant get(String oldName) {
        for (Variant variant : ORIGINAL)
            if (variant.legacy().equals(oldName)) return variant;
        return null;
    }
}
