package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Original Thaumcraft 6.1.BETA26 CrystalModel#getQuads, verified against
 * original_BETA26.jar (SHA256 9425f864...).
 *
 * Each of the six source-era extended-state support flags independently
 * shuffles the same eight OBJ shard groups, but with a DIFFERENT seed.
 * The source MeshModel.rotate(angle, axis, translation) applies rotation
 * around the origin followed by the literal translation vector.
 */
public final class LegacyCrystalMeshSelection {
    private LegacyCrystalMeshSelection() {}
    private static final List<String> PARTS = List.of(
            "s014", "s015", "s016", "s017", "s018", "s019", "s024", "s026");

    public enum Face {
        UP(0), DOWN(5), EAST(10), WEST(15), NORTH(20), SOUTH(25);
        private final long seedOffset;
        Face(long seedOffset) { this.seedOffset = seedOffset; }
        public long seedOffset() { return seedOffset; }
    }

    public record Point(double x, double y, double z) {}

    public static List<String> originalGroups() { return PARTS; }

    /** Keep legacy single-seed selection for existing callers/parity tests. */
    public static List<String> select(int growth, long seed) {
        if (growth < 0 || growth > 3) throw new IllegalArgumentException("growth");
        ArrayList<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < PARTS.size(); i++) shuffled.add(i);
        Collections.shuffle(shuffled, new Random(seed));
        ArrayList<String> selected = new ArrayList<>();
        for (int i = 0; i <= growth; i++) selected.add(PARTS.get(shuffled.get(i)));
        return List.copyOf(selected);
    }

    /** Exact CrystalModel source seed offsets: 0, 5, 10, 15, 20, 25. */
    public static List<String> selectForFace(int growth, long positionSeed, Face face) {
        if (face == null) throw new IllegalArgumentException("face");
        return select(growth, positionSeed + face.seedOffset);
    }

    /**
     * Exact 90/180/270-degree rotation + translation operations in the
     * original CrystalModel bytecode:
     * UP:    Rx(180) + (0,1,1);
     * DOWN:  identity;
     * EAST:  Rx(90), Ry(270) + (1,1,0);
     * WEST:  Rx(90), Ry(90)  + (0,1,1);
     * NORTH: Rx(90) + (0,1,0);
     * SOUTH: Rx(90), Ry(180) + (1,1,1).
     *
     * MeshModel.rotate performs R*p + translation, not pivot rotation.
     */
    public static Point orient(Face face, Point p) {
        if (face == null || p == null) throw new IllegalArgumentException("face/point");
        double x = p.x(), y = p.y(), z = p.z();
        return switch (face) {
            case UP -> new Point(x, 1.0 - y, 1.0 - z);
            case DOWN -> p;
            case EAST -> new Point(1.0 - y, 1.0 - z, x);
            case WEST -> new Point(y, 1.0 - z, 1.0 - x);
            case NORTH -> new Point(x, 1.0 - z, y);
            case SOUTH -> new Point(1.0 - x, 1.0 - z, 1.0 - y);
        };
    }

    /** Direction/vertex normals get only the rotational component. */
    public static Point orientNormal(Face face, Point normal) {
        Point atOrigin = orient(face, new Point(0, 0, 0));
        Point transformed = orient(face, normal);
        return new Point(transformed.x() - atOrigin.x(),
                transformed.y() - atOrigin.y(), transformed.z() - atOrigin.z());
    }
}
