package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SilverwoodLegacyTreeTest {
    private SilverwoodLegacyTreeTest() {}

    public static void main(String[] args) {
        int assertions = 0;
        for (int height = 7; height <= 10; height++) {
            for (long seed : new long[]{0L, 1L, 99L}) {
                List<String> expected = new ArrayList<>();
                List<String> actual = new ArrayList<>();
                Random reference = new Random(seed);
                SilverwoodLegacyCanopy.place(8, 70, 8, height, reference,
                        (x, y, z) -> (x + z) % 3 != 0,
                        (x, y, z) -> expected.add("L:" + x + ":" + y + ":" + z));
                SilverwoodLegacyLogs.place(8, 70, 8, height, reference,
                        (x, y, z, meta) -> expected.add("W:" + x + ":" + y + ":" + z + ":" + meta));
                Random integrated = new Random(seed);
                SilverwoodLegacyTree.place(8, 70, 8, height, integrated,
                        (x, y, z) -> (x + z) % 3 != 0,
                        (x, y, z) -> actual.add("L:" + x + ":" + y + ":" + z),
                        (x, y, z, meta) -> actual.add("W:" + x + ":" + y + ":" + z + ":" + meta));
                check(expected.equals(actual), "writes height=" + height + " seed=" + seed);
                check(reference.nextLong() == integrated.nextLong(), "RNG continuation");
                assertions += 2;
            }
        }
        // Simulate a rejected canopy write without changing legacy RNG traversal.
        // This is a pure-core callback test, NOT a WorldGenLevel.setBlock GameTest.
        for (int height = 7; height <= 10; height++) {
            for (long seed : new long[]{0L, 1L, 99L, 20261117L}) {
                Random baseline = new Random(seed);
                SilverwoodLegacyTree.place(8, 70, 8, height, baseline,
                        (x, y, z) -> true, (x, y, z) -> {},
                        (x, y, z, meta) -> {});
                Random rejected = new Random(seed);
                int[] attemptedLeaves = {0};
                int[] acceptedLeaves = {0};
                int[] attemptedLogs = {0};
                boolean[] failed = {false};
                SilverwoodLegacyTree.place(8, 70, 8, height, rejected,
                        (x, y, z) -> true,
                        (x, y, z) -> {
                            if (failed[0]) return;
                            attemptedLeaves[0]++;
                            if (attemptedLeaves[0] == 3) failed[0] = true;
                            else acceptedLeaves[0]++;
                        },
                        (x, y, z, meta) -> {
                            if (!failed[0]) attemptedLogs[0]++;
                        });
                check(failed[0], "failure injection not reached");
                check(attemptedLeaves[0] == 3, "extra leaf writes after rejection");
                check(acceptedLeaves[0] == 2, "wrong accepted leaf count");
                check(attemptedLogs[0] == 0, "trunk writes after rejected canopy");
                check(baseline.nextLong() == rejected.nextLong(),
                        "rejected canopy changed legacy RNG continuation");
                assertions += 5;
            }
        }
        Random invalid = new Random(7);
        int[] writes = {0};
        try {
            SilverwoodLegacyTree.place(0, 70, 0, 6, invalid, (x,y,z) -> true,
                    (x,y,z) -> writes[0]++, (x,y,z,m) -> writes[0]++);
            throw new AssertionError("invalid height accepted");
        } catch (IllegalArgumentException expected) {
            check(writes[0] == 0, "invalid height wrote blocks");
            check(invalid.nextLong() == new Random(7).nextLong(), "invalid height consumed RNG");
            assertions += 2;
        }
        System.out.println("M12 SilverwoodLegacyTree: OK (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
