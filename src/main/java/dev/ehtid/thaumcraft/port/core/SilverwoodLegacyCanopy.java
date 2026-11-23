package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Random;

/**
 * Archived Thaumcraft 6 Silverwood canopy write algorithm.
 * Reads replacement eligibility only after the original distance RNG roll.
 * The writer must apply the same air/leaves/replaceable check on actual writes.
 * No NeoForge registration or block state mapping is performed here.
 */
public final class SilverwoodLegacyCanopy {
    private SilverwoodLegacyCanopy() {}

    @FunctionalInterface
    public interface LeafWriter {
        void place(int x, int y, int z);
    }

    @FunctionalInterface
    public interface LeafReplaceability {
        boolean canReplace(int x, int y, int z);
    }

    public static void place(int x, int y, int z, int height, Random random,
                             LeafReplaceability replaceability, LeafWriter writer) {
        if (!SilverwoodLegacyFootprint.validHeight(height)) {
            throw new IllegalArgumentException("Invalid Silverwood height");
        }
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(replaceability, "replaceability");
        Objects.requireNonNull(writer, "writer");

        // Original for-loop initializer evaluates the top offset once.
        int start = y + height - 5;
        int end = y + height + 3 + random.nextInt(3);
        for (int layer = start; layer <= end; layer++) {
            int centerY = Math.max(y + height - 3, Math.min(layer, y + height));
            for (int xx = x - 5; xx <= x + 5; xx++) {
                for (int zz = z - 5; zz <= z + 5; zz++) {
                    double dx = (double) xx - x;
                    double dy = (double) layer - centerY;
                    double dz = (double) zz - z;
                    double distanceSquared = dx * dx + dy * dy + dz * dz;
                    // Legacy evaluates nextInt(8) for EVERY candidate, even
                    // those outside the radius and those that cannot be replaced.
                    if (distanceSquared < 10 + random.nextInt(8)
                            && replaceability.canReplace(xx, layer, zz)) {
                        writer.place(xx, layer, zz);
                    }
                }
            }
        }
    }
}
