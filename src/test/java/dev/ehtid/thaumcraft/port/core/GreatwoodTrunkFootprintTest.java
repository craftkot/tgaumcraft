package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;

public final class GreatwoodTrunkFootprintTest {
    private static int assertions;

    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        for (int x : new int[] {Integer.MIN_VALUE, -17, -1, 0, 15, Integer.MAX_VALUE - 1}) {
            for (int z : new int[] {Integer.MIN_VALUE, -17, -1, 0, 15, Integer.MAX_VALUE - 1}) {
                List<String> columns = new ArrayList<>();
                check(GreatwoodTrunkFootprint.visit(x, z,
                        (px, pz) -> columns.add(px + "," + pz)), "accepted footprint");
                check(columns.equals(List.of(x + "," + z, (x + 1) + "," + z,
                        x + "," + (z + 1), (x + 1) + "," + (z + 1))), "column order");
            }
        }
        for (int[] origin : new int[][] {
                {Integer.MAX_VALUE, 0}, {0, Integer.MAX_VALUE},
                {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
            List<String> columns = new ArrayList<>();
            check(!GreatwoodTrunkFootprint.visit(origin[0], origin[1],
                    (px, pz) -> columns.add(px + "," + pz)), "overflow rejected");
            check(columns.isEmpty(), "no partial emission");
        }
        try {
            GreatwoodTrunkFootprint.visit(0, 0, null);
            throw new AssertionError("null visitor accepted");
        } catch (NullPointerException expected) {
            assertions++;
        }
        System.out.println("M12 GreatwoodTrunkFootprint: OK (" + assertions + " assertions)");
    }
}
