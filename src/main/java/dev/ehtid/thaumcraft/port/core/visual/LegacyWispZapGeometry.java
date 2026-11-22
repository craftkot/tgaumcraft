package dev.ehtid.thaumcraft.port.core.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Source-shaped geometry for BETA26 FXBolt.calcSteps.
 *
 * The original uses MathHelper.sin's 65,536-entry lookup table, a seeded
 * java.util.Random, a 0.1f displacement envelope and five-sided CoreGLE tubes.
 * Coordinates are relative to the source entity; endpoint positions are exact.
 */
public final class LegacyWispZapGeometry {
    private static final float[] SIN_TABLE = createSinTable();

    private LegacyWispZapGeometry() {}

    public record Point(double x, double y, double z, float width) {}

    public static List<Point> build(
            double deltaX,
            double deltaY,
            double deltaZ,
            float width,
            float phaseOffset,
            int seed,
            int particleAge,
            float partialTick
    ) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY) || !Double.isFinite(deltaZ)) {
            throw new IllegalArgumentException("BETA26 FXBolt delta must be finite");
        }
        if (!Float.isFinite(width) || width < 0.0F) {
            throw new IllegalArgumentException("BETA26 FXBolt width must be finite and non-negative");
        }
        if (!Float.isFinite(phaseOffset)) {
            throw new IllegalArgumentException("BETA26 FXBolt phase offset must be finite");
        }
        if (particleAge < 0 || particleAge > 2) {
            throw new IllegalArgumentException("BETA26 FXBolt render age must be 0..2");
        }
        if (!Float.isFinite(partialTick) || partialTick < 0.0F || partialTick > 1.0F) {
            throw new IllegalArgumentException("BETA26 FXBolt partial tick must be in [0,1]");
        }

        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        float length = (float) (distance * Math.PI);
        if (!Double.isFinite(distance) || !Float.isFinite(length)) {
            throw new IllegalArgumentException("BETA26 FXBolt length must be finite");
        }

        int steps = (int) length;
        if (steps <= 2) {
            // FXBolt.renderParticle skips the polycone unless it has >2 points.
            return List.of();
        }

        Random random = new Random(seed);
        ArrayList<Point> points = new ArrayList<>(steps);
        points.add(new Point(0.0D, 0.0D, 0.0D, width));
        float amplitude = (particleAge + partialTick) / 10.0F;

        for (int step = 1; step < steps - 1; step++) {
            float distanceAlong = step * (length / steps) + phaseOffset;
            double x = deltaX / steps * step + legacySin(distanceAlong / 4.0F) * amplitude;
            double y = deltaY / steps * step + legacySin(distanceAlong / 3.0F) * amplitude;
            double z = deltaZ / steps * step + legacySin(distanceAlong / 2.0F) * amplitude;

            x += (random.nextFloat() - random.nextFloat()) * 0.1F;
            y += (random.nextFloat() - random.nextFloat()) * 0.1F;
            z += (random.nextFloat() - random.nextFloat()) * 0.1F;

            float taper = random.nextInt(4) == 0
                    ? 1.0F - particleAge * 0.25F
                    : 1.0F;
            points.add(new Point(x, y, z, taper * width));
        }

        points.add(new Point(deltaX, deltaY, deltaZ, width));
        return List.copyOf(points);
    }

    private static float legacySin(float value) {
        return SIN_TABLE[(int) (value * 10430.378F) & 65535];
    }

    private static float[] createSinTable() {
        float[] table = new float[65536];
        for (int index = 0; index < table.length; index++) {
            table[index] = (float) Math.sin(index * Math.PI * 2.0D / table.length);
        }
        return table;
    }
}
