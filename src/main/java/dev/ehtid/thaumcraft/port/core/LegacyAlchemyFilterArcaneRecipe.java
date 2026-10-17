package dev.ehtid.thaumcraft.port.core;

/**
 * Thaumcraft 6.1.BETA26 ConfigRecipes Filter:
 * BASEALCHEMY, 15 vis, one Aqua crystal; "GWG" shaped 3x1,
 * gold ingot / silverwood planks / gold ingot, yields two filters.
 * ShapedOreRecipe may place the horizontal recipe in any of three rows.
 */
public final class LegacyAlchemyFilterArcaneRecipe {
    private LegacyAlchemyFilterArcaneRecipe() {}
    public static final String RESEARCH = "BASEALCHEMY";
    public static final int VIS = 15;
    public static final int OUTPUT = 2;
    public static final int AQUA_SLOT = 11;
    public static final int CRYSTAL_MASK = 1 << 2;

    /** Returns consumed grid slots, or null if any ingredient is missing/extra. */
    public static int[] inputSlots(boolean[] gold, boolean[] silverwood, boolean[] occupied) {
        if (gold == null || silverwood == null || occupied == null
                || gold.length != 9 || silverwood.length != 9 || occupied.length != 9)
            return null;
        for (int row = 0; row < 3; row++) {
            int left = row * 3, middle = left + 1, right = left + 2;
            if (!gold[left] || !silverwood[middle] || !gold[right]) continue;
            boolean valid = true;
            for (int slot = 0; slot < 9; slot++) {
                boolean mustOccupy = slot == left || slot == middle || slot == right;
                if (occupied[slot] != mustOccupy) { valid = false; break; }
            }
            if (valid) return new int[]{left,middle,right};
        }
        return null;
    }

    public static int discountedVis(float discount) {
        return (int)(VIS * (1.0F - discount));
    }
}
