package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SilverwoodLegacyCanopyTest {
    private static int assertions;
    private static void check(boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError("Canopy assertion " + assertions);
    }
    public static void main(String[] args) {
        for (int height = 7; height <= 10; height++) {
            long seed = 912345L + height;
            Random actual = new Random(seed);
            Random reference = new Random(seed);
            List<String> writes = new ArrayList<>();
            List<String> expected = new ArrayList<>();
            int[] reads = {0};
            SilverwoodLegacyCanopy.place(8, 64, 8, height, actual,
                    (x, y, z) -> { reads[0]++; return ((x + y + z) & 1) == 0; },
                    (x, y, z) -> writes.add(x + ":" + y + ":" + z));
            int end = 64 + height + 3 + reference.nextInt(3);
            int eligible = 0;
            for (int layer = 64 + height - 5; layer <= end; layer++) {
                int cy = Math.max(64 + height - 3, Math.min(layer, 64 + height));
                for (int xx = 3; xx <= 13; xx++) {
                    for (int zz = 3; zz <= 13; zz++) {
                        double dist = (xx - 8) * (xx - 8) + (layer - cy) * (layer - cy)
                                + (zz - 8) * (zz - 8);
                        if (dist < 10 + reference.nextInt(8)) {
                            eligible++;
                            if (((xx + layer + zz) & 1) == 0) expected.add(xx + ":" + layer + ":" + zz);
                        }
                    }
                }
            }
            check(reads[0] == eligible);
            check(writes.equals(expected));
            check(actual.nextLong() == reference.nextLong());
            check(!writes.isEmpty());
        }
        // Check the actual RNG-driven canopy against the +/-4 chunk envelope.
        int outermost = 0;
        for (int h = 7; h <= 10; h++) {
            for (int seed = 0; seed < 32; seed++) {
                Random rng = new Random(10000L + 101L * h + seed);
                int[] farthest = {0};
                SilverwoodLegacyCanopy.place(-8, 64, -8, h, rng,
                        (x, y, z) -> true,
                        (x, y, z) -> {
                            int dx = Math.abs(x + 8);
                            int dz = Math.abs(z + 8);
                            check(dx <= 4 && dz <= 4);
                            check(SilverwoodLegacyFootprint.withinChunk(x, z, -1, -1));
                            farthest[0] = Math.max(farthest[0], Math.max(dx, dz));
                        });
                outermost = Math.max(outermost, farthest[0]);
            }
        }
        check(outermost == 4);

        // No replaceability reads does not imply no random draws.
        Random actual = new Random(99);
        Random expected = new Random(99);
        int[] writes = {0};
        SilverwoodLegacyCanopy.place(-8, 64, -8, 7, actual, (x, y, z) -> false,
                (x, y, z) -> writes[0]++);
        int end = 64 + 7 + 3 + expected.nextInt(3);
        for (int layer = 64 + 7 - 5; layer <= end; layer++)
            for (int xx = -13; xx <= -3; xx++)
                for (int zz = -13; zz <= -3; zz++) expected.nextInt(8);
        check(writes[0] == 0);
        check(actual.nextLong() == expected.nextLong());

        for (int height : new int[]{0, 6, 11, Integer.MAX_VALUE}) {
            Random rng = new Random(11);
            boolean failed = false;
            try {
                SilverwoodLegacyCanopy.place(8, 64, 8, height, rng, (x, y, z) -> true,
                        (x, y, z) -> {});
            } catch (IllegalArgumentException ex) { failed = true; }
            check(failed);
            check(rng.nextInt() == new Random(11).nextInt());
        }
        System.out.println("M12 SilverwoodLegacyCanopy: OK (" + assertions + " assertions)");
    }
}
