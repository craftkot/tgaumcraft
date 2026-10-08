package dev.ehtid.thaumcraft.port.core;

/** Exact arithmetic from BETA26 HudHandler.renderThaumometerHud. */
public final class ThaumometerHudMath {
    private static final float VIS_CON = 525.0F;

    private ThaumometerHudMath() {}

    public record State(float base, float vis, float flux,
                        float visTop, float fluxTop, float baseTop) {}

    public static State calculate(short baseRaw, float visRaw, float fluxRaw) {
        float base = clamp01(baseRaw / VIS_CON);
        float vis = clamp01(visRaw / VIS_CON);
        float flux = clamp01(fluxRaw / VIS_CON);

        if (flux + vis > 1.0F) {
            float factor = 1.0F / (flux + vis);
            base *= factor;
            vis *= factor;
            flux *= factor;
        }

        float visTop = 10.0F + (1.0F - vis) * 64.0F;
        float fluxTop = 10.0F + (1.0F - flux - vis) * 64.0F;
        float baseTop = 8.0F + (1.0F - base) * 64.0F;
        return new State(base, vis, flux, visTop, fluxTop, baseTop);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
