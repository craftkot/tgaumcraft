package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable loader-neutral payload used as the serialization/sync boundary for player Thaumcraft data. */
public record PlayerThaumcraftSnapshot(
        Set<String> research,
        Map<String, Integer> stages,
        Map<String, Set<ResearchFlag>> flags,
        Map<String, Integer> knowledgeRaw,
        List<Integer> warp,
        int warpCounter) {
}
