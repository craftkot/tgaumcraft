package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Source BETA26 getSurroundings stabilizer traversal, including legacy position hashing. */
public final class LegacyInfusionSurroundings {
    private LegacyInfusionSurroundings() {}

    /** Mirrors may report strength even when their eligibility callback rejects them. */
    public record Sample(String blockKey, float strength, boolean eligible, float symmetryPenalty) {
        public Sample {
            if (blockKey == null || blockKey.isBlank() || !Float.isFinite(strength)
                    || !Float.isFinite(symmetryPenalty)) throw new IllegalArgumentException("stabilizer sample");
        }
    }

    private static final Sample AIR = new Sample("minecraft:air", 0.1F, false, 0);

    /** Original scan order is x, z, then DESCENDING y; the central column is skipped. */
    public static List<LegacyInfusionMatrixLayout.Offset> scanOffsets() {
        var result = new ArrayList<LegacyInfusionMatrixLayout.Offset>(3168);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            if (x == 0 && z == 0) continue;
            for (int v = -3; v <= 7; v++) result.add(new LegacyInfusionMatrixLayout.Offset(x, -v, z));
        }
        return List.copyOf(result);
    }

    /**
     * BETA26 first inserted eligible positions into HashSet<Long>, using its
     * old x26/y12/z26 packing, then removed the first element and its mirror.
     * This is deliberately different from sorting offsets or modern asLong().
     */
    public static LegacyInfusionStability.Balance balance(int matrixX, int matrixY, int matrixZ,
            Function<LegacyInfusionMatrixLayout.Offset, Sample> world,
            BiPredicate<LegacyInfusionMatrixLayout.Offset, LegacyInfusionMatrixLayout.Offset> penalty) {
        if (world == null || penalty == null) throw new IllegalArgumentException("world/penalty");
        var pending = new HashSet<Long>();
        var offsets = new HashMap<Long, LegacyInfusionMatrixLayout.Offset>();
        var samples = new HashMap<LegacyInfusionMatrixLayout.Offset, Sample>();
        for (var at : scanOffsets()) {
            Sample sample = world.apply(at);
            if (sample == null) sample = AIR;
            samples.put(at, sample);
            if (sample.eligible()) {
                long key = legacyPositionKey(matrixX + at.x(), matrixY + at.y(), matrixZ + at.z());
                pending.add(key);
                offsets.put(key, at);
            }
        }
        float replenish = 0;
        Map<String, Integer> counts = new HashMap<>();
        var problem = new ArrayList<LegacyInfusionMatrixLayout.Offset>();
        while (!pending.isEmpty()) {
            long key = pending.iterator().next();
            var at = offsets.get(key);
            var mirror = new LegacyInfusionMatrixLayout.Offset(-at.x(), at.y(), -at.z());
            Sample one = samples.get(at), two = samples.getOrDefault(mirror, AIR);
            if (one.blockKey().equals(two.blockKey()) && one.strength() == two.strength()) {
                if (penalty.test(at, mirror)) {
                    replenish -= one.symmetryPenalty();
                    problem.add(at);
                } else {
                    int previous = counts.getOrDefault(one.blockKey(), 0);
                    replenish += LegacyInfusionStability.diminishing(one.strength(), previous);
                    counts.put(one.blockKey(), previous + 1);
                }
            } else {
                replenish -= Math.max(one.strength(), two.strength());
                problem.add(at);
            }
            pending.remove(legacyPositionKey(matrixX + mirror.x(), matrixY + mirror.y(), matrixZ + mirror.z()));
            pending.remove(key);
        }
        return new LegacyInfusionStability.Balance(replenish, problem);
    }

    /** Original 1.12 BlockPos.toLong; modern positions use a different bit layout. */
    public static long legacyPositionKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | (long) z & 0x3FFFFFFL;
    }
}
