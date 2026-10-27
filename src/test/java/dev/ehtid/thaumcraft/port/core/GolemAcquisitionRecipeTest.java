package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.GolemComponentRecipes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Independent BETA26 ConfigRecipes modvision/modaggression arcane recipe fixtures. */
public final class GolemAcquisitionRecipeTest {
    private static int assertions;
    private GolemAcquisitionRecipeTest() {}

    public static void main(String[] args) {
        checkModule("VISION_MODULE", "GOLEMVISION", 4, "B BE EPGP");
        checkModule("AGGRESSION_MODULE", "SEALGUARD", 2, " R RTRPGP");
        checkComplexMechanism();
        System.out.println("BETA26 golem module acquisition: OK (" + assertions + " assertions)");
    }

    private static void checkComplexMechanism() {
        var recipe = Arrays.stream(GolemComponentRecipes.Recipe.values()).filter(r -> r.name().equals("COMPLEX_MECHANISM"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing original arcane recipe mechanism_complex"));
        same("BASEARTIFICE", recipe.research);
        same(50, recipe.vis);
        same(6, recipe.crystalMask); // Ignis1+Aqua1, not Ordo or Machina.
        var expected = List.of("EMPTY", "SIMPLE_MECHANISM", "EMPTY", "THAUMIUM_PLATE", "PISTON",
                "THAUMIUM_PLATE", "EMPTY", "SIMPLE_MECHANISM", "EMPTY");
        same(expected, recipe.pattern().stream().map(Enum::name).toList());
        int[] grid = masks(expected);
        same(true, recipe.matches(grid));
        for (int slot = 0; slot < 9; slot++) {
            int original = grid[slot];
            for (var ingredient : GolemComponentRecipes.Ingredient.values()) {
                grid[slot] = GolemComponentRecipes.bit(ingredient);
                same(grid[slot] == original, recipe.matches(grid));
            }
            grid[slot] = original;
        }
        var knowledge = new PlayerKnowledgeData();
        knowledge.addResearch("MINDBIOTHAUMIC");
        same(false, knowledge.isResearchKnown(recipe.research));
        knowledge.addResearch("BASEARTIFICE");
        same(true, knowledge.isResearchKnown(recipe.research));
    }

    private static void checkModule(String name, String research, int crystalMask, String golden) {
        var recipe = Arrays.stream(GolemComponentRecipes.Recipe.values()).filter(r -> r.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("Missing original arcane recipe " + name));
        same(research, recipe.research);
        same(50, recipe.vis);
        same(crystalMask, recipe.crystalMask);
        same(37, recipe.discountedVis(.25F));
        same(0, recipe.discountedVis(1));
        var expected = golden.chars().mapToObj(c -> ingredient((char) c)).toList();
        same(expected, recipe.pattern().stream().map(Enum::name).toList());
        int[] grid = masks(expected);
        same(true, recipe.matches(grid));
        same(false, recipe.matches(null));
        same(false, recipe.matches(new int[8]));
        for (int slot = 0; slot < 9; slot++) {
            for (var replacement : GolemComponentRecipes.Ingredient.values()) {
                int original = grid[slot];
                grid[slot] = GolemComponentRecipes.bit(replacement);
                same(grid[slot] == original, recipe.matches(grid));
                grid[slot] = original;
            }
        }
        var knowledge = new PlayerKnowledgeData();
        same(false, knowledge.isResearchKnown(recipe.research));
        knowledge.addResearch(name.equals("VISION_MODULE") ? "GOLEMCOMBATADV" : "GOLEMVISION");
        same(false, knowledge.isResearchKnown(recipe.research));
        knowledge.addResearch(research);
        same(true, knowledge.isResearchKnown(recipe.research));
    }

    private static String ingredient(char symbol) {
        return switch (symbol) {
            case 'B' -> "GLASS_BOTTLE";
            case 'E' -> "FERMENTED_SPIDER_EYE";
            case 'P' -> "BRASS_PLATE";
            case 'G' -> "SIMPLE_MECHANISM";
            case 'R' -> "GLASS_PANE";
            case 'T' -> "BLAZE_POWDER";
            default -> "EMPTY";
        };
    }

    private static int[] masks(List<String> ingredients) {
        return ingredients.stream().map(GolemComponentRecipes.Ingredient::valueOf)
                .mapToInt(GolemComponentRecipes::bit).toArray();
    }

    private static void same(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
