package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/**
 * Faithful source-defined BETA26 ConfigRecipes crucible metallurgy recipes:
 * thaumcraft:brassingot METALLURGY@1 -> ItemsTC.ingots metadata 2,
 *   catalyst ingotIron, 5 Instrumentum.
 * thaumcraft:thaumiumingot METALLURGY@2 -> ItemsTC.ingots metadata 0,
 *   catalyst ingotIron, 5 Praecantatio and 5 Terra.
 *
 * Original ThaumcraftCraftingManager.findMatchingCrucibleRecipe iterates
 * recipes, requires known player research, and chooses the eligible recipe
 * with the GREATEST summed essentia requirement (strict > comparison).
 */
public final class LegacyCrucibleCatalystLogic {
    private LegacyCrucibleCatalystLogic() {}

    public static final int WATER_PER_CRAFT_MB = 50;

    public record Recipe(String id, String research, String catalystOre,
                         int outputMeta, AspectListData required) {
        public int score() { return required.visSize(); }
    }

    public static List<Recipe> originalIronRecipes(AspectRegistry a) {
        return List.of(
                new Recipe("thaumcraft:brassingot", "METALLURGY@1",
                        "ingotIron", 2,
                        new AspectListData().add(a.TOOL, 5)),
                new Recipe("thaumcraft:thaumiumingot", "METALLURGY@2",
                        "ingotIron", 0,
                        new AspectListData().add(a.MAGIC, 5).add(a.EARTH, 5))
        );
    }

    public static Recipe bestIronRecipe(PlayerKnowledgeData knowledge,
                                        AspectListData available,
                                        List<Recipe> recipes) {
        if (knowledge == null || available == null || recipes == null) return null;
        Recipe best = null;
        int largest = 0;
        for (Recipe candidate : recipes) {
            if (!knowledge.isResearchKnown(candidate.research())
                    || !containsAll(available, candidate.required()))
                continue;
            if (candidate.score() > largest) {
                largest = candidate.score();
                best = candidate;
            }
        }
        return best;
    }

    public static boolean containsAll(AspectListData have, AspectListData needed) {
        if (have == null || needed == null) return false;
        for (AspectData aspect : needed.getAspects()) {
            if (have.getAmount(aspect) < needed.getAmount(aspect)) return false;
        }
        return true;
    }

    /** Original CrucibleRecipe.removeMatching removes required types only. */
    public static AspectListData afterCraft(AspectListData have, Recipe recipe) {
        if (recipe == null || !containsAll(have, recipe.required()))
            throw new IllegalArgumentException("Missing crucible essentia");
        AspectListData result = have.copy();
        for (AspectData aspect : recipe.required().getAspects())
            result.remove(aspect, recipe.required().getAmount(aspect));
        return result;
    }

    /** Original FluidTank.drain(50,true) accepts any positive water amount. */
    public static int waterAfterCraft(int waterMb) {
        if (waterMb <= 0) throw new IllegalArgumentException("Empty crucible");
        return Math.max(0, waterMb - WATER_PER_CRAFT_MB);
    }
}
