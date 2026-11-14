package dev.ehtid.thaumcraft.port.core;

/** Standalone Java 21 regression for M12 vertical placement bounds. */
public final class WorldgenHeightGateTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // Modern overworld: [-64, 320); both limits are supplied by the caller.
        check(WorldgenHeightGate.originWithinBuildHeight(-64, -64, 320), "lower bound");
        check(WorldgenHeightGate.originWithinBuildHeight(319, -64, 320), "upper minus one");
        check(!WorldgenHeightGate.originWithinBuildHeight(-65, -64, 320), "below world");
        check(!WorldgenHeightGate.originWithinBuildHeight(320, -64, 320), "exclusive upper");
        check(!WorldgenHeightGate.originWithinBuildHeight(0, 0, 0), "empty range");
        check(!WorldgenHeightGate.originWithinBuildHeight(0, 5, -5), "inverted range");

        check(WorldgenHeightGate.spanWithinBuildHeight(70, -4, 10, -64, 320), "tree span");
        check(WorldgenHeightGate.spanWithinBuildHeight(-64, 0, 0, -64, 320), "lower point");
        check(WorldgenHeightGate.spanWithinBuildHeight(319, 0, 0, -64, 320), "upper point");
        check(!WorldgenHeightGate.spanWithinBuildHeight(-64, -1, 0, -64, 320), "root below");
        check(!WorldgenHeightGate.spanWithinBuildHeight(319, 0, 1, -64, 320), "canopy above");
        check(!WorldgenHeightGate.spanWithinBuildHeight(70, 3, -3, -64, 320), "inverted offsets");
        check(!WorldgenHeightGate.spanWithinBuildHeight(70, 0, 0, 320, 320), "empty world");
        check(!WorldgenHeightGate.spanWithinBuildHeight(70, 0, 0, 320, -64), "inverted world");

        // Addition must widen to long before adding offsets.
        check(!WorldgenHeightGate.spanWithinBuildHeight(
                Integer.MAX_VALUE, 1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE),
                "positive overflow");
        check(!WorldgenHeightGate.spanWithinBuildHeight(
                Integer.MIN_VALUE, -1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE),
                "negative overflow");
        check(WorldgenHeightGate.spanWithinBuildHeight(
                Integer.MIN_VALUE, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE),
                "minimum integer");
        check(WorldgenHeightGate.spanWithinBuildHeight(
                Integer.MAX_VALUE - 1, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE),
                "maximum permitted integer");
        check(!WorldgenHeightGate.spanWithinBuildHeight(
                Integer.MAX_VALUE, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE),
                "exclusive max integer");
        System.out.println("M12 WorldgenHeightGate: OK (" + assertions + " assertions)");
    }
}
