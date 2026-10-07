package dev.ehtid.thaumcraft.port.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Insertion-ordered research catalog, matching TC6 ResearchCategories' LinkedHashMap iteration semantics. */
public final class ResearchCatalog implements ResearchIndex {
    private final Map<String, ResearchEntryData> entries = new LinkedHashMap<>();

    public ResearchCatalog add(ResearchEntryData entry) {
        entries.put(entry.key(), entry);
        return this;
    }

    public ResearchEntryData get(String key) {
        return entries.get(key);
    }

    public Collection<ResearchEntryData> entries() {
        return entries.values();
    }

    public int size() {
        return entries.size();
    }

    public Set<String> categoryKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (ResearchEntryData entry : entries.values()) {
            if (!entry.categoryKey().isEmpty()) out.add(entry.categoryKey());
        }
        return Set.copyOf(out);
    }

    /** Mirrors PlayerKnowledge.DefaultImpl.addAutoUnlockResearch(). */
    public int addAutoUnlockResearch(PlayerKnowledgeData knowledge) {
        int added = 0;
        for (ResearchEntryData entry : entries.values()) {
            if (entry.hasMeta(ResearchMeta.AUTOUNLOCK) && knowledge.addResearch(entry.key())) added++;
        }
        return added;
    }

    /** BETA26 ResearchManager.doesPlayerHaveRequisites for the parent syntax used by the shipped data. */
    public boolean hasRequisites(PlayerKnowledgeData knowledge, String researchKey) {
        ResearchEntryData entry = entries.get(researchKey);
        if (entry == null) return true;
        for (String raw : entry.parents()) {
            if (raw == null) continue;
            String parent = raw.startsWith("~") ? raw.substring(1) : raw; // getParentsStripped()
            if (parent.contains("&&") || parent.contains("||")) {
                // Shipped BETA26 JSON never uses these operators. Keep the legacy helper behavior isolated
                // rather than inventing modern boolean semantics.
                if (!knowsResearchStrictLegacy(knowledge, parent)) return false;
            } else if (parent.contains("@")) {
                if (!knowledge.isResearchKnown(parent)) return false;
            } else if (!knowledge.isResearchComplete(parent, this)) {
                return false;
            }
        }
        return true;
    }

    /** Mirrors ThaumcraftCapabilities.knowsResearchStrict, including its odd || control flow. */
    private boolean knowsResearchStrictLegacy(PlayerKnowledgeData knowledge, String expression) {
        for (String token : new String[]{expression}) {
            if (token.contains("&&")) {
                for (String part : token.split("&&")) if (!knowsResearchStrictLegacy(knowledge, part)) return false;
            } else if (token.contains("||")) {
                boolean any = false;
                for (String part : token.split("\\|\\|")) {
                    if (knowsResearchStrictLegacy(knowledge, part)) { any = true; break; }
                }
                // BETA26 returns true immediately on a successful alternative and, surprisingly,
                // also does not fail when none match. With a single expression both paths are true.
                if (any) return true;
            } else if (token.contains("@")) {
                if (!knowledge.isResearchKnown(token)) return false;
            } else if (!knowledge.isResearchComplete(token, this)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int stageCount(String researchKey) {
        ResearchEntryData entry = entries.get(researchKey);
        return entry == null ? -1 : entry.stageCount();
    }
}
