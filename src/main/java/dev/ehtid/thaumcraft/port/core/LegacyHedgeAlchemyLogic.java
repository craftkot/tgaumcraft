package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/**
 * BETA26 ConfigRecipes.initializeAlchemyRecipes:
 * - thaumcraft:hedge_tallow HEDGEALCHEMY@1, catalyst rotten flesh,
 *   product ItemsTC.tallow, required Ignis 1.
 * - thaumcraft:hedge_leather HEDGEALCHEMY@1, catalyst rotten flesh,
 *   product vanilla leather, required Aer 3 + Bestia 3.
 *
 * ThaumcraftCraftingManager.findMatchingCrucibleRecipe selects the
 * eligible recipe with the greatest cost, NOT registration order alone.
 * On equal scores its strict > comparison keeps the earliest match.
 */
public final class LegacyHedgeAlchemyLogic {
    private LegacyHedgeAlchemyLogic() {}

    public enum Output { TALLOW, LEATHER }

    public record Recipe(String id, String research, Output output,
                         AspectListData required) {
        public int score() { return required.visSize(); }
    }

    public static List<Recipe> originalRecipes(AspectRegistry a) {
        return List.of(
                new Recipe("thaumcraft:hedge_tallow", "HEDGEALCHEMY@1", Output.TALLOW,
                        new AspectListData().add(a.FIRE, 1)),
                new Recipe("thaumcraft:hedge_leather", "HEDGEALCHEMY@1", Output.LEATHER,
                        new AspectListData().add(a.AIR, 3).add(a.BEAST, 3))
        );
    }

    /** Call this only for actual vanilla rotten flesh catalyst stacks. */
    public static Recipe matchingRecipe(PlayerKnowledgeData knowledge,
                                        AspectListData contents, List<Recipe> recipes) {
        if (knowledge == null || contents == null || recipes == null) return null;
        Recipe best = null;
        int score = 0;
        for (Recipe recipe : recipes) {
            if (!knowledge.isResearchKnown(recipe.research())
                    || !LegacyCrucibleCatalystLogic.containsAll(contents, recipe.required()))
                continue;
            if (recipe.score() > score) {
                score = recipe.score();
                best = recipe;
            }
        }
        return best;
    }

    /** Consume only the selected output's original essentia types. */
    public static AspectListData afterCraft(AspectListData contents, Recipe recipe) {
        if (recipe == null
                || !LegacyCrucibleCatalystLogic.containsAll(contents, recipe.required()))
            throw new IllegalArgumentException("Missing hedge alchemy essentia");
        AspectListData after = contents.copy();
        for (AspectData aspect : recipe.required().getAspects())
            after.remove(aspect, recipe.required().getAmount(aspect));
        return after;
    }
}
