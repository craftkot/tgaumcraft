package dev.ehtid.thaumcraft.port.core;

/** Exact original BETA26 idle structural revalidation cadence. */
public final class LegacyInfusionMatrixIdle {
    private LegacyInfusionMatrixIdle() {}
    public static int structureCheckInterval(boolean crafting) {
        return crafting ? 20 : 100;
    }
    public static boolean checkNow(int ticks, boolean crafting) {
        return ticks > 0 && ticks % structureCheckInterval(crafting) == 0;
    }
    public static boolean deactivate(boolean active, boolean validLocation) {
        return active && !validLocation;
    }
}
