package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** Loader-neutral mirror of BETA26 PlayerKnowledge NBT structure. */
public record LegacyKnowledgePayload(
        List<ResearchRow> research,
        List<KnowledgeRow> knowledge) {

    public LegacyKnowledgePayload {
        research = research == null ? List.of() : List.copyOf(research);
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
    }

    public record ResearchRow(String key, Integer stage, String flags) {}
    public record KnowledgeRow(String key, int amount) {}
}
