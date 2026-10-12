package dev.ehtid.thaumcraft.port.core;

/**
 * Original BETA26 TileCrucible.getFluidHeight() and
 * TileCrucibleRenderer.renderFluid() formulas, independent of NeoForge.
 * Keep water and aspect quantities separate: the model reflects both.
 */
public final class LegacyCrucibleSurface {
    private LegacyCrucibleSurface() {}

    /** Surface Y in world-block units, if there is water in the tank. */
    public static float height(int waterMb, int storedVis) {
        if (waterMb <= 0) return 0.0f; // the renderer must hide empty tanks
        float base = 0.3f + 0.5f * Math.min(1000, waterMb) / 1000.0f;
        float raised = base + Math.max(0, storedVis) / 500.0f * (1.0f - base);
        if (raised > 1.0f) return 1.001f;
        if (raised == 1.0f) return 0.9999f;
        return raised;
    }

    /** Original aspect-tinted water brightness factor. */
    public static float saturation(int storedVis) {
        float s = Math.max(0, storedVis) / 500.0f;
        if (s > 0.0f) s = 0.5f + s / 2.0f;
        return Math.min(1.0f, s);
    }

    public static float red(int storedVis) { return 1.0f; }
    public static float green(int storedVis) { return 1.0f - saturation(storedVis) / 3.0f; }
    public static float blue(int storedVis) { return 1.0f - saturation(storedVis); }
    public static float alpha(int storedVis) { return 1.0f - saturation(storedVis) / 2.0f; }
}
