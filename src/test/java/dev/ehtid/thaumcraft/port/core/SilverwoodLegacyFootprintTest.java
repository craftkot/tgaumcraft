package dev.ehtid.thaumcraft.port.core;

public final class SilverwoodLegacyFootprintTest {
    private static int assertions;
    private static void check(boolean actual, boolean expected) {
        assertions++;
        if (actual != expected) throw new AssertionError("Assertion " + assertions + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        check(SilverwoodLegacyFootprint.validHeight(6), false);
        check(SilverwoodLegacyFootprint.validHeight(7), true);
        check(SilverwoodLegacyFootprint.validHeight(10), true);
        check(SilverwoodLegacyFootprint.validHeight(11), false);
        check(SilverwoodLegacyFootprint.withinBuildHeight(-63, 7, -64, 320), true);
        check(SilverwoodLegacyFootprint.withinBuildHeight(-64, 7, -64, 320), false);
        check(SilverwoodLegacyFootprint.withinBuildHeight(304, 10, -64, 320), true);
        check(SilverwoodLegacyFootprint.withinBuildHeight(305, 10, -64, 320), false);
        check(SilverwoodLegacyFootprint.withinBuildHeight(1, 7, 0, 0), false);
        check(SilverwoodLegacyFootprint.withinBuildHeight(Integer.MAX_VALUE, 10, -64, 320), false);
        check(SilverwoodLegacyFootprint.withinChunk(8, 8, 0, 0), true);
        check(SilverwoodLegacyFootprint.withinChunk(4, 8, 0, 0), true);
        check(SilverwoodLegacyFootprint.withinChunk(3, 8, 0, 0), false);
        check(SilverwoodLegacyFootprint.withinChunk(11, 8, 0, 0), true);
        check(SilverwoodLegacyFootprint.withinChunk(12, 8, 0, 0), false);
        check(SilverwoodLegacyFootprint.withinChunk(-8, -8, -1, -1), true);
        check(SilverwoodLegacyFootprint.withinChunk(-12, -8, -1, -1), true);
        check(SilverwoodLegacyFootprint.withinChunk(-4, -8, -1, -1), false);
        check(SilverwoodLegacyFootprint.withinChunk(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0), false);
        // Archived 9x9 origin distribution: x/z offsets 4..12 within each chunk.
        // Reachable canopy writes extend +/-4; safe offsets are 4..11.
        // Lock down this known parity loss until cross-chunk placement is coordinated.
        for (int chunkX : new int[] {-2, -1, 0, 2}) {
            for (int chunkZ : new int[] {-2, -1, 0, 2}) {
                int accepted = 0;
                for (int dx = 4; dx <= 12; dx++) {
                    for (int dz = 4; dz <= 12; dz++) {
                        boolean expected = dx >= 4 && dx <= 11 && dz >= 4 && dz <= 11;
                        boolean actual = SilverwoodLegacyFootprint.withinChunk(
                                chunkX * 16 + dx, chunkZ * 16 + dz, chunkX, chunkZ);
                        check(actual, expected);
                        if (actual) accepted++;
                    }
                }
                check(accepted == 64, true);
            }
        }
        System.out.println("M12 SilverwoodLegacyFootprint: OK (" + assertions + " assertions)");
    }
}
