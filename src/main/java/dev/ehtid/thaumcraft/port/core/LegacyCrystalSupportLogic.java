package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 crystals are removed when no rock support remains. The modern port
 * must distinguish a confirmed unsupported position from an unavailable
 * neighbour chunk, because the latter cannot safely be read without loading it.
 */
public final class LegacyCrystalSupportLogic {
    private LegacyCrystalSupportLogic() {}

    /**
     * Returns true only when all six support-neighbour chunks are known loaded
     * and no loaded support face exists.
     */
    public static boolean shouldRemoveUnsupportedCrystal(
            boolean hasLoadedSupport, boolean allSupportChunksLoaded) {
        return allSupportChunksLoaded && !hasLoadedSupport;
    }
}
