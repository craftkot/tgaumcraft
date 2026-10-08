package dev.ehtid.thaumcraft.port.core;

/** Exact BETA26 AuraHandler.generateAura base-vis arithmetic. */
public final class AuraGenerationLogic {
    private AuraGenerationLogic() {}

    /**
     * BETA26 averages the center biome aura modifier with the four horizontal
     * neighbours, multiplies by 500 and a gaussian factor, truncates to int,
     * narrows to short, then clamps that short to 0..500.
     */
    public static int generatedBase(float center, float north, float south,
                                    float west, float east, double gaussian) {
        float modifier = (center + north + south + west + east) / 5.0F;
        float factor = (float) (1.0D + gaussian * 0.10000000149011612D);
        short raw = (short) (int) (modifier * 500.0F * factor);
        return Math.max(0, Math.min(500, raw));
    }
}
