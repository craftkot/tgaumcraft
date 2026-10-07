package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Loader-neutral compatibility port of ThaumcraftCapabilities.knowsResearch / knowsResearchStrict.
 *
 * The two expression helpers contain legacy control-flow quirks that are intentionally preserved:
 * - an AND expression ("A&&B") is recursively required;
 * - an OR expression ("A||B") returns true for the entire vararg call as soon as any branch matches;
 * - an OR expression with no matching branch does not fail the call: execution continues with the next term;
 * - null terms throw just like String.contains in the BETA26 implementation.
 */
public final class ResearchKnowledgeQueries {
    private ResearchKnowledgeQueries() {}

    public static boolean knowsResearch(PlayerKnowledgeData knowledge, String... keys) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(keys, "keys");
        for (String key : keys) {
            // Do not null-check key: the original immediately calls String.contains().
            if (key.contains("&&")) {
                if (!knowsResearch(knowledge, key.split("&&"))) return false;
            } else if (key.contains("||")) {
                for (String branch : key.split("\\|\\|")) {
                    // Literal BETA26 control flow: true exits the whole method, not only the OR group.
                    if (knowsResearch(knowledge, branch)) return true;
                }
                // Also literal BETA26: no branch matching does NOT return false here.
            } else if (!knowledge.isResearchKnown(key)) {
                return false;
            }
        }
        return true;
    }

    public static boolean knowsResearchStrict(PlayerKnowledgeData knowledge, ResearchIndex index, String... keys) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(keys, "keys");
        for (String key : keys) {
            // Do not null-check key for bytecode parity.
            if (key.contains("&&")) {
                if (!knowsResearchStrict(knowledge, index, key.split("&&"))) return false;
            } else if (key.contains("||")) {
                for (String branch : key.split("\\|\\|")) {
                    if (knowsResearchStrict(knowledge, index, branch)) return true;
                }
            } else if (key.contains("@")) {
                if (!knowledge.isResearchKnown(key)) return false;
            } else if (!knowledge.isResearchComplete(key, index)) {
                return false;
            }
        }
        return true;
    }
}
