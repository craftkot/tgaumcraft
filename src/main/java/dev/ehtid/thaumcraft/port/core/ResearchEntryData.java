package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Set;

/**
 * Runtime-independent projection of the BETA26 ResearchEntry fields needed by progression,
 * persistence and the future research browser/runtime loader.
 *
 * Reward items are deliberately kept outside this core: the Minecraft integration layer will receive
 * completion events and insert/drop the actual ItemStacks.
 */
public record ResearchEntryData(
        String key,
        String categoryKey,
        Set<ResearchMeta> meta,
        List<ResearchStageData> stages,
        List<String> parents,
        List<String> siblings,
        List<ResearchAddendumData> addenda,
        List<KnowledgeReward> knowledgeRewards) {

    public ResearchEntryData {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("research key is required");
        categoryKey = categoryKey == null ? "" : categoryKey;
        meta = meta == null ? Set.of() : Set.copyOf(meta);
        stages = stages == null ? null : List.copyOf(stages); // null is semantically distinct in TC6
        parents = parents == null ? List.of() : List.copyOf(parents);
        siblings = siblings == null ? List.of() : List.copyOf(siblings);
        addenda = addenda == null ? List.of() : List.copyOf(addenda);
        knowledgeRewards = knowledgeRewards == null ? List.of() : List.copyOf(knowledgeRewards);
    }

    /** Compatibility constructor retained for synthetic parity tests written before category/meta were projected. */
    public ResearchEntryData(
            String key,
            List<ResearchStageData> stages,
            List<String> parents,
            List<String> siblings,
            List<ResearchAddendumData> addenda,
            List<KnowledgeReward> knowledgeRewards) {
        this(key, "", Set.of(), stages, parents, siblings, addenda, knowledgeRewards);
    }

    public int stageCount() {
        return stages == null ? -1 : stages.size();
    }

    /** BETA26 ResearchEntry.getParentsStripped: only a leading '~' is removed; stage suffixes remain intact. */
    public List<String> parentsStripped() {
        if (parents.isEmpty()) return List.of();
        return parents.stream().map(p -> p.startsWith("~") ? p.substring(1) : p).toList();
    }

    public boolean hasMeta(ResearchMeta value) {
        return meta.contains(value);
    }
}
