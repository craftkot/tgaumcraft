package dev.ehtid.thaumcraft.port.core;

/**
 * M12 preflight for a proposed feature origin and its vertical footprint.
 * This is a pure, fail-closed adapter boundary, not a BETA26 spawn rule.
 * The caller must separately validate original biome, chance and terrain
 * rules and must never load neighboring chunks to construct these arguments.
 */
public final class WorldgenPlacementGate {
    private WorldgenPlacementGate() {}

    /**
     * Check all local placement bounds before attempting any world mutation.
     * The full vertical footprint includes both inclusive relative offsets.
     */
    public static boolean mayAttempt(
            boolean generatingNewChunk,
            boolean allowedDimension,
            boolean currentChunkAvailable,
            int originBlockX,
            int originY,
            int originBlockZ,
            int generatingChunkX,
            int generatingChunkZ,
            int minOffsetY,
            int maxOffsetY,
            int minBuildY,
            int maxBuildYExclusive) {
        return WorldgenChunkGate.mayAttemptPlacementAt(
                    generatingNewChunk, allowedDimension, currentChunkAvailable,
                    originBlockX, originBlockZ, generatingChunkX, generatingChunkZ)
                && WorldgenHeightGate.spanWithinBuildHeight(
                    originY, minOffsetY, maxOffsetY, minBuildY, maxBuildYExclusive);
    }
}
