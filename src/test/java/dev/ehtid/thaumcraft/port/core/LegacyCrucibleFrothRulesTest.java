package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.visual.LegacyCrucibleFrothRules;

/** Standalone Java 21 regression for BETA26 TileCrucible froth effects. */
public final class LegacyCrucibleFrothRulesTest {
    private static int assertions;

    private static void expect(boolean actual, boolean expected, String scenario) {
        assertions++;
        if (actual != expected)
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }

    private static void expect(int actual, int expected, String scenario) {
        assertions++;
        if (actual != expected)
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }

    private static void close(double actual, double expected, double epsilon, String scenario) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > epsilon)
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }

    private static void expectThrows(Runnable action, String scenario) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(scenario + ": expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        expect(LegacyCrucibleFrothRules.shouldSpawnSurface(151, 1), true, "first boiling heat");
        expect(LegacyCrucibleFrothRules.shouldSpawnSurface(150, 1000), false, "strict boiling threshold");
        expect(LegacyCrucibleFrothRules.shouldSpawnSurface(151, 0), false, "empty tank never froths");
        expect(LegacyCrucibleFrothRules.shouldSpawnSurface(200, 1000), true, "maximum heat froths");

        expect(LegacyCrucibleFrothRules.overflowParticleCount(150, 1000, 501), 0,
                "no overflow at threshold heat");
        expect(LegacyCrucibleFrothRules.overflowParticleCount(151, 0, 501), 0,
                "no overflow without water");
        expect(LegacyCrucibleFrothRules.overflowParticleCount(151, 1000, 500), 0,
                "strict 500-vis boundary");
        expect(LegacyCrucibleFrothRules.overflowParticleCount(151, 1000, 501), 8,
                "two rounds of four edge froths");
        expect(LegacyCrucibleFrothRules.overflowParticleCount(200, 1000, 1000), 8,
                "overflow count remains capped at eight");

        expect(LegacyCrucibleFrothRules.lifetime(false, 0), 4, "surface minimum lifetime");
        expect(LegacyCrucibleFrothRules.lifetime(false, 2), 6, "surface maximum lifetime");
        expect(LegacyCrucibleFrothRules.lifetime(true, 0), 12, "overflow minimum lifetime");
        expect(LegacyCrucibleFrothRules.lifetime(true, 11), 23, "overflow maximum lifetime");
        close(LegacyCrucibleFrothRules.scale(false, 0.0F), 0.2, 1e-6, "surface minimum scale");
        close(LegacyCrucibleFrothRules.scale(false, 0.999999F), 0.3999998, 1e-6,
                "surface maximum scale");
        close(LegacyCrucibleFrothRules.scale(true, 0.0F), 0.4, 1e-6, "overflow minimum scale");
        close(LegacyCrucibleFrothRules.scale(true, 0.999999F), 0.5999998, 1e-6,
                "overflow maximum scale");

        expect(LegacyCrucibleFrothRules.frame(false, 0, 4), 64, "surface start frame");
        expect(LegacyCrucibleFrothRules.frame(false, 2, 4), 64, "surface frame before fade frames");
        expect(LegacyCrucibleFrothRules.frame(false, 3, 4), 66, "surface penultimate frame");
        expect(LegacyCrucibleFrothRules.frame(false, 4, 4), 65, "surface final frame");
        expect(LegacyCrucibleFrothRules.frame(true, 10, 12), 73, "overflow start frame");
        expect(LegacyCrucibleFrothRules.frame(true, 11, 12), 66, "overflow penultimate frame");
        expect(LegacyCrucibleFrothRules.frame(true, 12, 12), 65, "overflow final frame");
        expect(LegacyCrucibleFrothRules.frame(true, 13, 12), 65, "overflow final-frame clamp");

        expect(Double.doubleToRawLongBits(LegacyCrucibleFrothRules.DEFAULT_SLOW_DOWN)
                        == Double.doubleToRawLongBits(0.9800000190734863D),
                true, "FXGeneric default slowdown");
        close(LegacyCrucibleFrothRules.RANDOM_MOVEMENT_SCALE, 0.001, 0.0, "Gaussian drift scale");
        close(LegacyCrucibleFrothRules.SURFACE_GRAVITY, 0.1, 1e-8, "surface gravity");
        close(LegacyCrucibleFrothRules.OVERFLOW_GRAVITY, 0.05, 1e-8, "overflow gravity");
        close(LegacyCrucibleFrothRules.OVERFLOW_ALPHA, 0.8, 1e-6, "overflow alpha");
        expect(LegacyCrucibleFrothRules.SURFACE_START_FRAME, 64, "surface atlas index");
        expect(LegacyCrucibleFrothRules.OVERFLOW_START_FRAME, 73, "overflow atlas index");
        expect(LegacyCrucibleFrothRules.OVERFLOW_PARTICLE_COUNT, 8, "legacy overflow count");

        expectThrows(() -> LegacyCrucibleFrothRules.lifetime(false, 3), "surface random upper bound");
        expectThrows(() -> LegacyCrucibleFrothRules.lifetime(true, 12), "overflow random upper bound");
        expectThrows(() -> LegacyCrucibleFrothRules.scale(false, -0.1F), "negative scale random");
        expectThrows(() -> LegacyCrucibleFrothRules.scale(true, Float.NaN), "non-finite scale random");
        expectThrows(() -> LegacyCrucibleFrothRules.frame(false, -1, 4), "negative particle age");
        expectThrows(() -> LegacyCrucibleFrothRules.frame(true, 0, 0), "non-positive lifetime");

        System.out.println("BETA26 Crucible froth/froth-down rules: OK (" + assertions + " assertions)");
    }
}
