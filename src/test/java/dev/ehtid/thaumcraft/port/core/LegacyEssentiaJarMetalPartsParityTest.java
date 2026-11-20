package dev.ehtid.thaumcraft.port.core;

/** Executable BETA26 ModelJar geometry and TileJarRenderer visibility oracle. */
public final class LegacyEssentiaJarMetalPartsParityTest {
    private static int assertions;

    private LegacyEssentiaJarMetalPartsParityTest() {}

    private static void check(boolean value, String name) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }

    private static void near(float expected, float actual, String name) {
        assertions++;
        if (Math.abs(expected - actual) > 0.00001F)
            throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        check(LegacyEssentiaJarMetalPartsLogic.TEXTURE_WIDTH == 64, "jarbrine texture width");
        check(LegacyEssentiaJarMetalPartsLogic.TEXTURE_HEIGHT == 32, "jarbrine texture height");
        check(LegacyEssentiaJarMetalPartsLogic.MIRRORED_UV, "ModelJar mirrored UV");

        check(LegacyEssentiaJarMetalPartsLogic.LID_TEXTURE_U == 32, "lid U");
        check(LegacyEssentiaJarMetalPartsLogic.LID_TEXTURE_V == 24, "lid V");
        near(-3.0F, LegacyEssentiaJarMetalPartsLogic.LID_X, "lid X");
        near(0.0F, LegacyEssentiaJarMetalPartsLogic.LID_Y, "lid Y");
        near(-3.0F, LegacyEssentiaJarMetalPartsLogic.LID_Z, "lid Z");
        near(6.0F, LegacyEssentiaJarMetalPartsLogic.LID_WIDTH, "lid width");
        near(2.0F, LegacyEssentiaJarMetalPartsLogic.LID_HEIGHT, "lid height");
        near(6.0F, LegacyEssentiaJarMetalPartsLogic.LID_DEPTH, "lid depth");
        near(-14.0F, LegacyEssentiaJarMetalPartsLogic.LID_PIVOT_Y, "lid pivot");
        near(1.001F, LegacyEssentiaJarMetalPartsLogic.BRACE_SCALE, "brace anti-z-fighting scale");
        near(0.75F, LegacyEssentiaJarMetalPartsLogic.braceMinY(), "brace lower world bound");
        near(0.875F, LegacyEssentiaJarMetalPartsLogic.braceMaxY(), "brace upper world bound");

        check(LegacyEssentiaJarMetalPartsLogic.EXTENSION_TEXTURE_U == 0, "extension U");
        check(LegacyEssentiaJarMetalPartsLogic.EXTENSION_TEXTURE_V == 23, "extension V");
        near(-2.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_X, "extension X");
        near(-16.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_Y, "extension Y");
        near(-2.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_Z, "extension Z");
        near(4.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_WIDTH, "extension width");
        near(2.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_HEIGHT, "extension height");
        near(4.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_DEPTH, "extension depth");
        near(0.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_PIVOT_Y, "extension pivot");
        near(0.9F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_SCALE_XZ, "extension X/Z scale");
        near(0.875F, LegacyEssentiaJarMetalPartsLogic.extensionMinY(), "extension lower world bound");
        near(1.0F, LegacyEssentiaJarMetalPartsLogic.extensionMaxY(), "extension upper world bound");

        check(!LegacyEssentiaJarMetalPartsLogic.shouldRenderBrace(false), "unbraced jar has no lid brace");
        check(LegacyEssentiaJarMetalPartsLogic.shouldRenderBrace(true), "braced jar renders lid brace");
        check(!LegacyEssentiaJarMetalPartsLogic.shouldRenderLidExtension(false), "no transport above has no extension");
        check(LegacyEssentiaJarMetalPartsLogic.shouldRenderLidExtension(true), "connectable transport above renders extension");

        System.out.println("BETA26 jar metal parts geometry/gates: PASS (" + assertions + " assertions)");
    }
}
