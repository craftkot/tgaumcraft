package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 Silverwood's maximum possible block-write envelope, before any world access.
 * This is a conservative preflight, NOT the tree generator or a biome placement hook.
 *
 * Archived WorldGenSilverwoodTrees: height=7+nextInt(4); foliage begins at
 * height-5 and ends at height+3+nextInt(3), with x/z loops [-5,5].
 * Root blocks can reach y-1. A caller must still test soil and replaceability.
 */
public final class SilverwoodLegacyFootprint {
    private SilverwoodLegacyFootprint() {}

    public static boolean validHeight(int height) {
        return height >= 7 && height <= 10;
    }

    /** Build-height upper bound is exclusive, as in modern Minecraft. */
    public static boolean withinBuildHeight(int originY, int height, int minY, int maxYExclusive) {
        if (!validHeight(height) || minY >= maxYExclusive) return false;
        long low = (long) originY - 1;
        long high = (long) originY + height + 5;
        return low >= minY && high < maxYExclusive;
    }

    /**
     * Can every possible Silverwood write stay inside the currently generated chunk?
     * Rejecting a candidate is safer than loading or writing an adjacent chunk.
     * The 11x11 canopy envelope is intentionally wider than the actual round canopy.
     */
    public static boolean withinChunk(int originX, int originZ, int chunkX, int chunkZ) {
        long minX = (long) chunkX * 16;
        long minZ = (long) chunkZ * 16;
        return (long) originX - 5 >= minX && (long) originX + 5 < minX + 16
                && (long) originZ - 5 >= minZ && (long) originZ + 5 < minZ + 16;
    }
}
