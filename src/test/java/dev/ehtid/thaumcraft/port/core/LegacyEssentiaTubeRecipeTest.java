package dev.ehtid.thaumcraft.port.core;

public final class LegacyEssentiaTubeRecipeTest {
    private static int checked;
    private static void eq(Object a,Object b) {
        checked++;
        if(!java.util.Objects.equals(a,b))
            throw new AssertionError("expected "+a+", got "+b);
    }
    public static void main(String[] args) {
        eq("TUBES",LegacyEssentiaTubeRecipe.RESEARCH);
        eq(10,LegacyEssentiaTubeRecipe.BASE_VIS);
        eq(8,LegacyEssentiaTubeRecipe.OUTPUT);
        eq(5,LegacyEssentiaTubeRecipe.QUICKSILVER_NUGGET_META);
        eq(8,LegacyEssentiaTubeRecipe.BRASS_NUGGET_META);
        eq(1,LegacyEssentiaTubeRecipe.IRON_PLATE_META);
        var q=new boolean[9];var i=new boolean[9];var g=new boolean[9];
        var b=new boolean[9];var e=new boolean[9];
        q[1]=true;i[3]=true;i[5]=true;g[4]=true;b[7]=true;
        for(int p:new int[]{0,2,6,8})e[p]=true;
        eq(true,LegacyEssentiaTubeRecipe.matches(q,i,g,b,e));
        for(int p:new int[]{1,3,4,5,7}) {
            boolean[] a=p==1?q:p==4?g:p==7?b:i;
            a[p]=false;
            eq(false,LegacyEssentiaTubeRecipe.matches(q,i,g,b,e));
            a[p]=true;
        }
        for(int p:new int[]{0,2,6,8}) {
            e[p]=false;
            eq(false,LegacyEssentiaTubeRecipe.matches(q,i,g,b,e));
            e[p]=true;
        }
        eq(false,LegacyEssentiaTubeRecipe.matches(new boolean[0],i,g,b,e));
        eq(10,LegacyEssentiaTubeRecipe.discountedVis(0));
        eq(7,LegacyEssentiaTubeRecipe.discountedVis(0.25F));
        eq(0,LegacyEssentiaTubeRecipe.discountedVis(1F));
        System.out.println("BETA26 eight-tube arcane crafting: OK ("+checked+" assertions)");
    }
}
