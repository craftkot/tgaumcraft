package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeUpgradeRecipes.Upgrade;

public final class LegacyEssentiaTubeUpgradeRecipesTest {
    private static int checks;
    private static void eq(Object want, Object actual) {
        checks++;
        if (!java.util.Objects.equals(want,actual))
            throw new AssertionError(want+" != "+actual);
    }
    private static void slots(int[] want,int[] actual) {
        checks++;
        if (!Arrays.equals(want,actual))
            throw new AssertionError(Arrays.toString(want)+" != "+Arrays.toString(actual));
    }
    public static void main(String[] args) {
        eq("TUBES",LegacyEssentiaTubeUpgradeRecipes.RESEARCH);
        eq(10,LegacyEssentiaTubeUpgradeRecipes.VIS);
        eq(12,Upgrade.RESTRICT.crystalSlot);
        eq(11,Upgrade.ONEWAY.crystalSlot);
        eq(1<<3,Upgrade.RESTRICT.crystalMask);
        eq(1<<2,Upgrade.ONEWAY.crystalMask);
        boolean[] t=new boolean[9],l=new boolean[9],o=new boolean[9];
        t[7]=true;o[7]=true;
        slots(new int[]{7,-1},LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.RESTRICT,t,l,o));
        slots(new int[]{7,-1},LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.ONEWAY,t,l,o));
        eq(null,LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.VALVE,t,l,o));
        l[2]=true;o[2]=true;
        slots(new int[]{7,2},LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.VALVE,t,l,o));
        eq(null,LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.RESTRICT,t,l,o));
        t[0]=true;o[0]=true;
        eq(null,LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.VALVE,t,l,o));
        t[0]=false;o[0]=false;
        eq(7,LegacyEssentiaTubeUpgradeRecipes.discountedVis(0.25F));
        eq(0,LegacyEssentiaTubeUpgradeRecipes.discountedVis(1F));
        eq(null,LegacyEssentiaTubeUpgradeRecipes.match(Upgrade.ONEWAY,new boolean[8],l,o));
        System.out.println("BETA26 tube upgrades: OK ("+checks+" assertions)");
    }
}
