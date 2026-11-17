package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded BETA26 Flux Rift polycone centerline and radii.
 *
 * Source: EntityFluxRift.calcSteps. Keeping this in the loader-free core makes
 * the same mesh deterministic on the server, client and in parity tests.
 */
public final class LegacyFluxRiftGeometry {
    private LegacyFluxRiftGeometry() {}

    public record Point(double x, double y, double z, float width) {}

    public static List<Point> generate(int size, int seed) {
        ArrayList<Point> points = new ArrayList<>();
        if (size <= 0) {
            points.add(new Point(0.0D, 0.0D, 0.0D, 0.0F));
            points.add(new Point(0.0D, 0.0D, 0.0D, 0.0F));
            return List.copyOf(points);
        }

        Random random = new Random(seed);
        Vector right = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
        Vector left = right.scale(-1.0D);
        Vector rightPosition = Vector.ZERO;
        Vector leftPosition = Vector.ZERO;
        int steps = (int) Math.ceil(size / 3.0F);
        float girth = size / 300.0F;
        float decrement = girth / steps;
        double angle = 0.33D;

        for (int step = 0; step < steps; step++) {
            girth -= decrement;

            right = right.rotatePitch((float) (random.nextGaussian() * angle));
            right = right.rotateYaw((float) (random.nextGaussian() * angle));
            rightPosition = rightPosition.add(right.scale(0.2D));
            points.add(new Point(rightPosition.x(), rightPosition.y(), rightPosition.z(), girth));

            left = left.rotatePitch((float) (random.nextGaussian() * angle));
            left = left.rotateYaw((float) (random.nextGaussian() * angle));
            leftPosition = leftPosition.add(left.scale(0.2D));
            points.add(0, new Point(leftPosition.x(), leftPosition.y(), leftPosition.z(), girth));
        }

        rightPosition = rightPosition.add(right.scale(0.1D));
        points.add(new Point(rightPosition.x(), rightPosition.y(), rightPosition.z(), 0.0F));
        leftPosition = leftPosition.add(left.scale(0.1D));
        points.add(0, new Point(leftPosition.x(), leftPosition.y(), leftPosition.z(), 0.0F));
        return List.copyOf(points);
    }

    private record Vector(double x, double y, double z) {
        private static final Vector ZERO = new Vector(0.0D, 0.0D, 0.0D);

        Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        Vector scale(double amount) {
            return new Vector(x * amount, y * amount, z * amount);
        }

        Vector normalize() {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length < 1.0E-4D ? ZERO : new Vector(x / length, y / length, z / length);
        }

        Vector rotatePitch(float pitch) {
            float cosine = (float) Math.cos(pitch);
            float sine = (float) Math.sin(pitch);
            return new Vector(x, y * cosine + z * sine, z * cosine - y * sine);
        }

        Vector rotateYaw(float yaw) {
            float cosine = (float) Math.cos(yaw);
            float sine = (float) Math.sin(yaw);
            return new Vector(x * cosine + z * sine, y, z * cosine - x * sine);
        }
    }
}
