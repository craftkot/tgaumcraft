package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Source-era Greatwood height initialization, before any leaf-node draws.
 * Unlike Silverwood, the archived Greatwood generator consumes one nextLong()
 * from the chunk RNG and seeds its own java.util.Random.
 * This is not a full tree generator or a NeoForge registration.
 */
public final class GreatwoodLegacyHeight {
    public static final int HEIGHT_LIMIT = 11;

    private GreatwoodLegacyHeight() {}

    public static SeededHeight begin(RandomGenerator chunkRandom) {
        Objects.requireNonNull(chunkRandom, "chunkRandom");
        long seed = chunkRandom.nextLong();
        Random treeRandom = new Random(seed);
        int heightLimit = HEIGHT_LIMIT + treeRandom.nextInt(HEIGHT_LIMIT);
        return new SeededHeight(heightLimit, treeRandom);
    }

    /** Tree RNG must be reused for subsequent leaf-node generation. */
    public record SeededHeight(int heightLimit, Random treeRandom) {
        public SeededHeight {
            if (heightLimit < HEIGHT_LIMIT || heightLimit >= HEIGHT_LIMIT * 2) {
                throw new IllegalArgumentException("Greatwood height limit out of range");
            }
            Objects.requireNonNull(treeRandom, "treeRandom");
        }
    }
}
