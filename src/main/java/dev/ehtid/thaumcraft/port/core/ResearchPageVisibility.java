package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Server-knowledge-bounded Thaumonomicon stage disclosure.
 * Original BETA26 research stages are 1-based in localization, while
 * PlayerKnowledge stage counters are 0-based. No pages beyond the player's
 * actually saved stage may be displayed in a read-only book.
 */
public final class ResearchPageVisibility {
    private ResearchPageVisibility() {}

    /** -1 means not discovered. All returned indices are zero-based. */
    public static int maximumStage(LegacyResearchPages.Page page,
                                   PlayerKnowledgeData knowledge,
                                   ResearchIndex catalog) {
        Objects.requireNonNull(page);
        Objects.requireNonNull(knowledge);
        Objects.requireNonNull(catalog);
        if (page.stages().isEmpty() || !knowledge.isResearchKnown(page.key())) return -1;
        if (knowledge.isResearchComplete(page.key(), catalog))
            return page.stages().size() - 1;
        return Math.min(page.stages().size() - 1,
                Math.max(0, knowledge.getResearchStage(page.key())));
    }

    public static int clampRequestedStage(int requested, int permittedMaximum) {
        if (permittedMaximum < 0) return -1;
        return Math.max(0, Math.min(requested, permittedMaximum));
    }
}
