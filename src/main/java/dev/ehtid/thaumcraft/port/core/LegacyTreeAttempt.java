package dev.ehtid.thaumcraft.port.core;

import java.util.random.RandomGenerator;

/**
 * M12 BETA26 vegetation attempt decisions, separate from block placement.
 * The caller owns the original per-chunk RNG and must preserve its call order.
 * These are attempts, never a promise that a tree can grow.
 */
public final class LegacyTreeAttempt {
    private LegacyTreeAttempt() {}

    /** BETA26: random.nextInt(80) == 3. */
    public static boolean silverwoodRoll(RandomGenerator random) {
        return random.nextInt(80) == 3;
    }

    /** BETA26: random.nextInt(25) == 7. */
    public static boolean greatwoodRoll(RandomGenerator random) {
        return random.nextInt(25) == 7;
    }

    /** BETA26: chunk*16+8+random.nextInt(9)-4, with safe int arithmetic. */
    public static int centeredCoordinate(int chunk, RandomGenerator random) {
        int offset = random.nextInt(9) - 4;
        long block = (long) chunk * 16 + 8 + offset;
        if (block < Integer.MIN_VALUE || block > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Chunk origin exceeds block coordinate range");
        }
        return (int) block;
    }

    /**
     * Greatwood: biome support must exceed a fresh nextFloat.
     * Caller must supply source-mapped support (not a guessed modern tag).
     */
    public static boolean greatwoodBiome(float support, RandomGenerator random) {
        return support > random.nextFloat();
    }

    /**
     * Silverwood: support/2 > roll OR magical except Magical Forest OR
     * legacy biome id 18/28. Keep source-era predicates explicit.
     * Java's short-circuit behavior preserves the original RNG call count.
     */
    public static boolean silverwoodBiome(float greatwoodSupport, boolean magical,
            boolean magicalForest, int legacyBiomeId, RandomGenerator random) {
        return greatwoodSupport / 2.0F > random.nextFloat()
                || (magical && !magicalForest)
                || legacyBiomeId == 18 || legacyBiomeId == 28;
    }

    /** Conditional 1/8 spider variant, called only after Greatwood biome acceptance. */
    public static boolean spiderGreatwood(RandomGenerator random) {
        return random.nextInt(8) == 0;
    }
}
