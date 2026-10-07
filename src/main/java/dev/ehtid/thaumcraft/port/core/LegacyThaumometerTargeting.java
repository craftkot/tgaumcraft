package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loader-neutral geometry port of the entity-selection half of TC6 EntityUtils#getPointedEntity.
 *
 * <p>The original Thaumometer does not use vanilla's normal entity pick. It traces to 9 blocks,
 * ignores candidates whose entity position is closer than 1 block to the eye, performs a separate
 * unobstructed block line-of-sight test to the candidate eye, and expands every collision box by at
 * least 0.8 blocks. These details noticeably change which overlapping entity is selected.</p>
 */
public final class LegacyThaumometerTargeting {
    private LegacyThaumometerTargeting() {}

    public record Vec(double x, double y, double z) {
        public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
        public Vec scale(double amount) { return new Vec(x * amount, y * amount, z * amount); }
        public double distanceTo(Vec other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("invalid box");
            }
        }

        public static Box point(Vec point) {
            return new Box(point.x, point.y, point.z, point.x, point.y, point.z);
        }

        public Box inflate(double amount) {
            return new Box(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount);
        }

        /** Equivalent shape operation to the legacy AABB expand(rayDelta), then grow(border). */
        public Box expandTowards(Vec delta) {
            double nMinX = delta.x < 0 ? minX + delta.x : minX;
            double nMaxX = delta.x > 0 ? maxX + delta.x : maxX;
            double nMinY = delta.y < 0 ? minY + delta.y : minY;
            double nMaxY = delta.y > 0 ? maxY + delta.y : maxY;
            double nMinZ = delta.z < 0 ? minZ + delta.z : minZ;
            double nMaxZ = delta.z > 0 ? maxZ + delta.z : maxZ;
            return new Box(nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
        }

        public boolean contains(Vec p) {
            return p.x >= minX && p.x <= maxX
                    && p.y >= minY && p.y <= maxY
                    && p.z >= minZ && p.z <= maxZ;
        }

        /** Segment/AABB intersection using the same nearest-intercept semantics needed by the legacy selector. */
        public Optional<Vec> clip(Vec start, Vec end) {
            double tMin = 0.0;
            double tMax = 1.0;
            double[] s = { start.x, start.y, start.z };
            double[] d = { end.x - start.x, end.y - start.y, end.z - start.z };
            double[] min = { minX, minY, minZ };
            double[] max = { maxX, maxY, maxZ };

            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(d[axis]) < 1.0e-12) {
                    if (s[axis] < min[axis] || s[axis] > max[axis]) return Optional.empty();
                    continue;
                }
                double inv = 1.0 / d[axis];
                double t1 = (min[axis] - s[axis]) * inv;
                double t2 = (max[axis] - s[axis]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMax < tMin) return Optional.empty();
            }
            return Optional.of(start.add(new Vec(d[0], d[1], d[2]).scale(tMin)));
        }
    }

    public record Candidate<T>(
            T value,
            Vec position,
            Vec eyePosition,
            Box bounds,
            boolean collidable,
            float collisionBorder) {
        public Candidate {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(eyePosition, "eyePosition");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    @FunctionalInterface
    public interface LineOfSight<T> {
        boolean unobstructed(T candidate, Vec start, Vec candidateEye);
    }

    public static Box queryBox(Vec start, Box sourceBounds, Vec look, double maxDistance, double queryGrow) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(look, "look");
        Box base = sourceBounds == null ? Box.point(start).inflate(0.5) : sourceBounds;
        return base.expandTowards(look.scale(maxDistance)).inflate(queryGrow);
    }

    /**
     * Selects the closest candidate using BETA26's getPointedEntity rules.
     * Candidate iteration order is preserved when two hits have exactly equal distance.
     */
    public static <T> T selectEntity(
            Vec start,
            Vec look,
            double minDistance,
            double maxDistance,
            boolean allowNonCollidable,
            List<Candidate<T>> candidates,
            LineOfSight<T> lineOfSight) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(look, "look");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(lineOfSight, "lineOfSight");

        Vec end = start.add(look.scale(maxDistance));
        T best = null;
        double bestDistance = maxDistance;

        for (Candidate<T> candidate : candidates) {
            if (start.distanceTo(candidate.position()) < minDistance) continue;
            if (!allowNonCollidable && !candidate.collidable()) continue;
            if (!lineOfSight.unobstructed(candidate.value(), start, candidate.eyePosition())) continue;

            double border = Math.max(0.8, candidate.collisionBorder());
            Box bounds = candidate.bounds().inflate(border);
            if (bounds.contains(start)) {
                if (bestDistance >= 0.0) {
                    best = candidate.value();
                    bestDistance = 0.0;
                }
                continue;
            }

            Optional<Vec> intercept = bounds.clip(start, end);
            if (intercept.isEmpty()) continue;
            double distance = start.distanceTo(intercept.get());
            if (distance < bestDistance || bestDistance == 0.0) {
                // The bestDistance==0 branch intentionally mirrors the odd legacy control flow: an entity
                // containing the start may be replaced by a later intercepted entity.
                best = candidate.value();
                bestDistance = distance;
            }
        }
        return best;
    }

    public enum ScanTargetKind { ENTITY, BLOCK, SKY_OR_NULL }

    /** Thaumometer doScan priority: pointed entity first, then block ray trace, finally null for ScanSky. */
    public static ScanTargetKind targetKind(boolean hasPointedEntity, boolean hasBlockHit) {
        if (hasPointedEntity) return ScanTargetKind.ENTITY;
        if (hasBlockHit) return ScanTargetKind.BLOCK;
        return ScanTargetKind.SKY_OR_NULL;
    }
}
