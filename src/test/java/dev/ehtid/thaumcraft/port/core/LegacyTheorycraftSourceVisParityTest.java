package dev.ehtid.thaumcraft.port.core;

public final class LegacyTheorycraftSourceVisParityTest {
    private LegacyTheorycraftSourceVisParityTest() {}
    private static void eq(int want, int got) {
        if (want != got) throw new AssertionError("expected " + want + " got " + got);
    }
    public static void main(String[] ignored) {
        eq(15, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:ingot", 2));
        eq(20, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:amber", 0));
        eq(20, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:quicksilver", 0));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:ingot", 0));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:ingot", 1));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:ingot", 3));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:brain", 0));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:nitor_yellow", 0));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("thaumcraft:amber", 1));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize("minecraft:iron_ingot", 0));
        eq(-1, LegacyTheorycraftSourceVis.explicitlyTaggedVisSize(null, 0));
        System.out.println("Original BETA26 CardInfuse explicit ore-tag Vis: OK");
    }
}
