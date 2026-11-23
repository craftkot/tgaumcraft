package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;

public final class GreatwoodTrunkVolumeTest {
    private static int checks;
    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        List<String> cells = new ArrayList<>();
        check(GreatwoodTrunkVolume.visit(-1, -64, 15, 3, -64, 320,
                (x,y,z) -> cells.add(x + "," + y + "," + z)), "valid volume");
        check(cells.size() == 12, "four cells per level");
        check(cells.get(0).equals("-1,-64,15"), "first block");
        check(cells.get(11).equals("0,-62,16"), "last block");
        int[][] bad = {
            {0, 320, 0, 1}, {0, 319, 0, 2},
            {0, -65, 0, 1}, {0, 0, 0, 0},
            {Integer.MAX_VALUE, 0, 0, 1},
            {0, 0, Integer.MAX_VALUE, 1},
            {0, Integer.MAX_VALUE - 1, 0, 3}
        };
        for (int[] args4 : bad) {
            cells.clear();
            check(!GreatwoodTrunkVolume.visit(args4[0], args4[1], args4[2],
                    args4[3], -64, Integer.MAX_VALUE,
                    (x,y,z) -> cells.add("write")), "invalid preflight");
            check(cells.isEmpty(), "no partial emission");
        }
        check(!GreatwoodTrunkVolume.visit(0, 0, 0, 1, 10, 10,
                (x,y,z) -> {}), "invalid build limits");
        try {
            GreatwoodTrunkVolume.visit(0, 0, 0, 1, -64, 320, null);
            throw new AssertionError("null visitor accepted");
        } catch (NullPointerException expected) {
            checks++;
        }
        System.out.println("M12 GreatwoodTrunkVolume: OK (" + checks + " assertions)");
    }
}
