package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Pure 2x2 Greatwood trunk footprint, with no world writes or chunk loads.
 * This models only the horizontal trunk columns; branches and leaf nodes
 * require separate source-parity work.
 */
public final class GreatwoodTrunkFootprint {
    private GreatwoodTrunkFootprint() {}

    @FunctionalInterface
    public interface ColumnVisitor {
        void accept(int x, int z);
    }

    /**
     * Enumerates all four columns only if their coordinates are representable.
     * Rejecting the whole footprint before invoking the visitor avoids a
     * partially emitted trunk when x+1 or z+1 overflows.
     */
    public static boolean visit(int x, int z, ColumnVisitor visitor) {
        Objects.requireNonNull(visitor, "visitor");
        if (x == Integer.MAX_VALUE || z == Integer.MAX_VALUE) return false;
        visitor.accept(x, z);
        visitor.accept(x + 1, z);
        visitor.accept(x, z + 1);
        visitor.accept(x + 1, z + 1);
        return true;
    }
}
