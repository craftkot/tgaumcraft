package dev.ehtid.thaumcraft.port.core.visual;

/**
 * BETA26 drawBlockSparkles samples a 0.4 + Gaussian*0.1 sprite scale.
 * Clamp only the extreme tails to the valid modern DustParticleOptions size
 * interval while the original FXGeneric sprite renderer is still absent.
 */
public final class SalisSparkleScaleRules {
    private static final float MIN_DUST_SCALE = 0.01F;
    private static final float MAX_DUST_SCALE = 4.0F;

    private SalisSparkleScaleRules() {}

    public static float blockFace(double gaussianSample) {
        if (!Double.isFinite(gaussianSample)) {
            throw new IllegalArgumentException("Gaussian sample must be finite");
        }
        float original = 0.4F + (float) gaussianSample * 0.1F;
        return Math.max(MIN_DUST_SCALE, Math.min(MAX_DUST_SCALE, original));
    }
}
