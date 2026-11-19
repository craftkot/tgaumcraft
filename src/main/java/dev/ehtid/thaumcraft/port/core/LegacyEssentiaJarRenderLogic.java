package dev.ehtid.thaumcraft.port.core;

public final class LegacyEssentiaJarRenderLogic {
    private LegacyEssentiaJarRenderLogic() {}

    public static final float LIQUID_MIN_X = 0.25F;
    public static final float LIQUID_MAX_X = 0.75F;
    public static final float LIQUID_BASE_Y = 0.0625F;
    public static final float LIQUID_MIN_Z = 0.25F;
    public static final float LIQUID_MAX_Z = 0.75F;
    public static final float LIQUID_MAX_HEIGHT = 0.625F;

    public static boolean hasLiquid(int amount) {
        return amount > 0;
    }

    /** BETA26: amount / 250.0f * 0.625f, with modern-state bounds protection. */
    public static float liquidHeight(int amount) {
        int bounded = Math.max(0, Math.min(LegacyEssentiaJarLogic.CAPACITY, amount));
        return bounded / (float) LegacyEssentiaJarLogic.CAPACITY * LIQUID_MAX_HEIGHT;
    }

    public static float liquidTopY(int amount) {
        return LIQUID_BASE_Y + liquidHeight(amount);
    }

    /** Original Color(0) fallback for absent or unknown aspect data. */
    public static int aspectColor(AspectRegistry registry, String aspectTag) {
        if (registry == null || aspectTag == null) return 0;
        AspectData aspect = registry.get(aspectTag);
        return aspect == null ? 0 : aspect.color() & 0xFFFFFF;
    }
}
