package dev.ehtid.thaumcraft.port.core;

/** Exact BETA26 shapeless TUBES upgrades: valve, restrict and one-way. */
public final class LegacyEssentiaTubeUpgradeRecipes {
    private LegacyEssentiaTubeUpgradeRecipes() {}
    public static final String RESEARCH = "TUBES";
    public static final int VIS = 10;

    public enum Upgrade {
        VALVE(-1, 0, true),
        RESTRICT(12, 1 << 3, false), // Terra
        ONEWAY(11, 1 << 2, false); // Aqua

        public final int crystalSlot;
        public final int crystalMask;
        public final boolean lever;
        Upgrade(int crystalSlot, int crystalMask, boolean lever) {
            this.crystalSlot = crystalSlot;
            this.crystalMask = crystalMask;
            this.lever = lever;
        }
    }

    /** Returns the tube and optional lever indices in the shapeless 3x3 grid. */
    public static int[] match(Upgrade kind, boolean[] tube, boolean[] lever, boolean[] occupied) {
        if (kind == null || tube == null || lever == null || occupied == null
                || tube.length != 9 || lever.length != 9 || occupied.length != 9) return null;
        int t = -1, l = -1;
        for (int i = 0; i < 9; i++) {
            if (!occupied[i]) continue;
            if (tube[i] && t < 0) t = i;
            else if (kind.lever && lever[i] && l < 0) l = i;
            else return null;
        }
        if (t < 0 || (kind.lever && l < 0)) return null;
        return new int[]{t,l};
    }

    public static int discountedVis(float discount) {
        return (int)(VIS * (1.0F - discount));
    }
}
