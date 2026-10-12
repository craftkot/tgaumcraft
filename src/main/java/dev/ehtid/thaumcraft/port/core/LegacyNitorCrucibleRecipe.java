package dev.ehtid.thaumcraft.port.core;

/**
 * Source-faithful first (yellow) Nitor CrucibleRecipe from BETA26
 * ConfigRecipes.initializeAlchemyRecipes:
 * research UNLOCKALCHEMY@3, catalyst dustGlowstone,
 * required Potentia 10 + Ignis 10 + Lux 10, output yellow Nitor.
 *
 * Colored Nitor variations are separate, ordinary dye+Nitor recipes
 * and must not be silently substituted for this crucible output.
 */
public final class LegacyNitorCrucibleRecipe {
    public static final String ID = "thaumcraft:nitor";
    public static final String RESEARCH = "UNLOCKALCHEMY@3";
    public static final int SCORE = 30;
    private LegacyNitorCrucibleRecipe() {}

    public static AspectListData required(AspectRegistry a) {
        return new AspectListData().add(a.ENERGY, 10)
                .add(a.FIRE, 10).add(a.LIGHT, 10);
    }

    /** Caller must validate actual glowstone-dust catalyst first. */
    public static boolean canCraft(PlayerKnowledgeData knowledge,
                                   AspectListData stored,
                                   AspectRegistry registry) {
        return knowledge != null && knowledge.isResearchKnown(RESEARCH)
                && LegacyCrucibleCatalystLogic.containsAll(
                        stored, required(registry));
    }

    public static AspectListData afterCraft(AspectListData stored,
                                            AspectRegistry registry) {
        AspectListData cost = required(registry);
        if (!LegacyCrucibleCatalystLogic.containsAll(stored, cost))
            throw new IllegalArgumentException("Nitor requires 10 each Potentia, Ignis and Lux");
        AspectListData remaining = stored.copy();
        for (AspectData aspect : cost.getAspects())
            remaining.remove(aspect, cost.getAmount(aspect));
        return remaining;
    }
}
