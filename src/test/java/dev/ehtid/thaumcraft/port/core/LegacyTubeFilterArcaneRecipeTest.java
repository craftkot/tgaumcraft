package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;

public final class LegacyTubeFilterArcaneRecipeTest {
    private static int checked;
    private static void eq(Object expected,Object actual) {
        checked++;
        if(!java.util.Objects.equals(expected,actual))
            throw new AssertionError(expected+" != "+actual);
    }
    private static void slots(int[] expected,int[] actual) {
        checked++;
        if(!Arrays.equals(expected,actual))
            throw new AssertionError(Arrays.toString(expected)+" != "+Arrays.toString(actual));
    }
    public static void main(String[] args) {
        eq("TUBES",LegacyTubeFilterArcaneRecipe.RESEARCH);
        eq(10,LegacyTubeFilterArcaneRecipe.VIS);
        boolean[] tube=new boolean[9],filter=new boolean[9],occupied=new boolean[9];
        tube[7]=true;filter[2]=true;occupied[7]=occupied[2]=true;
        slots(new int[]{7,2},LegacyTubeFilterArcaneRecipe.inputSlots(tube,filter,occupied));
        tube[7]=false;occupied[7]=false;tube[1]=true;occupied[1]=true;
        slots(new int[]{1,2},LegacyTubeFilterArcaneRecipe.inputSlots(tube,filter,occupied));
        occupied[5]=true;
        eq(null,LegacyTubeFilterArcaneRecipe.inputSlots(tube,filter,occupied));
        occupied[5]=false;
        tube[0]=true;occupied[0]=true;
        eq(null,LegacyTubeFilterArcaneRecipe.inputSlots(tube,filter,occupied));
        tube[0]=false;occupied[0]=false;
        filter[2]=false;
        eq(null,LegacyTubeFilterArcaneRecipe.inputSlots(tube,filter,occupied));
        eq(null,LegacyTubeFilterArcaneRecipe.inputSlots(new boolean[0],filter,occupied));
        eq(10,LegacyTubeFilterArcaneRecipe.discountedVis(0));
        eq(7,LegacyTubeFilterArcaneRecipe.discountedVis(0.25F));
        eq(0,LegacyTubeFilterArcaneRecipe.discountedVis(1.0F));
        System.out.println("BETA26 filtered tube arcane crafting: OK ("+checked+" assertions)");
    }
}
