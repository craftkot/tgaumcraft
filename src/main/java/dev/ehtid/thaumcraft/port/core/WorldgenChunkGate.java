package dev.ehtid.thaumcraft.port.core;

/**
 * Loader-neutral safety gate for M12 world generation. This does not decide
 * which Thaumcraft feature spawns, or its BETA26 frequency/biome rules.
 *
 * <p>Callers must supply facts from the actual world-generation context;
 * this class never queries or force-loads neighboring chunks.
 */
public final class WorldgenChunkGate {
    private WorldgenChunkGate() {}

    /**
     * @param generatingNewChunk true only during generation of a new chunk;
     *                           never set for ordinary chunk load/reload
     * @param allowedDimension true when the current dimension is permitted by
     *                         the feature's independently verified rules
     * @param currentChunkAvailable true only when the current chunk is already
     *                              available without a force-load
     * @param positionInsideCurrentChunk true if the proposed origin is in the
     *                                   chunk being generated
     * @return whether placement may be attempted; not a guarantee of success
     */
    public static boolean mayAttemptPlacement(
            boolean generatingNewChunk,
            boolean allowedDimension,
            boolean currentChunkAvailable,
            boolean positionInsideCurrentChunk) {
        return generatingNewChunk
                && allowedDimension
                && currentChunkAvailable
                && positionInsideCurrentChunk;
    }

    /** Minecraft block coordinate to chunk coordinate, including negatives. */
    public static int chunkCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, 16);
    }

    /** Prevent accidental attempts from an origin outside the active chunk. */
    public static boolean originInChunk(int blockX, int blockZ, int chunkX, int chunkZ) {
        return chunkCoordinate(blockX) == chunkX
                && chunkCoordinate(blockZ) == chunkZ;
    }
}
