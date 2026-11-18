package dev.ehtid.thaumcraft.port.core;

/** Standalone Java 21 regression against BETA26 FXDispatcher.scanHighlight. */
public final class LegacyThaumometerSparkleRulesTest {
    private static int assertions;

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
        expect(LegacyThaumometerSparkleRules.channel(16, 32, 0), 16, "red lower bound");
        expect(LegacyThaumometerSparkleRules.channel(16, 32, 16), 32, "red upper bound");
        expect(LegacyThaumometerSparkleRules.channel(132, 165, 33), 165, "green upper bound");
        expect(LegacyThaumometerSparkleRules.channel(223, 239, 16), 239, "blue upper bound");
        expect(LegacyThaumometerSparkleRules.delay(0), 0, "zero delay");
        expect(LegacyThaumometerSparkleRules.delay(9), 9, "maximum delay");
        expect(LegacyThaumometerSparkleRules.lifetime(0), 16, "minimum lifetime");
        expect(LegacyThaumometerSparkleRules.lifetime(3), 19, "maximum lifetime");
        expect(LegacyThaumometerSparkleRules.firstFrame(true), 320, "20-percent frame sequence");
        expect(LegacyThaumometerSparkleRules.firstFrame(false), 512, "80-percent frame sequence");

        for (int lifetimeRoll = 0; lifetimeRoll < 4; lifetimeRoll++) {
            int lifetime = LegacyThaumometerSparkleRules.lifetime(lifetimeRoll);
            for (int alphaRoll = 0; alphaRoll < lifetime / 3; alphaRoll++) {
                int count = LegacyThaumometerSparkleRules.alphaKeyCount(lifetime, alphaRoll);
                if (count < 6 || count > 11) throw new AssertionError("alpha key count " + count);
                assertions++;
            }
        }
        for (int start : new int[] {320, 512}) {
            for (int age = 0; age < 32; age++) {
                expect(LegacyThaumometerSparkleRules.frame(start, age),
                        start + age % 16, "16-frame looping sequence");
            }
        }

        close(LegacyThaumometerSparkleRules.initialScale(0.0D), 0.4D, 0.000001D, "base scale");
        close(LegacyThaumometerSparkleRules.initialScale(-2.0D), 0.2D, 0.000001D,
                "negative Gaussian remains unclamped");
        close(LegacyThaumometerSparkleRules.scaleAt(0.4F, 0.0F), 0.4D, 0.000001D, "scale start");
        close(LegacyThaumometerSparkleRules.scaleAt(0.4F, 1.0F), 0.8D, 0.000001D, "scale end");
        close(LegacyThaumometerSparkleRules.alphaAt(new float[] {0.0F, 1.0F, 0.0F}, 0.0F),
                0.0D, 0.000001D, "alpha start");
        close(LegacyThaumometerSparkleRules.alphaAt(new float[] {0.0F, 1.0F, 0.0F}, 1.0F),
                1.0D, 0.000001D, "alpha key");
        close(LegacyThaumometerSparkleRules.alphaAt(new float[] {0.0F, 1.0F, 0.0F}, 2.0F),
                0.0D, 0.000001D, "alpha end");

        LegacyThaumometerSparkleRules.Wind calm = LegacyThaumometerSparkleRules.wind(0, 0);
        close(calm.x(), 0.00005D, 1.0E-10D, "phase-zero X wind");
        close(calm.z(), 0.0D, 1.0E-10D, "phase-zero Z wind");
        LegacyThaumometerSparkleRules.Wind rotated = LegacyThaumometerSparkleRules.wind(4, 9);
        float angle = 4 * 49 / 180.0F * (float) Math.PI;
        close(rotated.x(), Math.cos(angle) * 0.00005D, 1.0E-10D, "moon-phase X wind");
        close(rotated.z(), -Math.sin(angle) * 0.00005D, 1.0E-10D, "moon-phase Z wind");

        for (boolean first : new boolean[] {false, true}) {
            double payload = LegacyThaumometerSparkleRules.encodeColorVariant(16, 165, 239, first);
            LegacyThaumometerSparkleRules.ColorVariant decoded =
                    LegacyThaumometerSparkleRules.decodeColorVariant(payload);
            expect(decoded.red(), 16, "packed red");
            expect(decoded.green(), 165, "packed green");
            expect(decoded.blue(), 239, "packed blue");
            assertions++;
            if (decoded.firstSequence() != first) throw new AssertionError("packed frame selector");
        }
        float scale = -0.1375F;
        close(LegacyThaumometerSparkleRules.decodeScale(
                LegacyThaumometerSparkleRules.encodeScale(scale)), scale, 0.000001D, "scale transport");
        for (int delay = 0; delay <= 9; delay++) {
            expect(LegacyThaumometerSparkleRules.decodeDelay(
                    LegacyThaumometerSparkleRules.encodeDelay(delay)), delay, "delay transport");
        }

        expectThrows(() -> LegacyThaumometerSparkleRules.lifetime(4), "age roll upper bound");
        expectThrows(() -> LegacyThaumometerSparkleRules.delay(10), "delay upper bound");
        expectThrows(() -> LegacyThaumometerSparkleRules.alphaKeyCount(16, 5), "alpha key upper bound");
        expectThrows(() -> LegacyThaumometerSparkleRules.decodeColorVariant(999.0D), "invalid color payload");
        System.out.println("BETA26 Thaumometer FXGeneric sparkle: OK (" + assertions + " assertions)");
    }
}
