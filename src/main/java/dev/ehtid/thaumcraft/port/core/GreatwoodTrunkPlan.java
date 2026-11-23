package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, loader-neutral preflight plan for a Greatwood 2x2 trunk.
 * No WorldGenLevel writes occur while building this plan.
 * The caller still owns replaceability checks, snapshotting and rollback.
 */
public final class GreatwoodTrunkPlan {
    private GreatwoodTrunkPlan() {}

    public record Cell(int x, int y, int z) {}

    /**
     * Returns an empty list when the volume cannot fit. Valid plans are
     * complete and ordered bottom-to-top, four cells per layer.
     */
    public static List<Cell> create(int x, int baseY, int z, int trunkLength,
                                    int minBuildY, int maxBuildYExclusive) {
        // Prevent allocation overflow and reject invalid bounds before visiting.
        if (trunkLength <= 0 || (long) trunkLength * 4L > Integer.MAX_VALUE\n                || minBuildY >= maxBuildYExclusive || baseY < minBuildY\n                || (long) baseY + trunkLength > maxBuildYExclusive\n                || x == Integer.MAX_VALUE || z == Integer.MAX_VALUE) {
            return List.of();
        }
        ArrayList<Cell> cells = new ArrayList<>(trunkLength * 4);
        boolean valid = GreatwoodTrunkVolume.visit(x, baseY, z, trunkLength,
                minBuildY, maxBuildYExclusive,
                (cx, cy, cz) -> cells.add(new Cell(cx, cy, cz)));
        return valid ? List.copyOf(cells) : List.of();
    }
}
