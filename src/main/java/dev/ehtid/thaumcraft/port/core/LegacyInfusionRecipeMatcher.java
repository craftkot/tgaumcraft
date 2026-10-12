package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.function.Predicate;

/**
 * BETA26 InfusionRecipe.matches checks the research key, central Ingredient,
 * then uses Forge RecipeMatcher.findMatches for an unordered, one-to-one
 * assignment of actual pedestal stacks to ingredient predicates.
 *
 * This is the exact combinatorial core, not an invented playable altar:
 * NeoForge pedestal ItemStacks/Ingredient predicates must be wired later.
 */
public final class LegacyInfusionRecipeMatcher {
    private LegacyInfusionRecipeMatcher() {}

    /**
     * @return assignment supplied pedestal index -> required ingredient index,
     *         or null if any gate/ingredient fails.
     */
    public static <T> int[] match(PlayerKnowledgeData knowledge, String research,
                                  Predicate<T> centralIngredient, T centralStack,
                                  List<T> suppliedPedestals,
                                  List<Predicate<T>> requiredIngredients) {
        if (knowledge == null || !knowledge.isResearchKnown(research)
                || centralIngredient == null || !centralIngredient.test(centralStack))
            return null;
        return findMatches(suppliedPedestals, requiredIngredients);
    }

    public static <T> int[] findMatches(List<T> supplied,
                                        List<Predicate<T>> required) {
        if (supplied == null || required == null || supplied.size() != required.size())
            return null;
        int size = required.size();
        int[] pedestalToIngredient = new int[size];
        java.util.Arrays.fill(pedestalToIngredient, -1);
        for (int ingredient = 0; ingredient < size; ingredient++) {
            if (required.get(ingredient) == null
                    || !assign(ingredient, supplied, required,
                    pedestalToIngredient, new boolean[size]))
                return null;
        }
        // Forge RecipeMatcher.findMatches returns ret[inputIndex] = testIndex.
        return pedestalToIngredient;
    }

    private static <T> boolean assign(int ingredient, List<T> supplied,
                                       List<Predicate<T>> required,
                                       int[] pedestalToIngredient, boolean[] seen) {
        for (int pedestal = 0; pedestal < supplied.size(); pedestal++) {
            if (seen[pedestal] || !required.get(ingredient).test(supplied.get(pedestal)))
                continue;
            seen[pedestal] = true;
            int occupied = pedestalToIngredient[pedestal];
            if (occupied == -1 || assign(occupied, supplied, required,
                    pedestalToIngredient, seen)) {
                pedestalToIngredient[pedestal] = ingredient;
                return true;
            }
        }
        return false;
    }
}
