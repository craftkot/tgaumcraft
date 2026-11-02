package dev.ehtid.thaumcraft.port.core.visual;

/** BETA26 FXDispatcher.drawBlockSparkles face coordinates, without Minecraft dependencies. */
public final class SalisSparkleFaceLayout {
    private static final double FACE_OFFSET = 0.51D;
    private static final double SPREAD = 0.6D;
    private static final double SPARKLES_PER_EDGE = 20.0D;

    private SalisSparkleFaceLayout() {}

    public static int particlesPerFace(double xSize, double ySize, double zSize) {
        // 2 * floor(20 * average edge length); tiny shapes may emit zero.
        return 2 * (int) (((xSize + ySize + zSize) / 3.0D) * SPARKLES_PER_EDGE);
    }

    public static double coordinate(double min, double max, int faceStep, double gaussianSample) {
        if (faceStep < -1 || faceStep > 1) {
            throw new IllegalArgumentException("faceStep must be -1, 0 or 1");
        }
        // The original uses block-local 0.5, not the midpoint of the shape.
        double value = 0.5D + faceStep * FACE_OFFSET;
        if (faceStep == 0) value += gaussianSample * SPREAD;
        return Math.max(min, Math.min(max, value));
    }
}
