package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;

public final class LegacyAlchemyFilterArcaneRecipeTest {
    private static int checks;
    private static void eq(Object want, Object got) {
        checks++;
        if (!java.util.Objects.equals(want, got))
            throw new AssertionError("wanted="+want+" got="+got);
    }
    private static void slots(int[] want, int[] got) {
        checks++;
        if (!Arrays.equals(want,got))
            throw new AssertionError(Arrays.toString(want)+" != "+Arrays.toString(got));
    }
    public static void main(String[] args) {
        eq("BASEALCHEMY",LegacyAlchemyFilterArcaneRecipe.RESEARCH);
        eq(15,LegacyAlchemyFilterArcaneRecipe.VIS);
        eq(2,LegacyAlchemyFilterArcaneRecipe.OUTPUT);
        eq(11,LegacyAlchemyFilterArcaneRecipe.AQUA_SLOT);
        eq(1<<2,LegacyAlchemyFilterArcaneRecipe.CRYSTAL_MASK);
        for (int row=0;row<3;row++) {
            int base=row*3;
            boolean[] gold=new boolean[9], wood=new boolean[9], filled=new boolean[9];
            gold[base]=gold[base+2]=true;
            wood[base+1]=true;
            for(int slot=base;slot<base+3;slot++)filled[slot]=true;
            slots(new int[]{base,base+1,base+2},
                    LegacyAlchemyFilterArcaneRecipe.inputSlots(gold,wood,filled));
            filled[(base+3)%9]=true;
            eq(null,LegacyAlchemyFilterArcaneRecipe.inputSlots(gold,wood,filled));
            filled[(base+3)%9]=false;
            gold[base]=false;
            eq(null,LegacyAlchemyFilterArcaneRecipe.inputSlots(gold,wood,filled));
            gold[base]=true;
            wood[base+1]=false;
            eq(null,LegacyAlchemyFilterArcaneRecipe.inputSlots(gold,wood,filled));
        }
        eq(null,LegacyAlchemyFilterArcaneRecipe.inputSlots(null,new boolean[9],new boolean[9]));
        eq(null,LegacyAlchemyFilterArcaneRecipe.inputSlots(new boolean[8],new boolean[9],new boolean[9]));
        eq(15,LegacyAlchemyFilterArcaneRecipe.discountedVis(0F));
        eq(11,LegacyAlchemyFilterArcaneRecipe.discountedVis(0.25F));
        eq(0,LegacyAlchemyFilterArcaneRecipe.discountedVis(1F));
        System.out.println("BETA26 alchemy filter arcane crafting: OK ("+checks+" assertions)");
    }
}
