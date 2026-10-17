package dev.ehtid.thaumcraft.port.core;

/**
 * Source BETA26 ConfigRecipes AlchemicalConstruct: 2 outputs,
 * research TUBES, 75 vis, Aqua + Ordo + Perditio one each.
 * IVI / TWT / IVI, I=iron plate, V=valve tube,
 * T=regular essentia tube, W=greatwood planks.
 */
public final class LegacyAlchemyConstructArcaneRecipe {
    private LegacyAlchemyConstructArcaneRecipe() {}
    public static final String RESEARCH="TUBES";
    public static final int VIS=75, OUTPUT=2;
    public static final int AQUA_SLOT=11, ORDO_SLOT=13, PERDITIO_SLOT=14;
    public static final int CRYSTAL_MASK=(1<<2)|(1<<4)|(1<<5);
    private static final String[] PATTERN={"IVI","TWT","IVI"};

    public static boolean matches(boolean[] iron,boolean[] valve,
                                  boolean[] tube,boolean[] wood) {
        if(iron==null||valve==null||tube==null||wood==null
                ||iron.length!=9||valve.length!=9||tube.length!=9||wood.length!=9)
            return false;
        for(int slot=0;slot<9;slot++) {
            char needed=PATTERN[slot/3].charAt(slot%3);
            boolean valid=switch(needed) {
                case 'I' -> iron[slot];
                case 'V' -> valve[slot];
                case 'T' -> tube[slot];
                case 'W' -> wood[slot];
                default -> false;
            };
            if(!valid)return false;
        }
        return true;
    }

    public static int discountedVis(float discount) {
        return (int)(VIS*(1.0F-discount));
    }
}
