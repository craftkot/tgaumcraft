package dev.ehtid.thaumcraft.port.core;

/**
 * Loader-independent validation of the vertical origin of a worldgen feature.
 * The caller supplies the actual dimension build limits; no chunk is queried
 * or loaded here. The upper build limit is exclusive, as in Minecraft.
 *
 * <p>This only validates the origin. Tree canopy/root extent and original
 * BETA26 biome/frequency rules must be checked by the concrete feature.
 */
public final class WorldgenHeightGate {
    private WorldgenHeightGate() {}

    /**
     * @param originY proposed feature origin
     * @param minBuildY inclusive dimension lower build limit
     * @param maxBuildYExclusive exclusive dimension upper build limit
     */
    public static boolean originWithinBuildHeight(
            int originY, int minBuildY, int maxBuildYExclusive) {
        return minBuildY < maxBuildY
                && originY >= minBuildY
                && originY < maxBuildYExclusive;
    }

    /**
     * Validates a feature's entire vertical span without integer overflow.
     * Offsets are relative to originY, inclusive at both ends.
     */
    public static boolean spanWithinBuildHeight(
            int originY, int minOffsetY, int maxOffsetY,
            int minBuildY, int maxBuildYExclusive) {
        if (minBuildY >= maxBuildYExclusive || minOffsetY > maxOffsetY) {
            return false;
        }
        long bottom = (long) originY + minOffsetY;
        long top = (long) originY + maxOffsetY;
        return bottom >= minBuildY && top < maxBuildYExclusive;
    }
}
