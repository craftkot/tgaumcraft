package dev.ehtid.thaumcraft.port.core.visual;

/** BETA26 FXDispatcher.drawBlockSparkles uses the raw FXGeneric scale keys. */
public final class SalisSparkleScaleRules {
    private SalisSparkleScaleRules() {}

    public static float blockFace(double gaussianSample) {
        if (!Double.isFinite(gaussianSample)) {
            throw new IllegalArgumentException("Gaussian sample must be finite");
        }
        // FXDispatcher: setScale(0.4f + Gaussian*0.1f, scale*2.0f).
        // FXGeneric does not clamp the source scale to DustParticleOptions limits.
        return 0.4F + (float) gaussianSample * 0.1F;
    }
}
