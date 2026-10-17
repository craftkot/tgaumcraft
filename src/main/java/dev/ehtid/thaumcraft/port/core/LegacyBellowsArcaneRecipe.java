package dev.ehtid.thaumcraft.port.core;

/**
 * Original Thaumcraft 6.1.BETA26 ConfigRecipes Bellows arcane recipe:
 * BELLOWS, 25 vis, one Aer crystal, WW / LLI / WW.
 * ShapedOreRecipe supports horizontal mirroring.
 */
public final class LegacyBellowsArcaneRecipe {
    private LegacyBellowsArcaneRecipe() {}
    public static final String RESEARCH = "BELLOWS";
    public static final int VIS = 25;
    public static final int CRYSTAL_SLOT = 9;
    public static final int CRYSTAL_MASK = 1;

    public static boolean matches(boolean[] planks, boolean[] leather,
                                  boolean[] iron, boolean[] empty) {
        if (planks == null || leather == null || iron == null || empty == null ||
                planks.length != 9 || leather.length != 9 ||
                iron.length != 9 || empty.length != 9) return false;
        return matchesOrientation(planks, leather, iron, empty, false)
                || matchesOrientation(planks, leather, iron, empty, true);
    }

    private static boolean matchesOrientation(boolean[] planks, boolean[] leather,
                                               boolean[] iron, boolean[] empty,
                                               boolean mirrored) {
        final String[] pattern = {"WW ", "LLI", "WW "};
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                char want = pattern[row].charAt(mirrored ? 2-col : col);
                int slot = row*3+col;
                if (want == 'W' && !planks[slot] ||
                    want == 'L' && !leather[slot] ||
                    want == 'I' && !iron[slot] ||
                    want == ' ' && !empty[slot]) return false;
            }
        return true;
    }

    public static int discountedVis(float discount) {
        return (int)(VIS*(1.0F-discount));
    }
}
