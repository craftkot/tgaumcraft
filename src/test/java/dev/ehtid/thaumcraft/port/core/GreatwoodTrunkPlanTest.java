package dev.ehtid.thaumcraft.port.core;

import java.util.List;

public final class GreatwoodTrunkPlanTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        List<GreatwoodTrunkPlan.Cell> cells =
                GreatwoodTrunkPlan.create(-3, -64, 7, 3, -64, 320);
        check(cells.size() == 12, "complete 2x2 x 3 trunk");
        check(cells.getFirst().equals(new GreatwoodTrunkPlan.Cell(-3, -64, 7)),
                "first cell");
        check(cells.getLast().equals(new GreatwoodTrunkPlan.Cell(-2, -62, 8)),
                "last cell");
        check(cells.stream().distinct().count() == 12, "no duplicate coordinates");
        try {
            cells.add(new GreatwoodTrunkPlan.Cell(0, 0, 0));
            throw new AssertionError("mutable plan");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        int[][] invalid = {
            {0, 319, 0, 2}, {0, -65, 0, 1},
            {Integer.MAX_VALUE, 0, 0, 1},
            {0, 0, Integer.MAX_VALUE, 1},
            {0, 0, 0, 0}, {0, 0, 0, Integer.MAX_VALUE},
            {0, Integer.MAX_VALUE - 1, 0, 4}
        };
        for (int[] v : invalid) {
            check(GreatwoodTrunkPlan.create(v[0], v[1], v[2], v[3],
                    -64, 320).isEmpty(), "invalid volume must stage nothing");
        }
        check(GreatwoodTrunkPlan.create(0, 0, 0, 1, 10, 10).isEmpty(),
                "invalid build bounds");
        System.out.println("M12 GreatwoodTrunkPlan: OK (" + checks + " assertions)");
    }
}
