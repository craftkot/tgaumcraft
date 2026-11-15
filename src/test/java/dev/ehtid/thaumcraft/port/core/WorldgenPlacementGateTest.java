package dev.ehtid.thaumcraft.port.core;

/** Standalone M12 composition regression; no Minecraft runtime required. */
public final class WorldgenPlacementGateTest {
    private static int checks;

    private static void check(boolean expected, boolean actual, String label) {
        checks++;
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static boolean attempt(boolean fresh, boolean dimension, boolean loaded,
                                   int x, int y, int z, int chunkX, int chunkZ,
                                   int bottom, int top, int minY, int maxY) {
        return WorldgenPlacementGate.mayAttempt(fresh, dimension, loaded,
                x, y, z, chunkX, chunkZ, bottom, top, minY, maxY);
    }

    public static void main(String[] args) {
        for (int mask = 0; mask < 8; mask++) {
            boolean fresh = (mask & 1) != 0;
            boolean dimension = (mask & 2) != 0;
            boolean loaded = (mask & 4) != 0;
            check(mask == 7, attempt(fresh, dimension, loaded,
                    -1, 70, -16, -1, -1, -4, 10, -64, 320), "flags " + mask);
        }
        int[] xs = {-33, -32, -17, -16, -1, 0, 15, 16, 31, 32};
        for (int x : xs) {
            for (int z : xs) {
                int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
                check(true, attempt(true, true, true, x, 70, z, cx, cz,
                        -4, 10, -64, 320), "valid origin " + x + "," + z);
                check(false, attempt(true, true, true, x, 70, z, cx + 1, cz,
                        -4, 10, -64, 320), "foreign x chunk " + x + "," + z);
                check(false, attempt(true, true, true, x, 70, z, cx, cz - 1,
                        -4, 10, -64, 320), "foreign z chunk " + x + "," + z);
            }
        }
        check(false, attempt(true, true, true, 0, 320, 0, 0, 0,
                -100, -90, -64, 320), "invalid origin despite footprint");
        check(false, attempt(true, true, true, 0, -64, 0, 0, 0,
                -1, 10, -64, 320), "roots below world");
        check(false, attempt(true, true, true, 0, 319, 0, 0, 0,
                0, 1, -64, 320), "canopy above world");
        check(false, attempt(true, true, true, 0, 70, 0, 0, 0,
                2, -2, -64, 320), "inverted footprint");
        check(false, attempt(true, true, true, 0, 70, 0, 0, 0,
                0, 0, 320, 320), "empty world height");
        check(true, attempt(true, true, true, Integer.MIN_VALUE, -64,
                Integer.MAX_VALUE, Math.floorDiv(Integer.MIN_VALUE, 16),
                Math.floorDiv(Integer.MAX_VALUE, 16), 0, 0, -64, 320),
                "extreme x/z");
        System.out.println("M12 WorldgenPlacementGate: OK (" + checks + " assertions)");
    }
}
