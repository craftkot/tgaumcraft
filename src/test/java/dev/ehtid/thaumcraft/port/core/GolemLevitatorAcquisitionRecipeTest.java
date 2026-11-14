package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.GolemComponentRecipes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Original ConfigRecipes Levitator3805..3961, independent source recipe fixture. */
public final class GolemLevitatorAcquisitionRecipeTest {
    private static int assertions;
    public static void main(String[] args) {
        var recipe = Arrays.stream(GolemComponentRecipes.Recipe.values()).filter(r -> r.name().equals("LEVITATOR"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing original arcane levitator acquisition"));
        same("LEVITATOR", recipe.research); same(35, recipe.vis); same(1, recipe.crystalMask);
        same(26, recipe.discountedVis(.25F));
        var golden = List.of("WOODEN_PLANK", "THAUMIUM_PLATE", "WOODEN_PLANK", "IRON_PLATE", "NITOR",
                "IRON_PLATE", "WOODEN_PLANK", "SIMPLE_MECHANISM", "WOODEN_PLANK");
        same(golden, recipe.pattern().stream().map(Enum::name).toList());
        int[] masks = golden.stream().map(GolemComponentRecipes.Ingredient::valueOf).mapToInt(GolemComponentRecipes::bit).toArray();
        same(true, recipe.matches(masks));
        for (int slot=0;slot<9;slot++) {
            int original=masks[slot];
            for(var ingredient:GolemComponentRecipes.Ingredient.values()) {
                masks[slot]=GolemComponentRecipes.bit(ingredient);
                same(masks[slot]==original, recipe.matches(masks));
            }
            masks[slot]=original;
        }
        System.out.println("BETA26 levitator acquisition: OK (" + assertions + " assertions)");
    }
    private static void same(Object expected,Object actual) {
        assertions++; if(!Objects.equals(expected,actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
