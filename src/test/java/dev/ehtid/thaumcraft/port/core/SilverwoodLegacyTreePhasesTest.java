package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SilverwoodLegacyTreePhasesTest {
    private static int assertions;
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Silverwood phases assertion " + assertions);
    }

    public static void main(String[] args) {
        for (int height = 7; height <= 10; height++) {
            final int h = height;
            Random actual = new Random(20261115L + height);
            Random expected = new Random(20261115L + height);
            List<String> writes = new ArrayList<>();
            List<String> manual = new ArrayList<>();
            int[] canopyCalls = {0};
            SilverwoodLegacyTreePhases.place(8, 64, 8, height, actual,
                    (x, y, z, size, rng) -> {
                        canopyCalls[0]++;
                        check(x == 8 && y == 64 && z == 8 && size == h);
                        for (int i = 0; i < 17; i++) writes.add("leaf:" + rng.nextInt(37));
                    },
                    (x, y, z, meta) -> writes.add("log:" + x + "," + y + "," + z + ":" + meta));
            for (int i = 0; i < 17; i++) manual.add("leaf:" + expected.nextInt(37));
            SilverwoodLegacyLogs.place(8, 64, 8, height, expected,
                    (x, y, z, meta) -> manual.add("log:" + x + "," + y + "," + z + ":" + meta));
            check(canopyCalls[0] == 1);
            check(writes.equals(manual));
            check(actual.nextLong() == expected.nextLong());
            check(writes.get(0).startsWith("leaf:"));
            check(writes.stream().anyMatch(s -> s.startsWith("log:")));
        }

        for (int height : new int[]{-1, 0, 6, 11, Integer.MAX_VALUE}) {
            int[] calls = {0};
            Random rng = new Random(42);
            boolean rejected = false;
            try {
                SilverwoodLegacyTreePhases.place(8, 64, 8, height, rng,
                        (x, y, z, h, random) -> calls[0]++,
                        (x, y, z, meta) -> calls[0]++);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected);
            check(calls[0] == 0);
            check(rng.nextInt() == new Random(42).nextInt());
        }
        for (int missing = 0; missing < 3; missing++) {
            Random rng = new Random(42);
            int[] calls = {0};
            boolean rejected = false;
            try {
                SilverwoodLegacyTreePhases.place(8, 64, 8, 7,
                        missing == 0 ? null : rng,
                        missing == 1 ? null : (x, y, z, h, random) -> calls[0]++,
                        missing == 2 ? null : (x, y, z, meta) -> calls[0]++);
            } catch (NullPointerException expected) {
                rejected = true;
            }
            check(rejected);
            check(calls[0] == 0);
            check(rng.nextInt() == new Random(42).nextInt());
        }
        System.out.println("M12 SilverwoodLegacyTreePhases: OK (" + assertions + " assertions)");
    }
}
