package dev.ehtid.thaumcraft.port.core;

public final class LegacyAlembicArcaneRecipeTest {
    private static int checks;
    private static void eq(Object expected,Object actual) {
        checks++;
        if(!java.util.Objects.equals(expected,actual))
            throw new AssertionError("expected "+expected+", actual "+actual);
    }
    public static void main(String[] args) {
        eq("ESSENTIASMELTER",LegacyAlembicArcaneRecipe.RESEARCH);
        eq(50,LegacyAlembicArcaneRecipe.VIS);
        eq(11,LegacyAlembicArcaneRecipe.AQUA_SLOT);
        eq(1<<2,LegacyAlembicArcaneRecipe.CRYSTAL_MASK);
        boolean[] w=new boolean[9],f=new boolean[9],s=new boolean[9],b=new boolean[9];
        for(int pos:new int[]{0,2,6,8})w[pos]=true;
        for(int pos:new int[]{1,7})f[pos]=true;
        for(int pos:new int[]{3,5})s[pos]=true;
        b[4]=true;
        eq(true,LegacyAlembicArcaneRecipe.matches(w,f,s,b));
        for(int pos:new int[]{0,2,6,8}) {
            w[pos]=false;
            eq(false,LegacyAlembicArcaneRecipe.matches(w,f,s,b));
            w[pos]=true;
        }
        for(int pos:new int[]{1,7}) {
            f[pos]=false;
            eq(false,LegacyAlembicArcaneRecipe.matches(w,f,s,b));
            f[pos]=true;
        }
        for(int pos:new int[]{3,5}) {
            s[pos]=false;
            eq(false,LegacyAlembicArcaneRecipe.matches(w,f,s,b));
            s[pos]=true;
        }
        b[4]=false;
        eq(false,LegacyAlembicArcaneRecipe.matches(w,f,s,b));
        eq(false,LegacyAlembicArcaneRecipe.matches(new boolean[8],f,s,b));
        eq(false,LegacyAlembicArcaneRecipe.matches(null,f,s,b));
        eq(50,LegacyAlembicArcaneRecipe.discountedVis(0F));
        eq(37,LegacyAlembicArcaneRecipe.discountedVis(0.25F));
        eq(0,LegacyAlembicArcaneRecipe.discountedVis(1F));
        System.out.println("BETA26 alembic arcane crafting: OK ("+checks+" assertions)");
    }
}
