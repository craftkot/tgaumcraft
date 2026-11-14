package dev.ehtid.thaumcraft.port.core;

/** Standalone Java 21 regression for M12 placement guards. */
public final class WorldgenChunkGateTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int mask = 0; mask < 16; mask++) {
            boolean fresh = (mask & 1) != 0;
            boolean dimension = (mask & 2) != 0;
            boolean available = (mask & 4) != 0;
            boolean inside = (mask & 8) != 0;
            check(WorldgenChunkGate.mayAttemptPlacement(fresh, dimension, available, inside)
                    == (mask == 15), "gate truth table " + mask);
        }
        int[] blocks = {-33, -32, -17, -16, -15, -1, 0, 1, 15, 16, 17, 31, 32};
        int[] chunks = {-3, -2, -2, -1, -1, -1, 0, 0, 0, 1, 1, 1, 2};
        for (int i = 0; i < blocks.length; i++) {
            check(WorldgenChunkGate.chunkCoordinate(blocks[i]) == chunks[i],
                    "floor chunk " + blocks[i]);
            check(WorldgenChunkGate.originInChunk(blocks[i], blocks[i], chunks[i], chunks[i]),
                    "same chunk " + blocks[i]);
            check(!WorldgenChunkGate.originInChunk(blocks[i], blocks[i], chunks[i] + 1, chunks[i]),
                    "neighbor chunk " + blocks[i]);
        }
        check(!WorldgenChunkGate.originInChunk(-1, 0, -1, -1), "mixed Z border");
        check(WorldgenChunkGate.originInChunk(-16, 15, -1, 0), "mixed signs");
        System.out.println("M12 WorldgenChunkGate: OK (" + assertions + " assertions)");
    }
}
