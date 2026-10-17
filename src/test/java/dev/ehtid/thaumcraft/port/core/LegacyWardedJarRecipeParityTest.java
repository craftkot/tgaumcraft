package dev.ehtid.thaumcraft.port.core;

public final class LegacyWardedJarRecipeParityTest {
    private static int count;
    private static void eq(Object expected, Object actual) {
        count++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("wanted " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        eq("WARDEDJARS", LegacyWardedJarRecipes.RESEARCH);
        eq(5, LegacyWardedJarRecipes.NORMAL_VIS);
        eq(50, LegacyWardedJarRecipes.VOID_VIS);
        eq(14, LegacyWardedJarRecipes.VOID_CRYSTAL_SLOT);
        eq(32, LegacyWardedJarRecipes.VOID_CRYSTAL_MASK);
        boolean[] pane = new boolean[9], slab = new boolean[9], empty = new boolean[9];
        for (int i : new int[] {0,2,3,5,6,7,8}) pane[i] = true;
        slab[1] = true; empty[4] = true;
        eq(true, LegacyWardedJarRecipes.normalPattern(pane, slab, empty));
        pane[3] = false;
        eq(false, LegacyWardedJarRecipes.normalPattern(pane, slab, empty));
        pane[3] = true;
        slab[1] = false;
        eq(false, LegacyWardedJarRecipes.normalPattern(pane, slab, empty));
        slab[1] = true;
        empty[4] = false;
        eq(false, LegacyWardedJarRecipes.normalPattern(pane, slab, empty));
        empty[4] = true;
        eq(false, LegacyWardedJarRecipes.normalPattern(null, slab, empty));
        boolean[] occupied = new boolean[9], jars = new boolean[9];
        eq(-1, LegacyWardedJarRecipes.voidJarSlot(occupied, jars));
        for (int i = 0; i < 9; i++) {
            occupied[i] = true; jars[i] = true;
            eq(i, LegacyWardedJarRecipes.voidJarSlot(occupied, jars));
            jars[i] = false;
            eq(-1, LegacyWardedJarRecipes.voidJarSlot(occupied, jars));
            occupied[i] = false;
        }
        occupied[0] = occupied[8] = true;
        jars[0] = jars[8] = true;
        eq(-1, LegacyWardedJarRecipes.voidJarSlot(occupied, jars));
        eq(-1, LegacyWardedJarRecipes.voidJarSlot(new boolean[0], new boolean[0]));
        eq(50, LegacyWardedJarRecipes.discountedVis(50, 0.0F));
        eq(37, LegacyWardedJarRecipes.discountedVis(50, 0.25F));
        eq(0, LegacyWardedJarRecipes.discountedVis(50, 1.0F));
        eq(-5, LegacyWardedJarRecipes.discountedVis(50, 1.1F));
        System.out.println("BETA26 warded/void jar arcane recipes: OK (" + count + " assertions)");
    }
}
