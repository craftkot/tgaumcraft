package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Enumerates a 2x2 Greatwood trunk volume without world mutations.
 * Caller supplies the source-derived trunk length; heightLimit is NOT
 * assumed to equal the actual trunk length in the archived generator.
 */
public final class GreatwoodTrunkVolume {
    private GreatwoodTrunkVolume() {}

    @FunctionalInterface
    public interface BlockVisitor {
        void accept(int x, int y, int z);
    }

    /**
     * Preflights every coordinate before invoking the visitor.
     * A throwing visitor is not rolled back; callers must stage writes.
     */
    public static boolean visit(int x, int baseY, int z, int trunkLength,
                                int minBuildY, int maxBuildYExclusive,
                                BlockVisitor visitor) {
        Objects.requireNonNull(visitor, "visitor");
        if (trunkLength <= 0 || minBuildY >= maxBuildYExclusive) return false;
        if (x == Integer.MAX_VALUE || z == Integer.MAX_VALUE) return false;
        long topExclusive = (long) baseY + trunkLength;
        if (baseY < minBuildY || topExclusive > maxBuildYExclusive) return false;
        if (topExclusive > Integer.MAX_VALUE) return false;
        for (int y = baseY; (long) y < topExclusive; y++) {
            visitor.accept(x, y, z);
            visitor.accept(x + 1, y, z);
            visitor.accept(x, y, z + 1);
            visitor.accept(x + 1, y, z + 1);
        }
        return true;
    }
}
