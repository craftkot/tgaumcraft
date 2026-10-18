package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only Thaumonomicon category/research index over the exact BETA26
 * catalog and the player's CURRENT synchronized knowledge. No unlocks,
 * research grants, client-side progress or synthetic tree coordinates.
 *
 * This is an index of already known/discoverable nodes, not yet the original
 * spatial research graph or chapter-detail browser.
 */
public final class ResearchBrowserModel {
    private ResearchBrowserModel() {}

    public record Category(String key, String titleKey, boolean unlocked,
                           int visibleEntries, int knownEntries) {}

    public record Entry(String key, int stage, int stageCount,
                        boolean known, boolean complete, List<String> parents) {
        public Entry {
            parents = List.copyOf(parents);
        }
    }

    /** Match the research table's original category unlock condition. */
    public static boolean categoryUnlocked(ResearchCategoryData category,
                                           PlayerKnowledgeData knowledge,
                                           ResearchCatalog catalog) {
        Objects.requireNonNull(category);
        Objects.requireNonNull(knowledge);
        Objects.requireNonNull(catalog);
        return category.researchKey() == null
                || knowledge.isResearchComplete(category.researchKey(), catalog);
    }

    /** Do not leak unknown hidden research or chapters gated by parents. */
    public static List<Entry> entries(String categoryKey, PlayerKnowledgeData knowledge,
                                      ResearchCatalog catalog,
                                      ResearchCategoryRegistry categories) {
        Objects.requireNonNull(categoryKey);
        Objects.requireNonNull(knowledge);
        Objects.requireNonNull(catalog);
        Objects.requireNonNull(categories);
        ResearchCategoryData category = categories.get(categoryKey);
        if (category == null || !categoryUnlocked(category, knowledge, catalog))
            return List.of();
        ArrayList<Entry> result = new ArrayList<>();
        for (ResearchEntryData entry : catalog.entries()) {
            if (!categoryKey.equals(entry.categoryKey())) continue;
            boolean known = knowledge.isResearchKnown(entry.key());
            // HIDDEN / parent-gated entries are *not* visible until they
            // become known. Visible, unlocked entries are read-only.
            if (!known && (entry.hasMeta(ResearchMeta.HIDDEN)
                    || !ResearchRequisites.hasRequisites(knowledge, catalog, entry.key())))
                continue;
            int stage = known ? knowledge.getResearchStage(entry.key()) : -1;
            result.add(new Entry(entry.key(), stage, entry.stageCount(),
                    known, known && knowledge.isResearchComplete(entry.key(), catalog),
                    entry.parentsStripped()));
        }
        return List.copyOf(result);
    }

    public static List<Category> categories(PlayerKnowledgeData knowledge,
                                           ResearchCatalog catalog,
                                           ResearchCategoryRegistry categories) {
        Objects.requireNonNull(knowledge);
        Objects.requireNonNull(catalog);
        Objects.requireNonNull(categories);
        ArrayList<Category> result = new ArrayList<>();
        for (ResearchCategoryData category : categories.values()) {
            boolean unlocked = categoryUnlocked(category, knowledge, catalog);
            var visible = unlocked ? entries(category.key(), knowledge, catalog, categories)
                    : List.<Entry>of();
            result.add(new Category(category.key(), category.titleKey(), unlocked,
                    visible.size(), (int) visible.stream().filter(Entry::known).count()));
        }
        return List.copyOf(result);
    }
}
