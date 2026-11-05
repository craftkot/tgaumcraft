package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaSourceOrder.Offset;

/**
 * BETA26 EssentiaHandler.getSources directional geometry for essentia mirrors.
 * The two transverse coordinates run -range..range, while facing depth runs
 * 0..range-1. The origin is excluded. Equal-distance sources keep the original
 * transverse/transverse/depth discovery order.
 *
 * World source eligibility and linked-mirror exclusions belong to the bridge;
 * these cached immutable offsets describe only spatial priority.
 */
public final class LegacyInfusionSourceVolume {
    private LegacyInfusionSourceVolume() {}

    public enum Facing { DOWN, UP, NORTH, SOUTH, WEST, EAST }

    private record Volume(int range, Facing facing) {}

    private static final ConcurrentHashMap<Volume, List<Offset>> CACHE =
            new ConcurrentHashMap<>();

    public static List<Offset> offsets(int range, Facing facing) {
        if (facing == null) return LegacyEssentiaSourceOrder.nullFacing(range);
        if (range <= 0 || range > 32)
            throw new IllegalArgumentException("Essentia source range must be 1..32");
        return CACHE.computeIfAbsent(new Volume(range, facing), LegacyInfusionSourceVolume::generate);
    }

    private static List<Offset> generate(Volume volume) {
        int range = volume.range();
        List<Offset> found = new ArrayList<>((2 * range + 1) * (2 * range + 1) * range - 1);
        for (int a = -range; a <= range; a++) {
            for (int b = -range; b <= range; b++) {
                for (int depth = 0; depth < range; depth++) {
                    if (a == 0 && b == 0 && depth == 0) continue;
                    found.add(switch (volume.facing()) {
                        case DOWN -> new Offset(a, -depth, b);
                        case UP -> new Offset(a, depth, b);
                        case NORTH -> new Offset(a, b, -depth);
                        case SOUTH -> new Offset(a, b, depth);
                        case WEST -> new Offset(-depth, a, b);
                        case EAST -> new Offset(depth, a, b);
                    });
                }
            }
        }
        // List.sort is stable, matching the original strictly-nearer insertion.
        found.sort(Comparator.comparingInt(Offset::distanceSquared));
        return List.copyOf(found);
    }
}
