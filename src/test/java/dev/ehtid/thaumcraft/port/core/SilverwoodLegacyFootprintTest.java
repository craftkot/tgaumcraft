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
        check(SilverwoodLegacyFootprint.withinChunk(4, 8, 0, 0), false);
        check(SilverwoodLegacyFootprint.withinChunk(12, 8, 0, 0), false);
        check(SilverwoodLegacyFootprint.withinChunk(-8, -8, -1, -1), true);
        check(SilverwoodLegacyFootprint.withinChunk(-12, -8, -1, -1), false);
        check(SilverwoodLegacyFootprint.withinChunk(-4, -8, -1, -1), false);
        check(SilverwoodLegacyFootprint.withinChunk(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0), false);
        System.out.println("M12 SilverwoodLegacyFootprint: OK (" + assertions + " assertions)");
    }
}
