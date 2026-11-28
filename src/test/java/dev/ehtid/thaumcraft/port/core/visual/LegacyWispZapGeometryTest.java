package dev.ehtid.thaumcraft.port.core.visual;

import java.util.List;

/** Standalone Java 21 regression for the pinned BETA26 FXBolt.calcSteps contract. */
public final class LegacyWispZapGeometryTest {
    private static int assertions;

    private LegacyWispZapGeometryTest() {}

    private static void expect(boolean actual, boolean expected, String scenario) {
        assertions++;
        if (actual != expected) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expect(int actual, int expected, String scenario) {
        assertions++;
        if (actual != expected) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
    }

    private static void same(double actual, double expected, String scenario) {
        assertions++;
        if (Double.doubleToLongBits(actual) != Double.doubleToLongBits(expected)) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
    }

    private static void close(float actual, float expected, float epsilon, String scenario) {
        assertions++;
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > epsilon) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
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
        List<LegacyWispZapGeometry.Point> tooShort =
                LegacyWispZapGeometry.build(0.5D, 0.0D, 0.0D, 0.6F, 0.0F, 17, 0, 0.0F);
        expect(tooShort.isEmpty(), true, "BETA26 omits tubes with <=2 points");

        double dx = 2.0D;
        double dy = 1.0D;
        double dz = -1.0D;
        float width = 0.6F;
        List<LegacyWispZapGeometry.Point> first =
                LegacyWispZapGeometry.build(dx, dy, dz, width, 3.25F, 734, 0, 0.25F);
        int expectedSteps = (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * Math.PI);
        expect(first.size(), expectedSteps, "BETA26 step count is floor(distance * PI)");
        same(first.get(0).x(), 0.0D, "source X is exact");
        same(first.get(0).y(), 0.0D, "source Y is exact");
        same(first.get(0).z(), 0.0D, "source Z is exact");
        same(first.get(first.size() - 1).x(), dx, "target X is exact");
        same(first.get(first.size() - 1).y(), dy, "target Y is exact");
        same(first.get(first.size() - 1).z(), dz, "target Z is exact");
        close(first.get(0).width(), width, 0.0F, "source width is unchanged");
        close(first.get(first.size() - 1).width(), width, 0.0F, "target width is unchanged");

        List<LegacyWispZapGeometry.Point> repeat =
                LegacyWispZapGeometry.build(dx, dy, dz, width, 3.25F, 734, 0, 0.25F);
        expect(first.equals(repeat), true, "same seed and partial tick reproduce the same path");

        List<LegacyWispZapGeometry.Point> late =
                LegacyWispZapGeometry.build(dx, dy, dz, width, 3.25F, 734, 2, 0.25F);
        expect(first.equals(late), false, "three-tick bolt changes its seeded displacement over age");
        float taperedWidth = width * 0.5F;
        for (int index = 1; index < late.size() - 1; index++) {
            float actualWidth = late.get(index).width();
            boolean validWidth = Float.floatToIntBits(actualWidth) == Float.floatToIntBits(width)
                    || Float.floatToIntBits(actualWidth) == Float.floatToIntBits(taperedWidth);
            expect(validWidth, true, "BETA26 one-in-four interior width taper at point " + index);
        }

        List<LegacyWispZapGeometry.Point> finalTick =
                LegacyWispZapGeometry.build(dx, dy, dz, width, 3.25F, 734, 3, 0.25F);
        expect(finalTick.size(), expectedSteps, "BETA26 renders the max-age tick before expiry");
        expect(finalTick.equals(late), false, "age-three FXBolt geometry retains its final movement");
        float finalTaperedWidth = width * 0.25F;
        for (int index = 1; index < finalTick.size() - 1; index++) {
            float actualWidth = finalTick.get(index).width();
            boolean validWidth = Float.floatToIntBits(actualWidth) == Float.floatToIntBits(width)
                    || Float.floatToIntBits(actualWidth) == Float.floatToIntBits(finalTaperedWidth);
            expect(validWidth, true, "BETA26 age-three taper at point " + index);
        }
        expect(LegacyWispZapGeometry.isExpired(3L), false, "BETA26 max-age tick remains renderable");
        expect(LegacyWispZapGeometry.isExpired(4L), true, "BETA26 expires on the tick after max age");
        expect(LegacyWispZapGeometry.isExpired(3.999D), false, "final partial max-age tick remains visible");
        expect(LegacyWispZapGeometry.isExpired(4.0D), true, "age four is the first expired render age");
        close(LegacyWispZapGeometry.alpha(0), 1.0F, 0.0F, "BETA26 initial bolt alpha");
        close(LegacyWispZapGeometry.alpha(1), 2.0F / 3.0F, 0.000001F, "BETA26 age-one alpha");
        close(LegacyWispZapGeometry.alpha(2), 1.0F / 3.0F, 0.000001F, "BETA26 age-two alpha");
        close(LegacyWispZapGeometry.alpha(3), 0.1F, 0.000001F, "BETA26 final-age alpha floor");

        expectThrows(() -> LegacyWispZapGeometry.build(dx, dy, dz, width, 0.0F, 1, 4, 0.0F),
                "expired particle age rejected");
        expectThrows(() -> LegacyWispZapGeometry.alpha(4), "expired particle alpha rejected");
        expectThrows(() -> LegacyWispZapGeometry.build(dx, dy, dz, width, 0.0F, 1, 0, 1.1F),
                "partial tick above one rejected");
        expectThrows(() -> LegacyWispZapGeometry.build(dx, dy, dz, -0.1F, 0.0F, 1, 0, 0.0F),
                "negative width rejected");
        expectThrows(() -> LegacyWispZapGeometry.build(Double.NaN, dy, dz, width, 0.0F, 1, 0, 0.0F),
                "non-finite endpoint rejected");

        System.out.println("BETA26 FXBolt geometry: OK (" + assertions + " assertions)");
    }
}
