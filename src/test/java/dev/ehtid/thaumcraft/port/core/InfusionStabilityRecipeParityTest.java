package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;

/** Source masks/prices and exhaustive shapeless positions, with contaminant rejection. */
public final class InfusionStabilityRecipeParityTest {
    private static int checks;

    private static void eq(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", actual " + actual);
    }

    private static boolean matches(LegacyInfusionStabilityRecipes.Kind kind, char[] grid) {
        return LegacyInfusionStabilityRecipes.matches(kind,
                (slot, token) -> grid[slot] == token, slot -> grid[slot] == ' ');
    }

    public static void main(String[] args) {
        var speed = LegacyInfusionStabilityRecipes.Kind.SPEED;
        var cost = LegacyInfusionStabilityRecipes.Kind.COST;
        var inlay = LegacyInfusionStabilityRecipes.Kind.INLAY;
        var device = LegacyInfusionStabilityRecipes.Kind.STABILIZER;
        eq(500, speed.vis); eq(17, speed.crystalMask); eq("INFUSIONBOOST", speed.research);
        eq(500, cost.vis); eq(37, cost.crystalMask); eq("INFUSIONBOOST", cost.research);
        eq(25, inlay.vis); eq(4, inlay.crystalMask); eq(2, inlay.outputCount);
        eq("INFUSIONSTABLE", inlay.research);
        eq(250, device.vis); eq(44, device.crystalMask); eq("INFUSIONSTABLE", device.research);
        var glass = LegacyInfusionStabilityRecipes.Kind.MIRRORED_GLASS;
        eq(50, glass.vis); eq(20, glass.crystalMask); eq("BASEARTIFICE", glass.research);
        eq(375, LegacyInfusionStabilityRecipes.discountedVis(speed, .25F));
        eq(0, LegacyInfusionStabilityRecipes.discountedVis(speed, 1.1F));

        var kinds = new LegacyInfusionStabilityRecipes.Kind[]{speed, cost, device};
        var source = new String[]{"SNSNGNSNS", "SASAGASAS", "SRSBVBIMI"};
        for (int i = 0; i < kinds.length; i++) {
            char[] grid = source[i].toCharArray();
            eq(true, matches(kinds[i], grid));
            for (int slot = 0; slot < 9; slot++) {
                char old = grid[slot];
                grid[slot] = ' ';
                eq(false, matches(kinds[i], grid));
                grid[slot] = 'X';
                eq(false, matches(kinds[i], grid));
                grid[slot] = old;
            }
        }
        for (int red = 0; red < 9; red++) for (int gold = 0; gold < 9; gold++) {
            if (red == gold) continue;
            char[] grid = new char[9]; Arrays.fill(grid, ' ');
            grid[red] = 'R'; grid[gold] = 'G';
            eq(true, matches(inlay, grid));
            eq(true, matches(glass, grid));
            for (int extra = 0; extra < 9; extra++) if (extra != red && extra != gold) {
                grid[extra] = 'X';
                eq(false, matches(inlay, grid));
                eq(false, matches(glass, grid));
                grid[extra] = ' ';
            }
        }
        eq(false, matches(inlay, "R        ".toCharArray()));
        eq(false, matches(inlay, "GG       ".toCharArray()));
        System.out.println("BETA26 stability arcane recipes: OK (" + checks + " assertions)");
    }
}
