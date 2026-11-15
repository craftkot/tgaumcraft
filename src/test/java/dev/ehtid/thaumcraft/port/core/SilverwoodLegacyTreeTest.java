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
