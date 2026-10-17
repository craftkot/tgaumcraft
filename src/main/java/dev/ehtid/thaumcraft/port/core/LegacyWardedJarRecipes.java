package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 ConfigRecipes originals, not ordinary free crafting:
 * WardedJar: research WARDEDJARS, 5 vis, GWG/G G/GGG with wooden slab.
 * JarVoid: research WARDEDJARS, 50 vis, one Perditio crystal, one
 * existing normal jar anywhere in the 3x3 crafting area.
 *
 * ShapedArcaneVoidJar also carries the original jar's NBT to the void jar.
 * The live bench is responsible for preserving 1.21 block-entity components.
 */
public final class LegacyWardedJarRecipes {
    private LegacyWardedJarRecipes() {}
    public static final String RESEARCH = "WARDEDJARS";
    public static final int NORMAL_VIS = 5;
    public static final int VOID_VIS = 50;
    public static final int VOID_CRYSTAL_SLOT = 14; // Perditio
    public static final int VOID_CRYSTAL_MASK = 1 << 5;

    /** Exactly GWG / G G / GGG, without mirroring/rotating the shape. */
    public static boolean normalPattern(boolean[] pane, boolean[] slab, boolean[] empty) {
        if (pane == null || slab == null || empty == null ||
                pane.length != 9 || slab.length != 9 || empty.length != 9)
            return false;
        for (int i = 0; i < 9; i++) {
            if (i == 1) {
                if (!slab[i]) return false;
            } else if (i == 4) {
                if (!empty[i]) return false;
            } else if (!pane[i]) return false;
        }
        return true;
    }

    /** Original 1x1 shaped recipe matches in ANY 3x3 position. */
    public static int voidJarSlot(boolean[] occupied, boolean[] normalJar) {
        if (occupied == null || normalJar == null ||
                occupied.length != 9 || normalJar.length != 9)
            return -1;
        int found = -1;
        for (int i = 0; i < 9; i++) {
            if (!occupied[i]) continue;
            if (found >= 0 || !normalJar[i]) return -1;
            found = i;
        }
        return found;
    }

    /** Source SlotCraftingArcaneWorkbench truncates toward zero. */
    public static int discountedVis(int base, float discount) {
        return (int) (base * (1.0F - discount));
    }
}
