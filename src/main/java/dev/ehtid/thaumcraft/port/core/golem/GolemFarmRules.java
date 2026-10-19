package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Set;

/** First confirmed vanilla crop family of BETA26 CropUtils/ModConfig/SealHarvest. */
public final class GolemFarmRules {
    public static final int SCAN_TICKS=5;
    public static final int SEED_COST=1;
    public enum Crop {
        WHEAT("wheat","wheat_seeds",7,true), CARROTS("carrots","carrot",7,true),
        POTATOES("potatoes","potato",7,true), BEETROOTS("beetroots","beetroot_seeds",3,true),
        NETHER_WART("nether_wart","nether_wart",3,false);
        public final String block,seed;
        public final int maximumAge;
        public final boolean farmland;
        Crop(String block,String seed,int maximumAge,boolean farmland) {
            this.block=block; this.seed=seed; this.maximumAge=maximumAge; this.farmland=farmland;
        }
    }
    private GolemFarmRules() {}
    public static boolean eligible(Set<GolemTrait> traits) {
        return traits.contains(GolemTrait.SMART) && traits.contains(GolemTrait.DEFT);
    }
    public static boolean mature(Crop crop,int age) { return crop!=null && age==crop.maximumAge; }
}
