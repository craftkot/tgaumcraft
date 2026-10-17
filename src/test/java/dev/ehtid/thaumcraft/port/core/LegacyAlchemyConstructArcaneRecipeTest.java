package dev.ehtid.thaumcraft.port.core;

public final class LegacyAlchemyConstructArcaneRecipeTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if(!condition)throw new AssertionError("BETA26 AlchemicalConstruct");
    }
    public static void main(String[] args) {
        check("TUBES".equals(LegacyAlchemyConstructArcaneRecipe.RESEARCH));
        check(LegacyAlchemyConstructArcaneRecipe.VIS==75);
        check(LegacyAlchemyConstructArcaneRecipe.OUTPUT==2);
        check(LegacyAlchemyConstructArcaneRecipe.CRYSTAL_MASK==0b110100);
        boolean[] iron=new boolean[9],valve=new boolean[9],tube=new boolean[9],wood=new boolean[9];
        for(int x:new int[]{0,2,6,8})iron[x]=true;
        valve[1]=valve[7]=true;
        tube[3]=tube[5]=true;
        wood[4]=true;
        check(LegacyAlchemyConstructArcaneRecipe.matches(iron,valve,tube,wood));
        for(int x:new int[]{0,2,6,8}) {
            iron[x]=false;
            check(!LegacyAlchemyConstructArcaneRecipe.matches(iron,valve,tube,wood));
            iron[x]=true;
        }
        for(int x:new int[]{1,7}) {
            valve[x]=false;
            check(!LegacyAlchemyConstructArcaneRecipe.matches(iron,valve,tube,wood));
            valve[x]=true;
        }
        for(int x:new int[]{3,5}) {
            tube[x]=false;
            check(!LegacyAlchemyConstructArcaneRecipe.matches(iron,valve,tube,wood));
            tube[x]=true;
        }
        wood[4]=false;
        check(!LegacyAlchemyConstructArcaneRecipe.matches(iron,valve,tube,wood));
        wood[4]=true;
        check(!LegacyAlchemyConstructArcaneRecipe.matches(null,valve,tube,wood));
        check(!LegacyAlchemyConstructArcaneRecipe.matches(new boolean[0],valve,tube,wood));
        check(LegacyAlchemyConstructArcaneRecipe.discountedVis(0)==75);
        check(LegacyAlchemyConstructArcaneRecipe.discountedVis(0.25F)==56);
        check(LegacyAlchemyConstructArcaneRecipe.discountedVis(1F)==0);
        System.out.println("BETA26 alchemical construct arcane crafting: OK ("+checks+" checks)");
    }
}
