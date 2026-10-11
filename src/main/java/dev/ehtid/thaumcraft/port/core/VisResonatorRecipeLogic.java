package dev.ehtid.thaumcraft.port.core;

/**
 * The original BETA26 ConfigRecipes ShapelessArcaneRecipe registered as
 * thaumcraft:vis_resonator. Decompiled constructor arguments:
 * UNLOCKAUROMANCY@2, 50 vis, Aer x1, Aqua x1, plateIron, gemQuartz.
 * This pure matcher preserves the order-independent *two ingredient* grid,
 * including items that may belong to both ore-equivalence tags.
 */
public final class VisResonatorRecipeLogic {
    private VisResonatorRecipeLogic() {}

    public static final String RESEARCH = "UNLOCKAUROMANCY@2";
    public static final int BASE_VIS = 50;
    // ArcaneWorkbenchRuntime.PRIMAL_ORDER: aer, ignis, aqua, terra, ordo, perditio.
    public static final int REQUIRED_CRYSTAL_MASK = (1 << 0) | (1 << 2);

    /** Original slot crafting vis-cost rounding: truncation toward zero. */
    public static int discountedVis(float discount) {
        return (int) (BASE_VIS * (1.0F - discount));
    }

    /**
     * True shapeless/ore-equivalent matching over the nine crafting grid
     * slots; exactly one plateIron and one gemQuartz, in either order.
     * Returns [plate slot, quartz slot], or null when not matched.
     */
    public static int[] matchGrid(boolean[] occupied, boolean[] ironPlate,
                                  boolean[] gemQuartz) {
        if (occupied == null || ironPlate == null || gemQuartz == null
                || occupied.length != 9 || ironPlate.length != 9
                || gemQuartz.length != 9) return null;
        int first = -1;
        int second = -1;
        for (int i = 0; i < 9; i++) {
            if (!occupied[i]) continue;
            if (first == -1) first = i;
            else if (second == -1) second = i;
            else return null;
        }
        if (second == -1) return null;
        if (ironPlate[first] && gemQuartz[second])
            return new int[] {first, second};
        if (ironPlate[second] && gemQuartz[first])
            return new int[] {second, first};
        return null;
    }
}
