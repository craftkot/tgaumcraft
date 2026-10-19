package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Set;

/** Confirmed vanilla crop families of BETA26 CropUtils/ModConfig/SealHarvest. */
public final class GolemFarmRules {
    public static final int SCAN_TICKS=5;
    public static final int SEED_COST=1;
    public enum Readiness { AGE, FRUIT, STACKED }
    public enum Crop {
        WHEAT("wheat","wheat_seeds",7,true), CARROTS("carrots","carrot",7,true),
        POTATOES("potatoes","potato",7,true), BEETROOTS("beetroots","beetroot_seeds",3,true),
        NETHER_WART("nether_wart","nether_wart",3,false),
        MELON("melon",Readiness.FRUIT), PUMPKIN("pumpkin",Readiness.FRUIT),
        SUGAR_CANE("sugar_cane",Readiness.STACKED), CACTUS("cactus",Readiness.STACKED);
        public final String block,seed;
        public final Readiness readiness;
        public final int maximumAge;
        public final boolean farmland;
        Crop(String block,String seed,int maximumAge,boolean farmland) {
            this.block=block; this.seed=seed; this.maximumAge=maximumAge; this.farmland=farmland; this.readiness=Readiness.AGE;
        }
        Crop(String block,Readiness readiness) {
            this.block=block; this.seed=null; this.maximumAge=-1; this.farmland=false; this.readiness=readiness;
        }
    }
    private GolemFarmRules() {}
    public static boolean eligible(Set<GolemTrait> traits) {
        return traits.contains(GolemTrait.SMART) && traits.contains(GolemTrait.DEFT);
    }
    public static boolean mature(Crop crop,int age) { return crop!=null && crop.readiness==Readiness.AGE && age==crop.maximumAge; }
    public static boolean ready(Crop crop,int age,boolean sameBlockBelow) {
        return crop!=null && switch(crop.readiness) {
            case AGE -> mature(crop,age);
            case FRUIT -> true;
            case STACKED -> sameBlockBelow;
        };
    }
}
