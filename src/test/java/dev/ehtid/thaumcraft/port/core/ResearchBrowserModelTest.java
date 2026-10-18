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
        // In survival the player can open this UI only after owning the
        // Thaumonomicon. The original bootstrap marker is therefore present.
        knowledge.addResearch("!gotthaumonomicon");

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
        check(knowledge.getResearchList().equals(java.util.Set.of("!gotthaumonomicon")),
                "read-only browser must never mutate player research");
        for (var entry : basics) {
            for (String parent : entry.parents()) {
                String parentKey = parent.split("@", 2)[0];
                var source = catalog.get(parentKey);
                check(source != null, "never leak internal scan/flag prerequisite strings");
                check(!source.hasMeta(ResearchMeta.HIDDEN)
                                || knowledge.isResearchKnown(parentKey),
                        "never reveal a still-hidden parent research identity");
            }
        }


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
        knowledge.addResearch("UNLOCKALCHEMY");
        check(!ResearchBrowserModel.categoryUnlocked(categories.get("ALCHEMY"),
                knowledge, catalog), "merely knowing unlock research is insufficient");
        knowledge.setResearchStage("UNLOCKALCHEMY",
                catalog.stageCount("UNLOCKALCHEMY") + 1);
        check(ResearchBrowserModel.categoryUnlocked(categories.get("ALCHEMY"),
                knowledge, catalog), "completed source unlock must open Alchemy");
        var alchemy = ResearchBrowserModel.entries("ALCHEMY", knowledge, catalog, categories);
        check(!alchemy.isEmpty(), "unlocked Alchemy has valid source entries");
        check(alchemy.stream().noneMatch(e -> catalog.get(e.key()).hasMeta(ResearchMeta.HIDDEN)
                        && !e.known()), "hidden unknown Alchemy research remains secret");

        check(ResearchBrowserModel.categories(knowledge, catalog, categories)
                        .stream().map(ResearchBrowserModel.Category::key)
                        .toList().equals(List.of("BASICS", "AUROMANCY", "ALCHEMY",
                                "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH")),
                "preserve original category registration order");
        System.out.println("M07 read-only research browser: OK (" + assertions + " assertions)");
    }
}
