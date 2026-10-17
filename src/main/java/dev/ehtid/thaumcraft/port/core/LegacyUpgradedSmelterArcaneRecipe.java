package dev.ehtid.thaumcraft.port.core;

/**
 * Original BETA26 EssentiaSmelterThaumium and EssentiaSmelterVoid
 * recipes: BFB / IGI / III. B=brass plate, F=basic smelter,
 * I=matching thaumium/void plate, G=matching construct block.
 */
public final class LegacyUpgradedSmelterArcaneRecipe {
    private LegacyUpgradedSmelterArcaneRecipe() {}
    public static final int IGNIS_SLOT=10;
    public static final int CRYSTAL_MASK=1<<1;

    public enum Kind {
        THAUMIUM("ESSENTIASMELTERTHAUMIUM",250,2),
        VOID("ESSENTIASMELTERVOID",750,3);

        public final String research;
        public final int vis, ignisCrystals;

        Kind(String research,int vis,int ignisCrystals) {
            this.research=research;
            this.vis=vis;
            this.ignisCrystals=ignisCrystals;
        }
    }

    public static boolean matches(boolean[] brass,boolean[] basicSmelter,
                                  boolean[] plate,boolean[] construct) {
        if(brass==null||basicSmelter==null||plate==null||construct==null
                ||brass.length!=9||basicSmelter.length!=9
                ||plate.length!=9||construct.length!=9)return false;
        for(int slot=0;slot<9;slot++) {
            boolean matches;
            if(slot==0||slot==2)matches=brass[slot];
            else if(slot==1)matches=basicSmelter[slot];
            else if(slot==4)matches=construct[slot];
            else matches=plate[slot];
            if(!matches)return false;
        }
        return true;
    }

    public static int discountedVis(Kind kind,float discount) {
        return (int)(kind.vis*(1.0F-discount));
    }
}
