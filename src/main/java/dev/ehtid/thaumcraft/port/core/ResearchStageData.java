package dev.ehtid.thaumcraft.port.core;

/**
 * Loader-independent subset of a TC6 ResearchStage needed by ResearchManager.progressResearch.
 * BETA26 considers craft/obtain/knowledge/research requirements when deciding whether a stage
 * is auto-skipped by progressResearch; text-only stages are therefore represented with all four false.
 */
public record ResearchStageData(
        boolean hasCraftRequirement,
        boolean hasObtainRequirement,
        boolean hasKnowledgeRequirement,
        boolean hasResearchRequirement,
        int warp) {

    public ResearchStageData {
        if (warp < 0) throw new IllegalArgumentException("warp cannot be negative");
    }

    public boolean hasGateRequirement() {
        return hasCraftRequirement || hasObtainRequirement || hasKnowledgeRequirement || hasResearchRequirement;
    }

    public static ResearchStageData open(int warp) {
        return new ResearchStageData(false, false, false, false, warp);
    }

    public static ResearchStageData gated(int warp) {
        return new ResearchStageData(true, false, false, false, warp);
    }
}
