package dev.ehtid.thaumcraft.port.core;

/**
 * Original Thaumcraft 6.1.BETA26 ConfigRecipes TubeBuffer:
 * research TUBES, 25 vis, no crystals, PVP / TWT / PRP.
 * P = empty phial, V = valve tube, T = ordinary tube,
 * W = iron plate, R = restrictive tube.
 */
public final class LegacyTubeBufferArcaneRecipe {
    private LegacyTubeBufferArcaneRecipe() {}

    public static final String RESEARCH = "TUBES";
    public static final int BASE_VIS = 25;
    private static final String[] PATTERN = {"PVP", "TWT", "PRP"};

    public static boolean matches(boolean[] emptyPhial, boolean[] valve,
                                  boolean[] ordinaryTube, boolean[] ironPlate,
                                  boolean[] restrictTube) {
        if (emptyPhial == null || valve == null || ordinaryTube == null
                || ironPlate == null || restrictTube == null
                || emptyPhial.length != 9 || valve.length != 9
                || ordinaryTube.length != 9 || ironPlate.length != 9
                || restrictTube.length != 9) return false;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = row * 3 + col;
                boolean correct = switch (PATTERN[row].charAt(col)) {
                    case 'P' -> emptyPhial[slot];
                    case 'V' -> valve[slot];
                    case 'T' -> ordinaryTube[slot];
                    case 'W' -> ironPlate[slot];
                    case 'R' -> restrictTube[slot];
                    default -> false;
                };
                if (!correct) return false;
            }
        }
        return true;
    }

    public static int discountedVis(float discount) {
        return (int)(BASE_VIS * (1.0F - discount));
    }
}
