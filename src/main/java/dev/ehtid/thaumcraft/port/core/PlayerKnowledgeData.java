package dev.ehtid.thaumcraft.port.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loader-independent compatibility implementation of TC6 PlayerKnowledge.DefaultImpl.
 *
 * Important legacy semantics preserved here:
 * - unknown research stage is -1;
 * - known research with no explicit stage is stage 0;
 * - "KEY@N" means KEY must be known and current stage >= N;
 * - stage > number of JSON stages is COMPLETE (not >=);
 * - knowledge is stored as raw points and exposed in progression-sized levels.
 */
public final class PlayerKnowledgeData {
    private final Set<String> research = new HashSet<>();
    private final Map<String, Integer> stages = new HashMap<>();
    private final Map<String, EnumSet<ResearchFlag>> flags = new HashMap<>();
    private final Map<String, Integer> knowledge = new HashMap<>();

    public void clear() {
        research.clear();
        stages.clear();
        flags.clear();
        knowledge.clear();
    }

    public ResearchStatus getResearchStatus(String key, ResearchIndex index) {
        if (!isResearchKnown(key)) return ResearchStatus.UNKNOWN;
        int stageCount = index == null ? -1 : index.stageCount(baseKey(key));
        if (stageCount < 0 || getResearchStage(baseKey(key)) > stageCount) {
            return ResearchStatus.COMPLETE;
        }
        return ResearchStatus.IN_PROGRESS;
    }

    public boolean isResearchComplete(String key, ResearchIndex index) {
        return getResearchStatus(key, index) == ResearchStatus.COMPLETE;
    }

    public boolean isResearchKnown(String key) {
        if (key == null) return false;
        if (key.isEmpty()) return true;
        String[] split = key.split("@", 2);
        if (split.length > 1) {
            int requiredStage = parseIntOrZero(split[1]);
            if (getResearchStage(split[0]) < requiredStage) return false;
        }
        return research.contains(split[0]);
    }

    public int getResearchStage(String key) {
        if (key == null || !research.contains(key)) return -1;
        return stages.getOrDefault(key, 0);
    }

    public boolean setResearchStage(String key, int stage) {
        if (key == null || !research.contains(key) || stage <= 0) return false;
        stages.put(key, stage);
        return true;
    }

    public boolean addResearch(String key) {
        // BETA26 does not reject null here: isResearchKnown(null) is false and HashSet accepts null.
        if (isResearchKnown(key)) return false;
        research.add(key);
        return true;
    }

    public boolean removeResearch(String key) {
        if (!isResearchKnown(key)) return false;
        // Preserve BETA26 literally: it removes the argument, not the base part before '@'.
        // Thus removeResearch("R@1") can report success while leaving "R" present.
        research.remove(key);
        // BETA26 removeResearch itself does not clear stage/flags maps.
        return true;
    }

    public Set<String> getResearchList() {
        return Collections.unmodifiableSet(research);
    }

    public boolean setResearchFlag(String key, ResearchFlag flag) {
        EnumSet<ResearchFlag> set = flags.computeIfAbsent(key, ignored -> EnumSet.noneOf(ResearchFlag.class));
        if (set.contains(flag)) return false;
        set.add(flag);
        return true;
    }

    public boolean clearResearchFlag(String key, ResearchFlag flag) {
        EnumSet<ResearchFlag> set = flags.get(key);
        if (set == null) return false;
        boolean removed = set.remove(flag);
        if (set.isEmpty()) {
            // Intentional compatibility oddity. BETA26 bytecode calls flags.remove(researchSet)
            // instead of flags.remove(key), so the empty mapping normally remains in the map.
            flags.remove(research);
        }
        return removed;
    }

    public boolean hasResearchFlag(String key, ResearchFlag flag) {
        EnumSet<ResearchFlag> set = flags.get(key);
        return set != null && set.contains(flag);
    }

    public boolean addKnowledge(KnowledgeType type, String categoryKey, int amount) {
        String key = knowledgeKey(type, categoryKey);
        int value = knowledge.getOrDefault(key, 0);
        if (value + amount < 0) return false;
        knowledge.put(key, value + amount);
        return true;
    }

    public int getKnowledge(KnowledgeType type, String categoryKey) {
        return (int) Math.floor((double) getKnowledgeRaw(type, categoryKey) / type.progression());
    }

    public int getKnowledgeRaw(KnowledgeType type, String categoryKey) {
        return knowledge.getOrDefault(knowledgeKey(type, categoryKey), 0);
    }

    public Map<String, Integer> copyStages() {
        return Map.copyOf(stages);
    }

    public Map<String, Set<ResearchFlag>> copyFlags() {
        Map<String, Set<ResearchFlag>> out = new HashMap<>();
        for (Map.Entry<String, EnumSet<ResearchFlag>> entry : flags.entrySet()) {
            out.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return out;
    }

    /** Loader-neutral restore path used by Attachment/codec integration. */
    public void loadSnapshot(Set<String> researchKeys, Map<String, Integer> stageMap,
                             Map<String, ? extends Set<ResearchFlag>> flagMap,
                             Map<String, Integer> rawKnowledge) {
        // The public getResearchList() returns a live unmodifiable view of
        // this set. Snapshot it BEFORE clear(), or callers passing that view
        // would silently erase all their discovered research on restore.
        Set<String> researchCopy = researchKeys == null ? null : new HashSet<>(researchKeys);
        clear();
        if (researchCopy != null) research.addAll(researchCopy);
        if (stageMap != null) stages.putAll(stageMap);
        if (flagMap != null) {
            for (Map.Entry<String, ? extends Set<ResearchFlag>> entry : flagMap.entrySet()) {
                EnumSet<ResearchFlag> set = EnumSet.noneOf(ResearchFlag.class);
                set.addAll(entry.getValue());
                flags.put(entry.getKey(), set);
            }
        }
        if (rawKnowledge != null) knowledge.putAll(rawKnowledge);
    }

    public Map<String, Integer> copyKnowledgeRaw() {
        return Map.copyOf(knowledge);
    }

    private static String knowledgeKey(KnowledgeType type, String categoryKey) {
        return type.abbreviation() + "_" + (categoryKey == null ? "" : categoryKey);
    }

    private static String baseKey(String key) {
        int i = key.indexOf('@');
        return i < 0 ? key : key.substring(0, i);
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
