package dev.ehtid.thaumcraft.port.core;

import java.util.Random;
import java.util.random.RandomGenerator;

public final class GreatwoodLegacyHeightTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (long seed = -64; seed <= 64; seed++) {
            Random chunk = new Random(seed);
            Random controlChunk = new Random(seed);
            long internalSeed = controlChunk.nextLong();
            Random expectedTree = new Random(internalSeed);
            int expectedHeight = 11 + expectedTree.nextInt(11);

            GreatwoodLegacyHeight.SeededHeight actual = GreatwoodLegacyHeight.begin(chunk);
            check(actual.heightLimit() == expectedHeight, "height seed=" + seed);
            check(actual.heightLimit() >= 11 && actual.heightLimit() <= 21, "range");
            check(actual.treeRandom().nextFloat() == expectedTree.nextFloat(), "internal RNG continuation");
            check(chunk.nextLong() == controlChunk.nextLong(), "exactly one chunk RNG long");
        }
        try {
            GreatwoodLegacyHeight.begin(null);
            throw new AssertionError("null RNG accepted");
        } catch (NullPointerException expected) {
            assertions++;
        }
        System.out.println("M12 GreatwoodLegacyHeight: OK (" + assertions + " assertions)");
    }
}
