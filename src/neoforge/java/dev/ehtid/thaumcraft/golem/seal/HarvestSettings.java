package dev.ehtid.thaumcraft.golem.seal;

/** Source SealHarvest defaults: prep=true, ppro=false. */
public record HarvestSettings(boolean replant,boolean provision) {
    public static HarvestSettings defaults() { return new HarvestSettings(true,false); }
}
