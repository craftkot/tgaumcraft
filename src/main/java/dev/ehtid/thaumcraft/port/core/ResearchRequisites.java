package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/** Exact loader-independent port of ResearchManager.doesPlayerHaveRequisites. */
public final class ResearchRequisites {
    private ResearchRequisites() {}

    public static boolean hasRequisites(PlayerKnowledgeData knowledge, ResearchCatalog catalog, String researchKey) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(catalog, "catalog");
        ResearchEntryData entry = catalog.get(researchKey);
        // BETA26 intentionally permits arbitrary/non-JSON research keys (scan keys are the major user of this).
        if (entry == null) return true;
        if (entry.parents().isEmpty()) return true;
        return ResearchKnowledgeQueries.knowsResearchStrict(
                knowledge, catalog, entry.parentsStripped().toArray(String[]::new));
    }
}
