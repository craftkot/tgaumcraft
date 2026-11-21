package dev.ehtid.thaumcraft.port.core;

public final class LegacyEssentiaJarLabelRenderParityTest {
    private static int assertions;

    public static void main(String[] args) {
        check(!LegacyEssentiaJarLabelRenderLogic.shouldRender(null), "null filter hides label");
        check(LegacyEssentiaJarLabelRenderLogic.shouldRender("aer"), "Aer filter shows label");
        check(LegacyEssentiaJarLabelRenderLogic.shouldRender(""), "non-null empty filter follows original null-only gate");

        equal(0, LegacyEssentiaJarLabelRenderLogic.facingRotationDegrees(2), "north");
        equal(180, LegacyEssentiaJarLabelRenderLogic.facingRotationDegrees(3), "south");
        equal(270, LegacyEssentiaJarLabelRenderLogic.facingRotationDegrees(4), "west");
        equal(90, LegacyEssentiaJarLabelRenderLogic.facingRotationDegrees(5), "east");
        equal(0, LegacyEssentiaJarLabelRenderLogic.facingRotationDegrees(0), "unknown facing fallback");

        equal(0, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees("aer", 250, 2), "Aer straight");
        equal(0, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees("ignis", 250, 2), "Ignis straight");
        equal(-2, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees("terra", 250, 2), "Terra crooked");
        equal(-2, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees(true, "terra", 250, 2),
                "crooked labels enabled");
        equal(0, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees(false, "terra", 250, 2),
                "crooked labels disabled");
        equal(0, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees(false, "praemunio", 250, 2),
                "disabled labels bypass negative signed remainder");
        equal(-4, LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees("praemunio", 250, 2),
                "negative Java remainder is preserved");

        close(-0.4F, LegacyEssentiaJarLabelRenderLogic.LABEL_CENTER_Y, "label Y");
        close(0.315F, LegacyEssentiaJarLabelRenderLogic.LABEL_CENTER_Z, "label Z");
        close(0.316F, LegacyEssentiaJarLabelRenderLogic.ICON_CENTER_Z, "icon Z");
        close(0.25F, LegacyEssentiaJarLabelRenderLogic.LABEL_HALF_SIZE, "label half size");
        close(0.021F, LegacyEssentiaJarLabelRenderLogic.ICON_SCALE, "icon scale");
        close(8.0F, LegacyEssentiaJarLabelRenderLogic.ICON_HALF_SIZE, "icon half size");
        equal(0x1A1A1A, LegacyEssentiaJarLabelRenderLogic.ICON_TINT_RGB, "icon monochrome tint");
        equal(204, LegacyEssentiaJarLabelRenderLogic.ICON_ALPHA, "icon alpha");

        System.out.println("BETA26 jar aspect-label render parity: PASS (" + assertions + " assertions)");
    }

    private static void check(boolean value, String name) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }

    private static void equal(int expected, int actual, String name) {
        assertions++;
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    private static void close(float expected, float actual, String name) {
        assertions++;
        if (Math.abs(expected - actual) > 0.00001F) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
