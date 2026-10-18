package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** Fail-closed catalog and visibility tests for the read-only M07 browser. */
public final class ResearchBrowserModelTest {
    private static int assertions;
    private static void check(boolean value, String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var catalog = BuiltInResearchCatalog.create();
        var categories = BuiltInResearchCategories.create(new AspectRegistry());
        var knowledge = new PlayerKnowledgeData();

        check(catalog.size() == 148, "must display the actual 148-node catalog");
        check(categories.size() == 7, "seven original BETA26 research categories");
        var initial = ResearchBrowserModel.categories(knowledge, catalog, categories);
        check(initial.size() == 7, "all category descriptors remain in source order");
        check(initial.get(0).key().equals("BASICS") && initial.get(0).unlocked(),
                "fundamentals available without artificial unlock");
        for (var category : initial) {
            check(category.key().equals("BASICS") || !category.unlocked(),
                    "other categories require original source unlock research");
        }
        check(ResearchBrowserModel.entries("ALCHEMY", knowledge, catalog, categories).isEmpty(),
                "locked alchemy must reveal no nodes");
        check(ResearchBrowserModel.entries("NOT_A_CATEGORY", knowledge, catalog, categories).isEmpty(),
                "unknown categories must return no entries");

        var basics = ResearchBrowserModel.entries("BASICS", knowledge, catalog, categories);
        check(!basics.isEmpty(), "fundamentals has actual discoverable nodes");
        check(basics.stream().noneMatch(e -> e.known() || e.complete()),
                "fresh player must not acquire knowledge from browsing");
        for (var entry : basics) {
            var original = catalog.get(entry.key());
            check(!original.hasMeta(ResearchMeta.HIDDEN),
                    "unknown hidden nodes must not be exposed");
            check(ResearchRequisites.hasRequisites(knowledge, catalog, entry.key()),
                    "unknown parent-gated nodes must not be exposed");
        }
        check(knowledge.getResearchList().isEmpty(),
                "read-only browser must never mutate player research");

        // A known HIDDEN research stays visible even when its metadata
        // requests hiding from players who have not discovered it.
        ResearchEntryData hidden = catalog.entries().stream()
                .filter(e -> e.hasMeta(ResearchMeta.HIDDEN)
                        && e.categoryKey().equals("BASICS"))
                .findFirst().orElseThrow();
        knowledge.addResearch(hidden.key());
        var afterDiscovery = ResearchBrowserModel.entries("BASICS", knowledge, catalog, categories);
        check(afterDiscovery.stream().anyMatch(e -> e.key().equals(hidden.key()) && e.known()),
                "known hidden research must not vanish from player's own book");

        // Genuine category unlock is measured from the player's existing
        // progress. Merely learning the key must not open the whole category.
        check(!ResearchBrowserModel.categoryUnlocked(categories.get("ALCHEMY"),
                knowledge, catalog), "uncompleted unlock remains gated");
        check(ResearchBrowserModel.entries("ALCHEMY", knowledge, catalog, categories).isEmpty(),
                "category still hidden with unrelated known research");
        check(ResearchBrowserModel.categories(knowledge, catalog, categories)
                        .stream().map(ResearchBrowserModel.Category::key)
                        .toList().equals(List.of("BASICS", "AUROMANCY", "ALCHEMY",
                                "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH")),
                "preserve original category registration order");
        System.out.println("M07 read-only research browser: OK (" + assertions + " assertions)");
    }
}
