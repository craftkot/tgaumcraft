package dev.ehtid.thaumcraft.port.core;

/** Server-authoritative hidden research markers that bootstrap FIRSTSTEPS. */
public final class FirstStepsBootstrapLogic {
    private FirstStepsBootstrapLogic() {}
    public static boolean onCrystalPickup(PlayerKnowledgeData k) { return k.addResearch("!gotcrystals"); }
    public static boolean onWake(PlayerKnowledgeData k) {
        return k.isResearchKnown("!gotcrystals") && k.addResearch("!gotdream");
    }
    public static boolean onThaumonomiconPickup(PlayerKnowledgeData k) { return k.addResearch("!gotthaumonomicon"); }
    public enum DustTarget { NONE, THAUMONOMICON_ITEM, ARCANE_WORKBENCH_BLOCK, CRUCIBLE_BLOCK }
    public static DustTarget dustTarget(PlayerKnowledgeData k, String blockId) {
        if ("minecraft:bookshelf".equals(blockId) && k.isResearchKnown("!gotdream")) return DustTarget.THAUMONOMICON_ITEM;
        if ("minecraft:crafting_table".equals(blockId) && k.isResearchKnown("FIRSTSTEPS@1")) return DustTarget.ARCANE_WORKBENCH_BLOCK;
        // BETA26 ConfigRecipes.initializeCompoundRecipes:
        // DustTriggerSimple("UNLOCKALCHEMY@1", Blocks.CAULDRON, BlocksTC.crucible).
        // Modern Minecraft separates empty and water-filled cauldrons.
        if (("minecraft:cauldron".equals(blockId)
                || "minecraft:water_cauldron".equals(blockId))
                && k.isResearchKnown("UNLOCKALCHEMY@1"))
            return DustTarget.CRUCIBLE_BLOCK;
        return DustTarget.NONE;
    }
    public static int dustDelayTicks() { return 50; }
    public static boolean applyDelayed(String originalBlockId, String currentBlockId) { return originalBlockId.equals(currentBlockId); }
}
