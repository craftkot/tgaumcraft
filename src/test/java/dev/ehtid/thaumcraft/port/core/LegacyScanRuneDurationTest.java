package dev.ehtid.thaumcraft.port.core;

/** Standalone Java 21 regression against BETA26 ItemThaumometer / FXBlockRunes. */
public final class LegacyScanRuneDurationTest {
    private static int assertions;

    private static void expect(int actual, int expected, String scenario) {
        assertions++;
        if (actual != expected)
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        expect(LegacyScanRuneDuration.lifetimeTicks(LegacyScanRuneDuration.BLOCK_UNITS), 45,
                "block: 3 * 15 ticks");
        expect(LegacyScanRuneDuration.entityUnits(1.8F), 27, "player units");
        expect(LegacyScanRuneDuration.lifetimeTicks(LegacyScanRuneDuration.entityUnits(1.8F)),
                81, "player rune lifetime");
        expect(LegacyScanRuneDuration.entityUnits(1.95F), 29, "tall entity truncation");
        expect(LegacyScanRuneDuration.lifetimeTicks(LegacyScanRuneDuration.entityUnits(1.95F)),
                87, "tall entity lifetime");
        expect(LegacyScanRuneDuration.entityUnits(0.6F), 9, "small entity units");
        expect(LegacyScanRuneDuration.lifetimeTicks(LegacyScanRuneDuration.entityUnits(0.6F)),
                27, "small entity lifetime");
        expect(LegacyScanRuneDuration.entityUnits(0.01F), 1, "subpixel entity clamp");
        expect(LegacyScanRuneDuration.entityUnits(0.0F), 1, "zero height clamp");
        expect(LegacyScanRuneDuration.entityUnits(Float.NaN), 1, "invalid height clamp");
        expect(LegacyScanRuneDuration.lifetimeTicks(Double.NaN), 45, "invalid wire fallback");
        expect(LegacyScanRuneDuration.lifetimeTicks(-2.0), 3, "negative wire clamp");
        expect(LegacyScanRuneDuration.lifetimeTicks(1000000000.0), 300000,
                "oversized wire clamp");
        System.out.println("BETA26 scan rune duration: OK (" + assertions + " assertions)");
    }
}
