package dev.ehtid.thaumcraft.port.core;

/**
 * Original BETA26 ConfigRecipes EssentiaSmelter:
 * ESSENTIASMELTER@2, 50 vis, Ignis x1, BCB / SFS / SSS.
 * B = plateBrass, C = Thaumcraft crucible, F = vanilla furnace,
 * S = cobblestone (ore-dictionary equivalents).
 */
public final class LegacySmelterArcaneRecipe {
    private LegacySmelterArcaneRecipe() {}
    public static final String RESEARCH="ESSENTIASMELTER@2";
    public static final int BASE_VIS=50;
    public static final int IGNIS_SLOT=10;
    public static final int CRYSTAL_MASK=1<<1;

    public static boolean matches(boolean[] brass,boolean[] crucible,
                                  boolean[] furnace,boolean[] cobblestone) {
        if(brass==null||crucible==null||furnace==null||cobblestone==null
                ||brass.length!=9||crucible.length!=9||furnace.length!=9
                ||cobblestone.length!=9)return false;
        for(int i=0;i<9;i++) {
            if(i==0||i==2){if(!brass[i])return false;}
            else if(i==1){if(!crucible[i])return false;}
            else if(i==4){if(!furnace[i])return false;}
            else if(!cobblestone[i])return false;
        }
        return true;
    }
    public static int discountedVis(float discount) {
        return (int)(BASE_VIS*(1.0f-discount));
    }
}
