package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thaumcraft BETA26 EssentiaHandler.getSources(null facing), extracted from
 * original 1.12.2 bytecode. Null facing is used by the infusion matrix.
 *
 * The original enumerates x=-r..r, z=-r..r, y=-r..r-1 (yes, asymmetric Y),
 * excludes the origin, finds IAspectSource tiles and sorts by squared
 * Euclidean distance. Equal-distance entries retain discovery order.
 *
 * Source eligibility is checked against the world by the NeoForge bridge;
 * these immutable offsets are only the original spatial priority.
 */
public final class LegacyEssentiaSourceOrder {
    private LegacyEssentiaSourceOrder() {}

    public record Offset(int x, int y, int z) {
        public int distanceSquared() { return x * x + y * y + z * z; }
    }

    private record Candidate(Offset offset, int discoveryIndex) {}

    private static final ConcurrentHashMap<Integer, List<Offset>> CACHE =
            new ConcurrentHashMap<>();

    public static List<Offset> nullFacing(int range) {
        if (range <= 0 || range > 32)
            throw new IllegalArgumentException("Essentia source range must be 1..32");
        return CACHE.computeIfAbsent(range, LegacyEssentiaSourceOrder::generate);
    }

    private static List<Offset> generate(int range) {
        List<Candidate> found = new ArrayList<>();
        int ordinal = 0;
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -range; y < range; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    found.add(new Candidate(new Offset(x, y, z), ordinal++));
                }
            }
        }
        found.sort(Comparator.comparingInt((Candidate c) -> c.offset().distanceSquared())
                .thenComparingInt(Candidate::discoveryIndex));
        return List.copyOf(found.stream().map(Candidate::offset).toList());
    }
}
