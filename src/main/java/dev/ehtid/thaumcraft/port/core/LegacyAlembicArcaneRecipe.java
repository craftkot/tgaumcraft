package dev.ehtid.thaumcraft.port.core;

/**
 * Original TC6 6.1.BETA26 ConfigRecipes "Alembic", not a free craft.
 * Research ESSENTIASMELTER, 50 vis, one Aqua crystal.
 * WFW / SBS / WFW: W=greatwood plank, F=alchemical filter,
 * S=brass plate, B=vanilla bucket.
 */
public final class LegacyAlembicArcaneRecipe {
    private LegacyAlembicArcaneRecipe() {}

    public static final String RESEARCH = "ESSENTIASMELTER";
    public static final int VIS = 50;
    public static final int AQUA_SLOT = 11;
    public static final int CRYSTAL_MASK = 1 << 2;
    private static final String[] PATTERN = {"WFW", "SBS", "WFW"};

    public static boolean matches(boolean[] greatwood, boolean[] filter,
                                  boolean[] brassPlate, boolean[] bucket) {
        if (greatwood == null || filter == null || brassPlate == null || bucket == null
                || greatwood.length != 9 || filter.length != 9
                || brassPlate.length != 9 || bucket.length != 9) return false;
        for (int row=0;row<3;row++)
            for (int col=0;col<3;col++) {
                int slot=row*3+col;
                if (!switch (PATTERN[row].charAt(col)) {
                    case 'W' -> greatwood[slot];
                    case 'F' -> filter[slot];
                    case 'S' -> brassPlate[slot];
                    case 'B' -> bucket[slot];
                    default -> false;
                }) return false;
            }
        return true;
    }

    public static int discountedVis(float discount) {
        return (int)(VIS*(1.0F-discount));
    }
}
