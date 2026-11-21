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
        // A replaceability read can fail before any leaf write. Do not enter
        // the log phase or silently swallow the exception.
        for (int height = 7; height <= 10; height++) {
            Random failedRead = new Random(20261121L);
            int[] leafWrites = {0};
            int[] logWrites = {0};
            int[] reads = {0};
            try {
                SilverwoodLegacyTree.place(8, 70, 8, height, failedRead,
                        (x, y, z) -> {
                            reads[0]++;
                            throw new IllegalStateException("unavailable chunk read");
                        },
                        (x, y, z) -> leafWrites[0]++,
                        (x, y, z, meta) -> logWrites[0]++);
                throw new AssertionError("failed replaceability read was swallowed");
            } catch (IllegalStateException expected) {
                check("unavailable chunk read".equals(expected.getMessage()),
                        "wrong replaceability failure");
                check(reads[0] == 1, "continued after failed read");
                check(leafWrites[0] == 0, "leaf written after failed read");
                check(logWrites[0] == 0, "log phase entered after failed read");
                assertions += 4;
            }
        }
        // A log-phase failure must propagate immediately; do not continue writing
        // later trunk/root positions after a failed world write. Pure-core only.
        for (int height = 7; height <= 10; height++) {
            int[] attemptedLogs = {0};
            int[] canopyWrites = {0};
            try {
                SilverwoodLegacyTree.place(8, 70, 8, height, new Random(20261121L),
                        (x, y, z) -> true,
                        (x, y, z) -> canopyWrites[0]++,
                        (x, y, z, meta) -> {
                            attemptedLogs[0]++;
                            throw new IllegalStateException("failed log write");
                        });
                throw new AssertionError("failed log write was swallowed");
            } catch (IllegalStateException expected) {
                check("failed log write".equals(expected.getMessage()),
                        "wrong log failure");
                check(attemptedLogs[0] == 1, "continued writing logs after failure");
                check(canopyWrites[0] > 0, "log phase started without canopy");
                assertions += 3;
            }
        }
        // Invalid callback contracts must fail before consuming the RNG or writing blocks.
        for (int missing = 0; missing < 4; missing++) {
            Random untouched = new Random(20261121L);
            int[] callbackWrites = {0};
            try {
                SilverwoodLegacyTree.place(8, 70, 8, 7,
                        missing == 0 ? null : untouched,
                        missing == 1 ? null : (x, y, z) -> true,
                        missing == 2 ? null : (x, y, z) -> callbackWrites[0]++,
                        missing == 3 ? null : (x, y, z, meta) -> callbackWrites[0]++);
                throw new AssertionError("missing dependency accepted: " + missing);
            } catch (NullPointerException expected) {
                check(callbackWrites[0] == 0, "null dependency wrote blocks");
                check(untouched.nextLong() == new Random(20261121L).nextLong(),
                        "null dependency consumed RNG");
                assertions += 2;
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
