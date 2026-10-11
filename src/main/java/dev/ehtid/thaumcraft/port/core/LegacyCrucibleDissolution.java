package dev.ehtid.thaumcraft.port.core;

/**
 * The non-recipe branch of original BETA26 TileCrucible.attemptSmelt:
 * a hot water-filled crucible dissolves a tagged item into its exact
 * object aspects. If an actual catalyst recipe matches, THAT recipe
 * must run first; this class does not invent or substitute recipes.
 */
public final class LegacyCrucibleDissolution {
    private LegacyCrucibleDissolution() {}

    public static boolean canDissolve(int heat, int waterMb, AspectListData objectTags) {
        return LegacyCrucibleHeat.boiling(heat, waterMb)
                && objectTags != null && objectTags.size() > 0;
    }

    /**
     * Mirrors the no-matching-recipe aspect merge in the original
     * TileCrucible loop, once per consumed item. Keeps insertion order.
     * The caller owns the transaction and must shrink the input only
     * after successfully applying the resulting list.
     */
    public static AspectListData dissolve(AspectListData stored,
                                          AspectListData objectTags,
                                          int consumedItems) {
        if (stored == null || objectTags == null || consumedItems <= 0
                || objectTags.size() == 0)
            throw new IllegalArgumentException("No valid crucible dissolution");
        AspectListData result = stored.copy();
        for (int i = 0; i < consumedItems; i++) {
            for (AspectData aspect : objectTags.getAspects()) {
                int amount = objectTags.getAmount(aspect);
                if (aspect != null && amount > 0)
                    result.add(aspect, amount);
            }
        }
        return result;
    }
}
