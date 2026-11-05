package dev.ehtid.thaumcraft.port.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaSourceOrder.Offset;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionSourceVolume.Facing;

/** BETA26 EssentiaHandler.getSources: directional bounds and stable distance ties. */
public final class InfusionSourceVolumeTest {
    private static int checks;

    private static void eq(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual))
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }

    private static Offset at(int x, int y, int z) {
        return new Offset(x, y, z);
    }

    private static void firstSix(Facing facing, Offset... expected) {
        eq(List.of(expected), LegacyInfusionSourceVolume.offsets(2, facing).subList(0, 6));
    }

    private static void facingChangesDepthAxisWithoutChangingDiscoveryTies() {
        firstSix(Facing.DOWN, at(-1,0,0), at(0,0,-1), at(0,-1,0),
                at(0,0,1), at(1,0,0), at(-1,0,-1));
        firstSix(Facing.UP, at(-1,0,0), at(0,0,-1), at(0,1,0),
                at(0,0,1), at(1,0,0), at(-1,0,-1));
        firstSix(Facing.NORTH, at(-1,0,0), at(0,-1,0), at(0,0,-1),
                at(0,1,0), at(1,0,0), at(-1,-1,0));
        firstSix(Facing.SOUTH, at(-1,0,0), at(0,-1,0), at(0,0,1),
                at(0,1,0), at(1,0,0), at(-1,-1,0));
        firstSix(Facing.WEST, at(0,-1,0), at(0,0,-1), at(-1,0,0),
                at(0,0,1), at(0,1,0), at(0,-1,-1));
        firstSix(Facing.EAST, at(0,-1,0), at(0,0,-1), at(1,0,0),
                at(0,0,1), at(0,1,0), at(0,-1,-1));
        // The whole distance-two shell tests ties between depth and transverse offsets.
        eq(List.of(at(-1,0,-1), at(-1,1,0), at(-1,0,1), at(0,1,-1),
                at(0,1,1), at(1,0,-1), at(1,1,0), at(1,0,1)),
                LegacyInfusionSourceVolume.offsets(2, Facing.UP).subList(5, 13));
    }

    private static void rangeOneIsOnlyThePlaneThroughTheMirror() {
        List<Offset> plane = List.of(at(-1,0,0), at(0,0,-1), at(0,0,1), at(1,0,0),
                at(-1,0,-1), at(-1,0,1), at(1,0,-1), at(1,0,1));
        eq(plane, LegacyInfusionSourceVolume.offsets(1, Facing.UP));
        eq(plane, LegacyInfusionSourceVolume.offsets(1, Facing.DOWN));
    }

    private static void bounds(Facing facing, Offset expectedMin, Offset expectedMax) {
        var offsets = LegacyInfusionSourceVolume.offsets(2, facing);
        eq(expectedMin, at(offsets.stream().mapToInt(Offset::x).min().orElseThrow(),
                offsets.stream().mapToInt(Offset::y).min().orElseThrow(),
                offsets.stream().mapToInt(Offset::z).min().orElseThrow()));
        eq(expectedMax, at(offsets.stream().mapToInt(Offset::x).max().orElseThrow(),
                offsets.stream().mapToInt(Offset::y).max().orElseThrow(),
                offsets.stream().mapToInt(Offset::z).max().orElseThrow()));
    }

    private static void transverseBoundsAreInclusiveAndFacingDepthIsExclusive() {
        bounds(Facing.DOWN, at(-2,-1,-2), at(2,0,2));
        bounds(Facing.UP, at(-2,0,-2), at(2,1,2));
        bounds(Facing.NORTH, at(-2,-2,-1), at(2,2,0));
        bounds(Facing.SOUTH, at(-2,-2,0), at(2,2,1));
        bounds(Facing.WEST, at(-1,-2,-2), at(0,2,2));
        bounds(Facing.EAST, at(0,-2,-2), at(1,2,2));
    }

    private static void completeUniqueVolumesAreSortedAndExcludeTheOrigin() {
        int[] ranges = {1, 2, 8, 32};
        int[] counts = {8, 49, 2311, 135199};
        for (Facing facing : Facing.values()) {
            for (int i = 0; i < ranges.length; i++) {
                var offsets = LegacyInfusionSourceVolume.offsets(ranges[i], facing);
                eq(counts[i], offsets.size());
                eq(counts[i], new HashSet<>(offsets).size());
                eq(false, offsets.contains(at(0,0,0)));
                int previous = -1;
                for (Offset offset : offsets) {
                    int distance = offset.distanceSquared();
                    if (distance < previous)
                        throw new AssertionError("Source priority is not nearest first: " + facing);
                    previous = distance;
                }
                checks++;
            }
        }
    }

    private static void nullFacingKeepsTheMatrixVolumeAndPriority() {
        for (int range : new int[]{1, 2, 8, 32})
            eq(LegacyEssentiaSourceOrder.nullFacing(range),
                    LegacyInfusionSourceVolume.offsets(range, null));
        eq(99, LegacyInfusionSourceVolume.offsets(2, null).size());
        eq(List.of(at(-1,0,0), at(0,0,-1), at(0,-1,0), at(0,1,0),
                at(0,0,1), at(1,0,0)),
                LegacyInfusionSourceVolume.offsets(2, null).subList(0, 6));
    }

    private static void invalidRangesAreRejectedForEveryFacing() {
        for (Facing facing : new Facing[]{null, Facing.DOWN, Facing.UP, Facing.NORTH,
                Facing.SOUTH, Facing.WEST, Facing.EAST}) {
            for (int range : new int[]{Integer.MIN_VALUE, -1, 0, 33, Integer.MAX_VALUE}) {
                checks++;
                try {
                    LegacyInfusionSourceVolume.offsets(range, facing);
                } catch (IllegalArgumentException expected) {
                    continue;
                }
                throw new AssertionError("Accepted source range " + range + " for " + facing);
            }
        }
    }

    private static void callersCannotChangeAnotherQuery() {
        for (Facing facing : new Facing[]{null, Facing.DOWN, Facing.UP, Facing.NORTH,
                Facing.SOUTH, Facing.WEST, Facing.EAST}) {
            var offsets = LegacyInfusionSourceVolume.offsets(2, facing);
            var snapshot = List.copyOf(offsets);
            checks++;
            try {
                offsets.set(0, at(0,0,0));
                throw new AssertionError("Source offsets are mutable");
            } catch (UnsupportedOperationException expected) {
                eq(snapshot, LegacyInfusionSourceVolume.offsets(2, facing));
            }
        }
    }

    public static void main(String[] args) {
        facingChangesDepthAxisWithoutChangingDiscoveryTies();
        rangeOneIsOnlyThePlaneThroughTheMirror();
        transverseBoundsAreInclusiveAndFacingDepthIsExclusive();
        completeUniqueVolumesAreSortedAndExcludeTheOrigin();
        nullFacingKeepsTheMatrixVolumeAndPriority();
        invalidRangesAreRejectedForEveryFacing();
        callersCannotChangeAnotherQuery();
        System.out.println("BETA26 infusion source volume: OK (" + checks + " assertions)");
    }
}
