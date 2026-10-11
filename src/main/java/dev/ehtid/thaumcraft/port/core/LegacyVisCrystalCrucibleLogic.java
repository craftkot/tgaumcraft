package dev.ehtid.thaumcraft.port.core;

/**
 * The original ConfigRecipes.initializeAlchemyRecipes registers one
 * thaumcraft:vis_crystal_<aspect> CrucibleRecipe per Aspect.aspects entry:
 * research BASEALCHEMY, catalyst OreDictionary "nuggetQuartz",
 * output ThaumcraftApiHelper.makeCrystal(aspect), required aspect x2.
 *
 * When multiple recipes have equal 2-vis cost, the BETA26 manager's
 * strict ">" replacement keeps the FIRST eligible registered aspect.
 */
public final class LegacyVisCrystalCrucibleLogic {
    private LegacyVisCrystalCrucibleLogic() {}

    public static final String RESEARCH = "BASEALCHEMY";
    public static final int QUARTZ_NUGGET_META = 9;
    public static final int COST = 2;

    public static AspectData firstEligibleAspect(PlayerKnowledgeData knowledge,
                                                  AspectListData available,
                                                  AspectRegistry registry) {
        if (knowledge == null || available == null || registry == null
                || !knowledge.isResearchKnown(RESEARCH)) return null;
        for (AspectData candidate : registry.values()) {
            if (available.getAmount(candidate) >= COST) return candidate;
        }
        return null;
    }

    public static AspectListData afterCraft(AspectListData available, AspectData chosen) {
        if (available == null || chosen == null || available.getAmount(chosen) < COST)
            throw new IllegalArgumentException("Missing vis crystal essentia");
        AspectListData result = available.copy();
        result.remove(chosen, COST);
        return result;
    }
}
