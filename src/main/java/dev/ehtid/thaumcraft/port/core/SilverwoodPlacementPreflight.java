package dev.ehtid.thaumcraft.port.core;

/**
 * Read-only Silverwood placement preflight. The future NeoForge Feature supplies
 * actual world predicates; this class neither mutates the world nor registers a feature.
 * The checked trunk column is a minimum clearance requirement, not an exact
 * reproduction of the BETA26 canopy collision scan.
 */
public final class SilverwoodPlacementPreflight {
    private SilverwoodPlacementPreflight() {}

    @FunctionalInterface
    public interface BlockPredicate {
        boolean test(int x, int y, int z);
    }

    /**
     * Checks the entire conservative write envelope, soil below the trunk and
     * vertical trunk replaceability before any writes. This avoids partial trees
     * caused by an obstructed trunk or unloaded adjacent chunk.
     */
    public static boolean mayPlace(
            int x, int y, int z, int height,
            int chunkX, int chunkZ, int minY, int maxYExclusive,
            BlockPredicate validSoil, BlockPredicate replaceableTrunk) {
        if (validSoil == null || replaceableTrunk == null
                || !SilverwoodLegacyFootprint.withinBuildHeight(y, height, minY, maxYExclusive)
                || !SilverwoodLegacyFootprint.withinChunk(x, z, chunkX, chunkZ)) {
            return false;
        }
        if (!validSoil.test(x, y - 1, z)) return false;
        for (int dy = 0; dy < height; dy++) {
            if (!replaceableTrunk.test(x, y + dy, z)) return false;
        }
        return true;
    }
}
