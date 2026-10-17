package dev.ehtid.thaumcraft.port.core;

public final class LegacyUpgradedSmelterArcaneRecipeTest {
    private static int checks;
    private static void ok(boolean result) {
        checks++;
        if(!result)throw new AssertionError("upgraded smelter arcane recipe");
    }
    public static void main(String[] args) {
        var t=LegacyUpgradedSmelterArcaneRecipe.Kind.THAUMIUM;
        var v=LegacyUpgradedSmelterArcaneRecipe.Kind.VOID;
        ok(t.research.equals("ESSENTIASMELTERTHAUMIUM"));
        ok(v.research.equals("ESSENTIASMELTERVOID"));
        ok(t.vis==250&&t.ignisCrystals==2);
        ok(v.vis==750&&v.ignisCrystals==3);
        ok(LegacyUpgradedSmelterArcaneRecipe.IGNIS_SLOT==10);
        ok(LegacyUpgradedSmelterArcaneRecipe.CRYSTAL_MASK==2);
        boolean[] b=new boolean[9],f=new boolean[9],i=new boolean[9],g=new boolean[9];
        b[0]=b[2]=true;
        f[1]=true;
        g[4]=true;
        for(int s:new int[]{3,5,6,7,8})i[s]=true;
        ok(LegacyUpgradedSmelterArcaneRecipe.matches(b,f,i,g));
        for(int s:new int[]{0,2}) {
            b[s]=false;
            ok(!LegacyUpgradedSmelterArcaneRecipe.matches(b,f,i,g));
            b[s]=true;
        }
        f[1]=false;
        ok(!LegacyUpgradedSmelterArcaneRecipe.matches(b,f,i,g));
        f[1]=true;
        g[4]=false;
        ok(!LegacyUpgradedSmelterArcaneRecipe.matches(b,f,i,g));
        g[4]=true;
        for(int s:new int[]{3,5,6,7,8}) {
            i[s]=false;
            ok(!LegacyUpgradedSmelterArcaneRecipe.matches(b,f,i,g));
            i[s]=true;
        }
        ok(!LegacyUpgradedSmelterArcaneRecipe.matches(null,f,i,g));
        ok(!LegacyUpgradedSmelterArcaneRecipe.matches(new boolean[8],f,i,g));
        ok(LegacyUpgradedSmelterArcaneRecipe.discountedVis(t,0F)==250);
        ok(LegacyUpgradedSmelterArcaneRecipe.discountedVis(v,0F)==750);
        ok(LegacyUpgradedSmelterArcaneRecipe.discountedVis(t,0.2F)==200);
        ok(LegacyUpgradedSmelterArcaneRecipe.discountedVis(v,1F)==0);
        System.out.println("BETA26 upgraded smelter arcane crafting: OK ("+checks+" checks)");
    }
}
