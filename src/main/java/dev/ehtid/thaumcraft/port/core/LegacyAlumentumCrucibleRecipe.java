package dev.ehtid.thaumcraft.port.core;

/** Original Thaumcraft 6.1.BETA26 ALUMENTUM CrucibleRecipe, disassembled from ConfigRecipes. */
public final class LegacyAlumentumCrucibleRecipe {
    public static final String ID = "thaumcraft:alumentum";
    public static final String RESEARCH = "ALUMENTUM";
    public static final int SCORE = 25;
    // ItemAlumentum and EntityAlumentum, direct original BETA26 bytecode.
    public static final float THROW_SPEED = 0.4F;
    // EntityAlumentum.shoot(x,y,z,velocity,inaccuracy) overrides incoming velocity.
    public static final float ACTUAL_PROJECTILE_SPEED = 0.75F;
    public static final float THROW_INACCURACY = 2.0F;
    public static final float THROW_PITCH_OFFSET = -5.0F;
    public static final float EXPLOSION_POWER = 1.1F;
    private LegacyAlumentumCrucibleRecipe() {}

    /** Original Items.COAL metadata wildcard 32767: coal (0), charcoal (1). */
    public static boolean isOriginalCoalVariant(String id) {
        return "minecraft:coal".equals(id) || "minecraft:charcoal".equals(id);
    }

    public static AspectListData required(AspectRegistry registry) {
        return new AspectListData().add(registry.ENERGY, 10)
                .add(registry.FIRE, 10).add(registry.ENTROPY, 5);
    }

    public static boolean canCraft(PlayerKnowledgeData knowledge,
                                   AspectListData stored, AspectRegistry registry) {
        return knowledge != null && stored != null
                && knowledge.isResearchKnown(RESEARCH)
                && LegacyCrucibleCatalystLogic.containsAll(stored, required(registry));
    }

    public static AspectListData afterCraft(AspectListData stored, AspectRegistry registry) {
        AspectListData cost = required(registry);
        if (!LegacyCrucibleCatalystLogic.containsAll(stored, cost))
            throw new IllegalArgumentException("Missing Alumentum essentia");
        AspectListData after = stored.copy();
        for (AspectData aspect : cost.getAspects())
            after.remove(aspect, cost.getAmount(aspect));
        return after;
    }
}
