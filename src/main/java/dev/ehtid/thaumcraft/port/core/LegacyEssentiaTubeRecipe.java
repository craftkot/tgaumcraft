package dev.ehtid.thaumcraft.port.core;

/**
 * Authentic ConfigRecipes 6.1.BETA26 "Tube":
 * TUBES research, 10 vis, NO crystals, eight tube output.
 * Source shaped arcane recipe: " Q "/"IGI"/" B "
 * Q=nuggetQuicksilver, I=plateIron, G=blockGlass, B=nuggetBrass.
 */
public final class LegacyEssentiaTubeRecipe {
    private LegacyEssentiaTubeRecipe() {}
    public static final String RESEARCH="TUBES";
    public static final int BASE_VIS=10;
    public static final int OUTPUT=8;
    public static final int QUICKSILVER_NUGGET_META=5;
    public static final int BRASS_NUGGET_META=8;
    public static final int IRON_PLATE_META=1;

    public static boolean matches(boolean[] quicksilver,boolean[] ironPlate,
                                  boolean[] glassBlock,boolean[] brass,boolean[] empty) {
        if(quicksilver==null||ironPlate==null||glassBlock==null||brass==null
                ||empty==null||quicksilver.length!=9||ironPlate.length!=9
                ||glassBlock.length!=9||brass.length!=9||empty.length!=9)
            return false;
        for(int i=0;i<9;i++) {
            if(i==1) {if(!quicksilver[i])return false;}
            else if(i==3||i==5) {if(!ironPlate[i])return false;}
            else if(i==4) {if(!glassBlock[i])return false;}
            else if(i==7) {if(!brass[i])return false;}
            else if(!empty[i])return false;
        }
        return true;
    }

    public static int discountedVis(float discount) {
        return (int)(BASE_VIS*(1.0F-discount));
    }
}
