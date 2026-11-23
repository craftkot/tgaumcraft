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
        int[] edges = {-33, -32, -17, -16, -15, -1, 0, 1, 15, 16, 17, 31, 32};
        for (int x : edges) {
            for (int z : edges) {
                int cx = WorldgenChunkGate.chunkCoordinate(x);
                int cz = WorldgenChunkGate.chunkCoordinate(z);
                check(WorldgenChunkGate.mayAttemptPlacementAt(true, true, true, x, z, cx, cz),
                        "coordinate gate accepts own chunk " + x + "," + z);
                check(!WorldgenChunkGate.mayAttemptPlacementAt(true, true, true, x, z, cx + 1, cz),
                        "coordinate gate rejects neighbor X " + x + "," + z);
                check(!WorldgenChunkGate.mayAttemptPlacementAt(true, true, true, x, z, cx, cz - 1),
                        "coordinate gate rejects neighbor Z " + x + "," + z);
                check(!WorldgenChunkGate.mayAttemptPlacementAt(false, true, true, x, z, cx, cz),
                        "coordinate gate rejects reload " + x + "," + z);
            }
        }
        // The coordinate-aware adapter must reject every incomplete context, not
        // just reloads. This guards against accidentally bypassing dimension or
        // availability checks when the caller supplies a valid origin.
        for (int mask = 0; mask < 8; mask++) {
            boolean fresh = (mask & 1) != 0;
            boolean dimension = (mask & 2) != 0;
            boolean available = (mask & 4) != 0;
            for (int x : edges) {
                for (int z : edges) {
                    int cx = WorldgenChunkGate.chunkCoordinate(x);
                    int cz = WorldgenChunkGate.chunkCoordinate(z);
                    check(WorldgenChunkGate.mayAttemptPlacementAt(
                                    fresh, dimension, available, x, z, cx, cz) == (mask == 7),
                            "coordinate flags " + mask + " at " + x + "," + z);
                }
            }
        }
        // Chunk coordinates at int extrema must not overflow via +/-15
        // arithmetic, especially on negative world borders.
        int[] extremeBlocks = {Integer.MIN_VALUE, Integer.MIN_VALUE + 15,
                Integer.MIN_VALUE + 16, Integer.MAX_VALUE - 15,
                Integer.MAX_VALUE};
        int[] extremeChunks = {-134217728, -134217728, -134217727,
                134217727, 134217727};
        for (int i = 0; i < extremeBlocks.length; i++) {
            int b = extremeBlocks[i];
            int c = extremeChunks[i];
            check(WorldgenChunkGate.chunkCoordinate(b) == c, "extreme chunk " + b);
            check(WorldgenChunkGate.originInChunk(b, b, c, c), "extreme origin " + b);
            check(WorldgenChunkGate.mayAttemptPlacementAt(true, true, true, b, b, c, c),
                    "extreme placement " + b);
        }
        System.out.println("M12 WorldgenChunkGate: OK (" + assertions + " assertions)");
    }
}
