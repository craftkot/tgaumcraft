package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 TileCrucible.update() scalar heat slice, verified against its
 * original 1.12.2 bytecode. Its water tank holds 1000 mB, heat ranges
 * from 0 to 200, and items can be smelted only once heat > 150.
 * Heat rises by one per server tick with water and a valid heater below;
 * otherwise it falls by one. The actual alchemy/essentia smelting loop
 * is separate, not faked by these lifecycle primitives.
 */
public final class LegacyCrucibleHeat {
    public static final int CAPACITY_MB = 1000;
    public static final int MAX_HEAT = 200;
    public static final int BOILING_THRESHOLD = 150;

    private LegacyCrucibleHeat() {}

    public static int next(int current, int waterMb, boolean heaterBelow) {
        int heat = Math.max(0, Math.min(MAX_HEAT, current));
        if (waterMb > 0 && heaterBelow) return Math.min(MAX_HEAT, heat + 1);
        return Math.max(0, heat - 1);
    }

    public static boolean boiling(int heat, int waterMb) {
        return waterMb > 0 && heat > BOILING_THRESHOLD;
    }
}
