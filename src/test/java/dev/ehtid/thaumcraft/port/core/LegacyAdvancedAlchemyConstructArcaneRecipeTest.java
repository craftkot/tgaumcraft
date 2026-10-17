package dev.ehtid.thaumcraft.port.core;

public final class LegacyAdvancedAlchemyConstructArcaneRecipeTest {
    private static int assertions;
    private static void eq(Object a,Object b) {
        assertions++;
        if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        eq("ESSENTIASMELTERVOID@1",LegacyAdvancedAlchemyConstructArcaneRecipe.RESEARCH);
        eq(200,LegacyAdvancedAlchemyConstructArcaneRecipe.VIS);
        eq(10,LegacyAdvancedAlchemyConstructArcaneRecipe.IGNIS_SLOT);
        eq(12,LegacyAdvancedAlchemyConstructArcaneRecipe.TERRA_SLOT);
        eq(0b001010,LegacyAdvancedAlchemyConstructArcaneRecipe.CRYSTAL_MASK);
        boolean[] a=new boolean[9],v=new boolean[9],p=new boolean[9],o=new boolean[9];
        a[1]=a[7]=true;
        v[3]=v[5]=true;
        p[4]=true;
        for(int s:new int[]{1,3,4,5,7})o[s]=true;
        eq(true,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(a,v,p,o));
        for(int i:new int[]{1,7}){a[i]=false;eq(false,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(a,v,p,o));a[i]=true;}
        for(int i:new int[]{3,5}){v[i]=false;eq(false,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(a,v,p,o));v[i]=true;}
        p[4]=false;eq(false,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(a,v,p,o));p[4]=true;
        for(int i:new int[]{0,2,6,8}){o[i]=true;eq(false,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(a,v,p,o));o[i]=false;}
        eq(false,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(null,v,p,o));
        eq(false,LegacyAdvancedAlchemyConstructArcaneRecipe.matches(new boolean[8],v,p,o));
        for(int damage=0;damage<7;damage++)eq(damage+1,LegacyAdvancedAlchemyConstructArcaneRecipe.pearlRemainderDamage(damage));
        eq(-1,LegacyAdvancedAlchemyConstructArcaneRecipe.pearlRemainderDamage(7));
        for(int damage:new int[]{-1,8,100}) {
            boolean thrown=false;
            try{LegacyAdvancedAlchemyConstructArcaneRecipe.pearlRemainderDamage(damage);}
            catch(IllegalArgumentException expected){thrown=true;}
            eq(true,thrown);
        }
        eq(200,LegacyAdvancedAlchemyConstructArcaneRecipe.discountedVis(0F));
        eq(150,LegacyAdvancedAlchemyConstructArcaneRecipe.discountedVis(0.25F));
        eq(0,LegacyAdvancedAlchemyConstructArcaneRecipe.discountedVis(1F));
        System.out.println("BETA26 advanced alchemical construct arcane crafting: OK ("+assertions+" checks)");
    }
}
