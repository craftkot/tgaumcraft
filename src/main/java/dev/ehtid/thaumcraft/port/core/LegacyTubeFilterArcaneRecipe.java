package dev.ehtid.thaumcraft.port.core;

/**
 * Original BETA26 ConfigRecipes TubeFilter: a shapeless arcane craft.
 * TUBES research, 10 vis, no crystals, one basic tube + one filter.
 */
public final class LegacyTubeFilterArcaneRecipe {
    private LegacyTubeFilterArcaneRecipe() {}
    public static final String RESEARCH = "TUBES";
    public static final int VIS = 10;

    public static int[] inputSlots(boolean[] tube, boolean[] filter, boolean[] occupied) {
        if (tube == null || filter == null || occupied == null ||
            tube.length != 9 || filter.length != 9 || occupied.length != 9) return null;
        int tubeSlot=-1, filterSlot=-1;
        for (int slot=0;slot<9;slot++) {
            if (!occupied[slot]) continue;
            if (tube[slot] && tubeSlot < 0) tubeSlot=slot;
            else if (filter[slot] && filterSlot < 0) filterSlot=slot;
            else return null;
        }
        return tubeSlot >= 0 && filterSlot >= 0 ? new int[]{tubeSlot,filterSlot} : null;
    }

    public static int discountedVis(float discount) {
        return (int)(VIS*(1.0F-discount));
    }
}
