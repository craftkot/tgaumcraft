package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SilverwoodLegacyLogsTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Silverwood logs assertion " + assertions);
    }

    public static void main(String[] args) {
        List<String> writes = new ArrayList<>();
        Random rolls = new Random(1234L);
        SilverwoodLegacyLogs.place(8, 64, 8, 7, rolls,
                (x,y,z,meta) -> writes.add(x + "," + y + "," + z + ":" + meta));
        for (int dy = 0; dy < 7; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 || Math.abs(dx) == 1) {
                    check(writes.contains((8 + dx) + "," + (64 + dy) + ",8:1"));
                }
            }
            check(writes.contains("8," + (64 + dy) + ",7:1"));
            check(writes.contains("8," + (64 + dy) + ",9:1"));
        }
        check(writes.contains("8,71,8:1"));
        check(writes.contains("6,63,8:1"));
        check(writes.contains("10,63,8:1"));
        check(writes.contains("8,63,6:1"));
        check(writes.contains("8,63,10:1"));
        check(writes.contains("6,64,8:0"));
        check(writes.contains("8,64,6:2"));
        check(writes.contains("6,67,8:0"));
        check(writes.contains("8,67,6:2"));
        check(writes.contains("7,67,7:1"));
        check(writes.contains("9,67,9:1"));

        List<String> replay = new ArrayList<>();
        SilverwoodLegacyLogs.place(8,64,8,7,new Random(1234L),
                (x,y,z,meta) -> replay.add(x + "," + y + "," + z + ":" + meta));
        check(writes.equals(replay));
        check(writes.stream().allMatch(s -> {
            String[] p = s.substring(0,s.indexOf(':')).split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            return x >= 3 && x <= 13 && z >= 3 && z <= 13 && y >= 63 && y <= 76;
        }));
        boolean rejected = false;
        try { SilverwoodLegacyLogs.place(8,64,8,11,new Random(1), (x,y,z,m)->{}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
        // Feature success compares attempted writes with unique successful positions.
        // Duplicate log coordinates would make a complete tree look like a failure.
        // Exercise every supported height and a range of branch RNG outcomes.
        for (int height = 7; height <= 10; height++) {
            for (long seed = 0; seed < 32; seed++) {
                List<String> coordinates = new ArrayList<>();
                SilverwoodLegacyLogs.place(8, 64, 8, height, new Random(seed),
                        (x, y, z, meta) -> coordinates.add(x + "," + y + "," + z));
                check(coordinates.size() == new java.util.HashSet<>(coordinates).size());
            }
        }
        // Legacy skeleton coordinates must be translation-invariant across negative
        // block coordinates and chunk boundaries; seed-dependent branch rolls
        // must not change when the origin is moved.
        for (int height = 7; height <= 10; height++) {
            for (long seed = 0; seed < 16; seed++) {
                List<String> positive = new ArrayList<>();
                List<String> negative = new ArrayList<>();
                SilverwoodLegacyLogs.place(8, 64, 8, height, new Random(seed),
                        (x, y, z, meta) -> positive.add((x - 8) + "," + (y - 64) + "," + (z - 8) + ":" + meta));
                SilverwoodLegacyLogs.place(-24, 64, -8, height, new Random(seed),
                        (x, y, z, meta) -> negative.add((x + 24) + "," + (y - 64) + "," + (z + 8) + ":" + meta));
                check(positive.equals(negative));
            }
        }
        // A translated placement must leave the caller's RNG at the same state.
        // This matters when multiple features share a chunk-generation stream.
        for (int height = 7; height <= 10; height++) {
            for (long seed = 0; seed < 32; seed++) {
                Random positiveRolls = new Random(seed);
                Random negativeRolls = new Random(seed);
                SilverwoodLegacyLogs.place(8, 64, 8, height, positiveRolls, (x, y, z, meta) -> {});
                SilverwoodLegacyLogs.place(-24, 64, -8, height, negativeRolls, (x, y, z, meta) -> {});
                check(positiveRolls.nextLong() == negativeRolls.nextLong());
            }
        }
        System.out.println("M12 SilverwoodLegacyLogs: OK (" + assertions + " assertions)");
    }
}
