package dev.ehtid.thaumcraft.port.core;

public final class LegacyEssentiaJarRenderLogicTest {
    private static int assertions;

    private LegacyEssentiaJarRenderLogicTest() {}

    public static void main(String[] args) {
        AspectRegistry aspects = new AspectRegistry();

        eq(false, LegacyEssentiaJarRenderLogic.hasLiquid(-1));
        eq(false, LegacyEssentiaJarRenderLogic.hasLiquid(0));
        eq(true, LegacyEssentiaJarRenderLogic.hasLiquid(1));
        eq(true, LegacyEssentiaJarRenderLogic.hasLiquid(250));

        near(0.0F, LegacyEssentiaJarRenderLogic.liquidHeight(-1));
        near(0.0F, LegacyEssentiaJarRenderLogic.liquidHeight(0));
        near(0.0025F, LegacyEssentiaJarRenderLogic.liquidHeight(1));
        near(0.3125F, LegacyEssentiaJarRenderLogic.liquidHeight(125));
        near(0.625F, LegacyEssentiaJarRenderLogic.liquidHeight(250));
        near(0.625F, LegacyEssentiaJarRenderLogic.liquidHeight(251));
        near(0.0625F, LegacyEssentiaJarRenderLogic.liquidTopY(0));
        near(0.065F, LegacyEssentiaJarRenderLogic.liquidTopY(1));
        near(0.375F, LegacyEssentiaJarRenderLogic.liquidTopY(125));
        near(0.6875F, LegacyEssentiaJarRenderLogic.liquidTopY(250));

        near(0.25F, LegacyEssentiaJarRenderLogic.LIQUID_MIN_X);
        near(0.75F, LegacyEssentiaJarRenderLogic.LIQUID_MAX_X);
        near(0.0625F, LegacyEssentiaJarRenderLogic.LIQUID_BASE_Y);
        near(0.25F, LegacyEssentiaJarRenderLogic.LIQUID_MIN_Z);
        near(0.75F, LegacyEssentiaJarRenderLogic.LIQUID_MAX_Z);
        near(0.625F, LegacyEssentiaJarRenderLogic.LIQUID_MAX_HEIGHT);

        eq(0xFFFF7E, LegacyEssentiaJarRenderLogic.aspectColor(aspects, "aer"));
        eq(0xFF5A01, LegacyEssentiaJarRenderLogic.aspectColor(aspects, "ignis"));
        eq(0, LegacyEssentiaJarRenderLogic.aspectColor(aspects, "not_an_aspect"));
        eq(0, LegacyEssentiaJarRenderLogic.aspectColor(aspects, null));
        eq(0, LegacyEssentiaJarRenderLogic.aspectColor(null, "aer"));

        float previous = -1.0F;
        for (int amount = 0; amount <= LegacyEssentiaJarLogic.CAPACITY; amount++) {
            float expected = amount / 250.0F * 0.625F;
            float actual = LegacyEssentiaJarRenderLogic.liquidHeight(amount);
            near(expected, actual);
            if (actual < previous) throw new AssertionError("Liquid height is not monotonic at " + amount);
            assertions++;
            previous = actual;
        }

        System.out.println("BETA26 essentia jar liquid geometry/tint: PASS (" + assertions + " assertions)");
    }

    private static void near(float expected, float actual) {
        assertions++;
        if (Math.abs(expected - actual) > 0.000001F) {
            throw new AssertionError("expected " + expected + " got " + actual);
        }
    }

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " got " + actual);
        }
    }
}
