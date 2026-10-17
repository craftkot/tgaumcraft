package dev.ehtid.thaumcraft.port.core;

public final class LegacyBellowsArcaneRecipeTest {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected,actual))
            throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        eq("BELLOWS",LegacyBellowsArcaneRecipe.RESEARCH);
        eq(25,LegacyBellowsArcaneRecipe.VIS);
        eq(9,LegacyBellowsArcaneRecipe.CRYSTAL_SLOT);
        boolean[] w=new boolean[9],l=new boolean[9],i=new boolean[9],e=new boolean[9];
        for(int p:new int[]{0,1,6,7})w[p]=true;
        for(int p:new int[]{3,4})l[p]=true;
        i[5]=true;e[2]=true;e[8]=true;
        eq(true,LegacyBellowsArcaneRecipe.matches(w,l,i,e));
        w[0]=false;
        eq(false,LegacyBellowsArcaneRecipe.matches(w,l,i,e));
        w[0]=true;
        for(int p:new int[]{0,1,6,7})w[p]=false;
        for(int p:new int[]{3,4})l[p]=false;
        i[5]=false;e[2]=false;e[8]=false;
        for(int p:new int[]{1,2,7,8})w[p]=true;
        for(int p:new int[]{4,5})l[p]=true;
        i[3]=true;e[0]=true;e[6]=true;
        eq(true,LegacyBellowsArcaneRecipe.matches(w,l,i,e));
        e[0]=false;
        eq(false,LegacyBellowsArcaneRecipe.matches(w,l,i,e));
        eq(25,LegacyBellowsArcaneRecipe.discountedVis(0));
        eq(18,LegacyBellowsArcaneRecipe.discountedVis(0.25F));
        eq(0,LegacyBellowsArcaneRecipe.discountedVis(1));
        System.out.println("BETA26 bellows arcane crafting: OK ("+checks+" assertions)");
    }
}
