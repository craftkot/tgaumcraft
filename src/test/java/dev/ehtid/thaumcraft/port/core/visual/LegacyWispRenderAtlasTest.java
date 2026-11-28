package dev.ehtid.thaumcraft.port.core.visual;

/** Standalone Java 21 regression for the three original BETA26 Wisp atlas layers. */
public final class LegacyWispRenderAtlasTest {
    private static int assertions;
    private LegacyWispRenderAtlasTest() {}

    private static void expect(int actual, int expected, String scenario) {
        assertions++;
        if (actual != expected) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }
    private static void expect(boolean actual, boolean expected, String scenario) {
        assertions++;
        if (actual != expected) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }
    private static void close(float actual, float expected, String scenario) {
        assertions++;
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 1.0E-7F) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
    }
    private static void expectThrows(Runnable action, String scenario) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(scenario + ": expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        expect(LegacyWispRenderAtlas.animatedFrame(512, 0), 512, "outer starts at 512");
        expect(LegacyWispRenderAtlas.animatedFrame(512, 15), 527, "outer ends at 527");
        expect(LegacyWispRenderAtlas.animatedFrame(512, 16), 512, "outer loops every 16 ticks");
        expect(LegacyWispRenderAtlas.animatedFrame(320, 15), 335, "inner uses 320..335");
        expect(LegacyWispRenderAtlas.animatedFrame(800, 15), 815, "aspect uses 800..815");
        expect(LegacyWispRenderAtlas.animatedFrame(800, -1), 815, "negative tick wraps");

        LegacyWispRenderAtlas.Uv outer = LegacyWispRenderAtlas.frameUv(512, 64);
        close(outer.u0(), 0.0F, "outer column");
        close(outer.u1(), 1.0F / 64.0F, "outer width");
        close(outer.v0(), 8.0F / 64.0F, "outer row");
        close(outer.v1(), 9.0F / 64.0F, "outer height");

        LegacyWispRenderAtlas.Uv inner = LegacyWispRenderAtlas.frameUv(320, 64);
        close(inner.v0(), 5.0F / 64.0F, "inner row");

        LegacyWispRenderAtlas.Uv aspectFirst = LegacyWispRenderAtlas.frameUv(800, 32);
        close(aspectFirst.u0(), 0.0F, "aspect frame 800 column");
        close(aspectFirst.u1(), 1.0F / 32.0F, "aspect frame 800 width");
        close(aspectFirst.v0(), 25.0F / 32.0F, "aspect frame 800 row");
        close(aspectFirst.v1(), 26.0F / 32.0F, "aspect frame 800 height");

        LegacyWispRenderAtlas.Uv aspectLast = LegacyWispRenderAtlas.frameUv(815, 32);
        close(aspectLast.u0(), 15.0F / 32.0F, "aspect frame 815 column");
        close(aspectLast.u1(), 16.0F / 32.0F, "aspect frame 815 right edge");
        close(aspectLast.v0(), 25.0F / 32.0F, "aspect animation row");
        close(aspectLast.v1(), 26.0F / 32.0F, "aspect animation bottom");

        LegacyWispRenderAtlas.Uv wrongGrid = LegacyWispRenderAtlas.frameUv(800, 64);
        expect(Float.floatToIntBits(aspectFirst.v0()) != Float.floatToIntBits(wrongGrid.v0()),
                true, "64x64 grid selects wrong auranodes row");

        expectThrows(() -> LegacyWispRenderAtlas.frameUv(1024, 32), "out-of-range node frame rejected");
        expectThrows(() -> LegacyWispRenderAtlas.frameUv(800, 0), "zero grid rejected");
        expectThrows(() -> LegacyWispRenderAtlas.frameUv(-1, 32), "negative frame rejected");
        expectThrows(() -> LegacyWispRenderAtlas.animatedFrame(-1, 0), "negative base rejected");
        System.out.println("BETA26 Wisp atlas frame and UV parity: OK (" + assertions + " assertions)");
    }
}
