package dev.ehtid.thaumcraft.port.core;

/** BETA26 BlockCrystal#getBoundingBox, driven by the six original support-face flags. */
public final class LegacyCrystalOutlineBounds {
    private LegacyCrystalOutlineBounds() {}

    public record Bounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        public Bounds {
            if (!unit(minX) || !unit(minY) || !unit(minZ)
                    || !unit(maxX) || !unit(maxY) || !unit(maxZ)
                    || minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("invalid unit-cube bounds");
            }
        }
        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
        }
    }

    private static final Bounds FULL = new Bounds(0, 0, 0, 1, 1, 1);

    /**
     * Source BETA26 counts UP, DOWN, EAST, WEST, SOUTH and NORTH attachments.
     * More than one attachment (and the defensive zero-face case) uses a full
     * cube; exactly one attachment selects the corresponding half-cube.
     */
    public static Bounds fromSupportFaces(boolean up, boolean down, boolean east,
                                          boolean west, boolean south, boolean north) {
        int count = (up ? 1 : 0) + (down ? 1 : 0) + (east ? 1 : 0)
                + (west ? 1 : 0) + (south ? 1 : 0) + (north ? 1 : 0);
        if (count != 1) return FULL;
        if (up) return new Bounds(0, 0.5D, 0, 1, 1, 1);
        if (down) return new Bounds(0, 0, 0, 1, 0.5D, 1);
        if (east) return new Bounds(0.5D, 0, 0, 1, 1, 1);
        if (west) return new Bounds(0, 0, 0, 0.5D, 1, 1);
        if (south) return new Bounds(0, 0, 0.5D, 1, 1, 1);
        return new Bounds(0, 0, 0, 1, 1, 0.5D); // NORTH
    }
}
