package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/** The face-relative area and scan order of BETA26's public GolemHelper API. */
public record SealArea(SealPos seal, BlockPos size) implements Iterable<SealArea.BlockPos> {
    public SealArea {
        Objects.requireNonNull(seal, "seal");
        Objects.requireNonNull(size, "size");
        if (size.x < 1 || size.y < 1 || size.z < 1) {
            throw new IllegalArgumentException("Seal dimensions must be positive");
        }
        // Invalid external data must not wrap the original int scan arithmetic.
        volume(size, seal.face);
    }

    public record BlockPos(int x, int y, int z) {
        public BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }
    }

    /** Ordinals deliberately match Minecraft's DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    public enum Facing {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1),
        SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Facing(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int dx() { return dx; }
        public int dy() { return dy; }
        public int dz() { return dz; }
    }

    public record SealPos(BlockPos pos, Facing face) {
        public SealPos {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(face, "face");
        }
    }

    /** Half-open block bounds, directly usable as the six coordinates of an AABB. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(BlockPos pos) {
            return pos.x >= minX && pos.x < maxX && pos.y >= minY && pos.y < maxY
                    && pos.z >= minZ && pos.z < maxZ;
        }
    }

    /** ISealConfigArea starts at three across the face and one along its normal. */
    public static SealArea defaults(SealPos seal) {
        Facing face = seal.face;
        return new SealArea(seal, new BlockPos(face.dx == 0 ? 3 : 1,
                face.dy == 0 ? 3 : 1, face.dz == 0 ? 3 : 1));
    }

    private static long dimension(int size, int normal) {
        return 1L + (size - 1L) * (normal == 0 ? 2 : 1);
    }

    private static int volume(BlockPos size, Facing face) {
        return Math.toIntExact(Math.multiplyExact(Math.multiplyExact(
                dimension(size.x, face.dx), dimension(size.y, face.dy)),
                dimension(size.z, face.dz)));
    }

    public int count() { return volume(size, seal.face); }

    /** Matches getPosInArea, including wrapping a nonnegative scan counter. */
    public BlockPos positionAt(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative scan counter");
        Facing face = seal.face;
        int xx = Math.toIntExact(dimension(size.x, face.dx));
        int yy = Math.toIntExact(dimension(size.y, face.dy));
        int zz = Math.toIntExact(dimension(size.z, face.dz));
        int qx = face.dx != 0 ? face.dx : 1;
        int qy = face.dy != 0 ? face.dy : 1;
        int qz = face.dz != 0 ? face.dz : 1;
        int y = qy * ((count / zz) / xx) % yy + face.dy;
        int x = qx * (count / zz) % xx + face.dx;
        int z = qz * count % zz + face.dz;
        return seal.pos.offset(x - (face.dx == 0 ? xx / 2 : 0),
                y - (face.dy == 0 ? yy / 2 : 0), z - (face.dz == 0 ? zz / 2 : 0));
    }

    public Bounds bounds() {
        Facing face = seal.face;
        int x = seal.pos.x + face.dx;
        int y = seal.pos.y + face.dy;
        int z = seal.pos.z + face.dz;
        int ex = (size.x - 1) * face.dx;
        int ey = (size.y - 1) * face.dy;
        int ez = (size.z - 1) * face.dz;
        int gx = face.dx == 0 ? size.x - 1 : 0;
        int gy = face.dy == 0 ? size.y - 1 : 0;
        int gz = face.dz == 0 ? size.z - 1 : 0;
        return new Bounds(x + Math.min(0, ex) - gx, y + Math.min(0, ey) - gy,
                z + Math.min(0, ez) - gz, x + 1 + Math.max(0, ex) + gx,
                y + 1 + Math.max(0, ey) + gy, z + 1 + Math.max(0, ez) + gz);
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return new Iterator<>() {
            private int cursor;

            public boolean hasNext() { return cursor < count(); }

            public BlockPos next() {
                if (!hasNext()) throw new NoSuchElementException();
                return positionAt(cursor++);
            }
        };
    }
}
