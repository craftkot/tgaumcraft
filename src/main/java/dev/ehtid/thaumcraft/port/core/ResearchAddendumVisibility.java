package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only visibility of the BETA26 research addenda.
 *
 * The authoritative ResearchProgressionService sets ResearchFlag.PAGE after
 * the source milestone is reached. The book cannot unlock that flag; it
 * additionally verifies each individual source required-research condition
 * so a PAGE flag for one addendum cannot disclose its siblings (ORE/PLANTS).
 */
public final class ResearchAddendumVisibility {
    private ResearchAddendumVisibility() {}

    public static List<LegacyResearchPages.Addendum> visible(
            LegacyResearchPages.Page page,
            PlayerKnowledgeData knowledge,
            ResearchCatalog catalog) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(catalog, "catalog");
        if (!knowledge.isResearchComplete(page.key(), catalog)
                || !knowledge.hasResearchFlag(page.key(), ResearchFlag.PAGE))
            return List.of();

        ArrayList<LegacyResearchPages.Addendum> out = new ArrayList<>();
        for (var addendum : page.addenda()) {
            // Original BETA26 addenda are gated by source research keys.
            // All supported manifest gates are individual keys (no fabricated
            // 1.21 recipes, placeholder unlocks or client mutations).
            boolean unlocked = true;
            for (String requirement : addendum.requiredResearch()) {
                if (!ResearchKnowledgeQueries.knowsResearchStrict(
                        knowledge, catalog, requirement)) {
                    unlocked = false;
                    break;
                }
            }
            if (unlocked) out.add(addendum);
        }
        return List.copyOf(out);
    }
}
