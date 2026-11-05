package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Read from BETA26 TileInfusionMatrix.craftingStart + InfusionRecipe.matches.
 *
 * This is a pure, side-effect-free preflight for the *real* upcoming
 * server-side craftingStart. It NEVER grants an output, removes materials
 * or starts a ritual. Validated plan is kept immutable except caller-owned
 * AspectListData, of which the implementation keeps defensive snapshots.
 *
 * Bytecode behavior:
 * - require valid structure and a nonempty center pedestal;
 * - collect only occupied side pedestals; reject zero side inputs;
 * - exact unordered, one-to-one matching with research gate;
 * - after matched recipe, clamp essentia cost multiplier to >=0.5F;
 * - per aspect: (int)(originalAmount * multiplier), omit amount <=0;
 * - recipe instability and its output supplied by caller's verified catalog.
 */
public final class LegacyInfusionStartPlanner {
    private LegacyInfusionStartPlanner() {}

    public record Recipe<T>(String id, String research,
                            Predicate<T> centerIngredient,
                            List<Predicate<T>> sideIngredients,
                            AspectListData baseEssentia, int instability) {
        public Recipe {
            Objects.requireNonNull(id);
            Objects.requireNonNull(research);
            Objects.requireNonNull(centerIngredient);
            Objects.requireNonNull(sideIngredients);
            Objects.requireNonNull(baseEssentia);
            sideIngredients = List.copyOf(sideIngredients);
            baseEssentia = baseEssentia.copy();
        }
        @Override public AspectListData baseEssentia() { return baseEssentia.copy(); }
    }

    public record Prepared<T>(String recipeId, T center,
                              List<T> occupiedSideItems,
                              int[] ingredientIndices,
                              AspectListData cost,
                              int instability, float effectiveMultiplier) {
        public Prepared {
            occupiedSideItems = List.copyOf(occupiedSideItems);
            ingredientIndices = ingredientIndices.clone();
            cost = cost.copy();
        }
        @Override public int[] ingredientIndices() { return ingredientIndices.clone(); }
        @Override public AspectListData cost() { return cost.copy(); }
    }

    /** Java ItemStack.EMPTY is mapped to the explicit empty predicate by adapter. */
    public static <T> Prepared<T> prepare(
            boolean validStructure, boolean centerPresent, T center,
            List<T> occupiedSideItems, PlayerKnowledgeData player,
            Recipe<T> matchedRecipe, float costMultiplier) {
        if (!validStructure || !centerPresent || center == null
                || occupiedSideItems == null || occupiedSideItems.isEmpty()
                || player == null || matchedRecipe == null
                || !Float.isFinite(costMultiplier))
            return null;
        if (occupiedSideItems.stream().anyMatch(Objects::isNull)) return null;
        int[] assignment = LegacyInfusionRecipeMatcher.match(
                player, matchedRecipe.research(), matchedRecipe.centerIngredient(),
                center, occupiedSideItems, matchedRecipe.sideIngredients());
        if (assignment == null) return null;
        float multiplier = Math.max(0.5F, costMultiplier);
        AspectListData costs = calculateEssentia(matchedRecipe.baseEssentia(), multiplier);
        return new Prepared<>(matchedRecipe.id(), center,
                occupiedSideItems, assignment, costs,
                matchedRecipe.instability(), multiplier);
    }

    /** BETA26 uses a Java float multiplication followed by integer truncation. */
    public static AspectListData calculateEssentia(AspectListData base, float multiplier) {
        Objects.requireNonNull(base);
        if (!Float.isFinite(multiplier)) throw new IllegalArgumentException("multiplier");
        float cost = Math.max(0.5F, multiplier);
        AspectListData result = new AspectListData();
        for (AspectData aspect : base.getAspects()) {
            int units = (int) (base.getAmount(aspect) * cost);
            // BETA26 retains even floored-zero keys; missing ingredients can add one back later.
            result.add(aspect, units);
        }
        return result;
    }
}
