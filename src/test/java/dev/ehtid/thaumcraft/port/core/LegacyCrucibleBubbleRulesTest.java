package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.visual.LegacyCrucibleBubbleRules;

/** Standalone Java 21 regression for BETA26 FXDispatcher.crucibleBubble. */
public final class LegacyCrucibleBubbleRulesTest {
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
        for (int roll = 0; roll < 6; roll++) {
            expect(LegacyCrucibleBubbleRules.shouldSpawn(roll, 1), roll == 0,
                    "one-in-six aspect bubble roll " + roll);
            expect(LegacyCrucibleBubbleRules.shouldSpawn(roll, 0), false,
                    "empty aspect list never bubbles " + roll);
        }
        expect(LegacyCrucibleBubbleRules.lifetime(0.0D), 47, "maximum FXGeneric lifetime");
        expect(LegacyCrucibleBubbleRules.lifetime(0.5D), 20, "middle FXGeneric lifetime");
        expect(LegacyCrucibleBubbleRules.lifetime(0.999999D), 15, "minimum FXGeneric lifetime");
        close(LegacyCrucibleBubbleRules.scale(0.0F), 0.3D, 0.000001D, "minimum bubble scale");
        close(LegacyCrucibleBubbleRules.scale(0.999999F), 0.5999997D,
                0.000001D, "maximum bubble scale");

        expect(LegacyCrucibleBubbleRules.frame(0, 15), 64, "initial atlas cell");
        expect(LegacyCrucibleBubbleRules.frame(13, 15), 64, "base frame before final frames");
        expect(LegacyCrucibleBubbleRules.frame(14, 15), 66, "first final-frame index");
        expect(LegacyCrucibleBubbleRules.frame(15, 15), 65, "last final-frame index");
        expect(LegacyCrucibleBubbleRules.frame(16, 15), 65, "final-frame lower clamp");

        expectThrows(() -> LegacyCrucibleBubbleRules.shouldSpawn(6, 1), "roll upper bound");
        expectThrows(() -> LegacyCrucibleBubbleRules.shouldSpawn(0, -1), "negative aspect count");
        expectThrows(() -> LegacyCrucibleBubbleRules.lifetime(1.0D), "lifetime random upper bound");
        expectThrows(() -> LegacyCrucibleBubbleRules.scale(-0.1F), "negative scale random");
        expectThrows(() -> LegacyCrucibleBubbleRules.frame(-1, 15), "negative particle age");
        System.out.println("BETA26 Crucible FXDispatcher.crucibleBubble: OK (" + assertions + " assertions)");
    }
}
