package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only BETA26 research-knowledge gate display.
 *
 * Every original required_knowledge / required_knowledgeauram entry is a
 * TYPE;CATEGORY;LEVEL string. The player's knowledge is stored as raw points:
 * THEORY progresses in 32s and OBSERVATION in 16s. Comparing raw points
 * directly against the source LEVEL would falsely mark stages ready.
 *
 * This class never grants or spends knowledge; only the authoritative
 * research progression path may do that.
 */
public final class ResearchKnowledgeRequirementStatus {
    private ResearchKnowledgeRequirementStatus() {}

    public record Status(String source, KnowledgeType type, String category,
                         int currentLevels, int requiredLevels) {
        public boolean satisfied() { return currentLevels >= requiredLevels; }
    }

    public static Status inspect(String encoded, PlayerKnowledgeData knowledge,
                                 ResearchCategoryRegistry categories) {
        Objects.requireNonNull(knowledge);
        Objects.requireNonNull(categories);
        if (encoded == null) return null;
        Set<String> valid = new LinkedHashSet<>();
        for (ResearchCategoryData category : categories.values()) valid.add(category.key());
        KnowledgeReward requirement;
        try {
            requirement = LegacyResearchParser.parseKnowledge(encoded, valid);
        } catch (IllegalArgumentException badEnum) {
            return null; // malformed content must not claim a completed gate
        }
        if (requirement == null) return null;
        return new Status(encoded, requirement.type(), requirement.categoryKey(),
                knowledge.getKnowledge(requirement.type(), requirement.categoryKey()),
                requirement.amount());
    }
}
