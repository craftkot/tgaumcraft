package dev.ehtid.thaumcraft.port.core;

/**
 * Thaumcraft 6.1.BETA26 AdvAlchemyConstruct arcane crafting.
 * Research ESSENTIASMELTERVOID@1, 200 Vis, Terra + Ignis crystal.
 * Shaped " A " / "VPV" / " A ":
 * A = ordinary alchemical construct; V = void metal plate;
 * P = primordial pearl, returned with one extra point of damage,
 * until damage 7 when the pearl is finally consumed.
 */
public final class LegacyAdvancedAlchemyConstructArcaneRecipe {
    private LegacyAdvancedAlchemyConstructArcaneRecipe() {}
    public static final String RESEARCH="ESSENTIASMELTERVOID@1";
    public static final int VIS=200;
    public static final int IGNIS_SLOT=10;
    public static final int TERRA_SLOT=12;
    public static final int CRYSTAL_MASK=(1<<1)|(1<<3);
    private static final String[] PATTERN={" A ","VPV"," A "};

    public static boolean matches(boolean[] construct, boolean[] voidPlate,
                                  boolean[] pearl, boolean[] occupied) {
        if(construct==null||voidPlate==null||pearl==null||occupied==null
                ||construct.length!=9||voidPlate.length!=9
                ||pearl.length!=9||occupied.length!=9)return false;
        for(int slot=0;slot<9;slot++) {
            char expected=PATTERN[slot/3].charAt(slot%3);
            boolean correct=switch(expected) {
                case 'A' -> construct[slot];
                case 'V' -> voidPlate[slot];
                case 'P' -> pearl[slot];
                default -> !occupied[slot];
            };
            if(!correct)return false;
        }
        return true;
    }

    /** Original ItemPrimordialPearl.hasContainerItem: 0..6 -> next stage, 7 -> consumed. */
    public static int pearlRemainderDamage(int currentDamage) {
        if(currentDamage<0||currentDamage>7)
            throw new IllegalArgumentException("BETA26 pearl damage must be 0..7: "+currentDamage);
        return currentDamage<7?currentDamage+1:-1;
    }

    public static int discountedVis(float discount) {
        return (int)(VIS*(1.0F-discount));
    }
}
