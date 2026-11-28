package dev.ehtid.thaumcraft.port.core;

/** Standalone Java 21 regression for all BETA26 label overlay aspect colors. */
public final class LegacyLabelTintTest {
    private static int assertions;
    private static void expect(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AspectRegistry registry = new AspectRegistry();
        expect(registry.size() == 37, "BETA26 built-in aspect count");
        int tested = 0;
        for (AspectData aspect : registry.values()) {
            String tag = aspect.tag();
            int expected = 0xFF000000 | aspect.color();
            expect(LegacyLabelTint.colorFor(tag, 1) == expected, tag + " overlay color");
            expect(LegacyLabelTint.colorFor(tag, 0) == 0xFFFFFFFF, tag + " paper base");
            expect(LegacyLabelTint.colorFor(tag, 2) == 0xFFFFFFFF, tag + " other layer");
            tested++;
        }
        expect(tested == 37, "all 37 aspects tested");
        expect(LegacyLabelTint.colorFor(null, 1) == 0xFFFFFFFF, "blank label");
        expect(LegacyLabelTint.colorFor("", 1) == 0xFFFFFFFF, "empty aspect");
        expect(LegacyLabelTint.colorFor("not_an_aspect", 1) == 0xFFFFFFFF, "invalid aspect");
        expect(LegacyLabelTint.colorFor("aer", -1) == 0xFFFFFFFF, "negative tint index");
        expect(LegacyLabelTint.colorFor("aer", 1) != LegacyLabelTint.colorFor("ignis", 1),
                "different aspect colors");
        System.out.println("BETA26 label overlay tint: OK (" + assertions + " assertions)");
    }
}
