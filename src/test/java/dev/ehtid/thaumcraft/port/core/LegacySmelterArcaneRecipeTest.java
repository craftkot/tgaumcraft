package dev.ehtid.thaumcraft.port.core;
public final class LegacySmelterArcaneRecipeTest {
    private static int n;
    private static void eq(Object a,Object b) {
        n++;
        if(!java.util.Objects.equals(a,b))
            throw new AssertionError("expected "+a+" got "+b);
    }
    public static void main(String[] args){
        eq("ESSENTIASMELTER@2",LegacySmelterArcaneRecipe.RESEARCH);
        eq(50,LegacySmelterArcaneRecipe.BASE_VIS);
        eq(10,LegacySmelterArcaneRecipe.IGNIS_SLOT);
        eq(2,LegacySmelterArcaneRecipe.CRYSTAL_MASK);
        var brass=new boolean[9];var crucible=new boolean[9];
        var furnace=new boolean[9];var cobble=new boolean[9];
        brass[0]=brass[2]=true;crucible[1]=true;furnace[4]=true;
        for(int i:new int[]{3,5,6,7,8})cobble[i]=true;
        eq(true,LegacySmelterArcaneRecipe.matches(brass,crucible,furnace,cobble));
        for(int i=0;i<9;i++){
            boolean[] target=i==0||i==2?brass:i==1?crucible:i==4?furnace:cobble;
            target[i]=false;
            eq(false,LegacySmelterArcaneRecipe.matches(brass,crucible,furnace,cobble));
            target[i]=true;
        }
        eq(false,LegacySmelterArcaneRecipe.matches(null,crucible,furnace,cobble));
        eq(false,LegacySmelterArcaneRecipe.matches(new boolean[0],crucible,furnace,cobble));
        eq(50,LegacySmelterArcaneRecipe.discountedVis(0));
        eq(37,LegacySmelterArcaneRecipe.discountedVis(.25f));
        eq(0,LegacySmelterArcaneRecipe.discountedVis(1.0f));
        System.out.println("BETA26 basic smelter arcane crafting: OK ("+n+" assertions)");
    }
}
