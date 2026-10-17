package dev.ehtid.thaumcraft.port.core;

public final class LegacyTubeBufferArcaneRecipeTest {
    private static int assertions;
    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        eq("TUBES", LegacyTubeBufferArcaneRecipe.RESEARCH);
        eq(25, LegacyTubeBufferArcaneRecipe.BASE_VIS);
        boolean[] phial=new boolean[9],valve=new boolean[9],
                  tube=new boolean[9],iron=new boolean[9],restrict=new boolean[9];
        for(int slot:new int[]{0,2,6,8})phial[slot]=true;
        valve[1]=true;
        for(int slot:new int[]{3,5})tube[slot]=true;
        iron[4]=true;
        restrict[7]=true;
        eq(true,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
        for(int slot:new int[]{0,2,6,8}) {
            phial[slot]=false;
            eq(false,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
            phial[slot]=true;
        }
        valve[1]=false;
        eq(false,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
        valve[1]=true;
        tube[3]=false;
        eq(false,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
        tube[3]=true;
        tube[5]=false;
        eq(false,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
        tube[5]=true;
        iron[4]=false;
        eq(false,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
        iron[4]=true;
        restrict[7]=false;
        eq(false,LegacyTubeBufferArcaneRecipe.matches(phial,valve,tube,iron,restrict));
        restrict[7]=true;
        eq(false,LegacyTubeBufferArcaneRecipe.matches(new boolean[0],valve,tube,iron,restrict));
        eq(false,LegacyTubeBufferArcaneRecipe.matches(null,valve,tube,iron,restrict));
        eq(25,LegacyTubeBufferArcaneRecipe.discountedVis(0));
        eq(18,LegacyTubeBufferArcaneRecipe.discountedVis(0.25F));
        eq(0,LegacyTubeBufferArcaneRecipe.discountedVis(1.0F));
        System.out.println("BETA26 essentia buffer arcane crafting: OK ("+assertions+" assertions)");
    }
}
