package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;

/**
 * M07 save/reload parity of the catalog's visible tree, stages and unlocks.
 * Tests the SAME BETA26 knowledge rows consumed by NeoForge's attachment.
 * Does not replace a real-world save + server restart gameplay proof.
 */
public final class ResearchBrowserPersistenceParityTest {
    private static int assertions;

    private static void check(boolean ok, String name) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }

    private static List<ResearchBrowserModel.Category> categories(
            PlayerKnowledgeData player, ResearchCatalog source,
            ResearchCategoryRegistry cat) {
        return ResearchBrowserModel.categories(player, source, cat);
    }

    private static List<ResearchBrowserModel.Entry> entries(String cat,
            PlayerKnowledgeData player, ResearchCatalog source,
            ResearchCategoryRegistry definitions) {
        return ResearchBrowserModel.entries(cat, player, source, definitions);
    }

    public static void main(String[] args) {
        var catalog = BuiltInResearchCatalog.create();
        var categoryDefs = BuiltInResearchCategories.create(new AspectRegistry());
        var player = new PlayerKnowledgeData();
        catalog.addAutoUnlockResearch(player);
        player.addResearch("!gotthaumonomicon");
        player.addResearch("FIRSTSTEPS");
        player.setResearchStage("FIRSTSTEPS", catalog.stageCount("FIRSTSTEPS") + 1);
        player.addResearch("UNLOCKALCHEMY");
        player.setResearchStage("UNLOCKALCHEMY", catalog.stageCount("UNLOCKALCHEMY") + 1);
        player.addResearch("BASEALCHEMY");
        player.setResearchStage("BASEALCHEMY", 1);
        player.addResearch("ALUMENTUM");
        player.setResearchStage("ALUMENTUM", 1);
        player.addResearch("BATHSALTS"); // hidden but previously discovered
        player.addKnowledge(KnowledgeType.OBSERVATION, "BASICS", 8);

        var beforeCategories = categories(player, catalog, categoryDefs);
        var beforeBasics = entries("BASICS", player, catalog, categoryDefs);
        var beforeAlchemy = entries("ALCHEMY", player, catalog, categoryDefs);
        check(beforeCategories.stream().anyMatch(
                c -> c.key().equals("ALCHEMY") && c.unlocked()),
                "original category must unlock before saving");
        check(beforeAlchemy.stream().anyMatch(
                e -> e.key().equals("BATHSALTS") && e.known()),
                "discovered hidden source research visible in its own book");
        check(beforeAlchemy.stream().anyMatch(
                e -> e.key().equals("ALUMENTUM") && e.stage() == 1),
                "partially complete source research visible with correct stage");

        var save = LegacyPlayerDataCodec.encodeKnowledge(player);
        check(save.research().stream().anyMatch(
                row -> row.key().equals("UNLOCKALCHEMY")
                        && row.stage() == catalog.stageCount("UNLOCKALCHEMY") + 1),
                "completed chapter unlock serialized with stage");
        check(save.research().stream().anyMatch(
                row -> row.key().equals("BATHSALTS")),
                "hidden discovered research persists");

        var reloaded = new PlayerKnowledgeData();
        LegacyPlayerDataCodec.decodeKnowledge(save, reloaded, catalog);
        check(beforeCategories.equals(categories(reloaded, catalog, categoryDefs)),
                "category access/counts unchanged after BETA26 knowledge roundtrip");
        check(beforeBasics.equals(entries("BASICS", reloaded, catalog, categoryDefs)),
                "BASICS tree unchanged after save/reload");
        check(beforeAlchemy.equals(entries("ALCHEMY", reloaded, catalog, categoryDefs)),
                "ALCHEMY tree/stages/links unchanged after save/reload");
        check(reloaded.getKnowledgeRaw(KnowledgeType.OBSERVATION, "BASICS") == 8,
                "observation raw knowledge preserved");
        check(reloaded.isResearchComplete("UNLOCKALCHEMY", catalog),
                "completed unlock remains complete after save/reload");

        // Simulate new world/player with the same basic book but WITHOUT a
        // completed category key: a cached client screen must lock it.
        var other = new PlayerKnowledgeData();
        catalog.addAutoUnlockResearch(other);
        other.addResearch("!gotthaumonomicon");
        check(!ResearchBrowserModel.categoryUnlocked(
                categoryDefs.get("ALCHEMY"), other, catalog),
                "another player must not inherit previous player's unlock");
        check(entries("ALCHEMY", other, catalog, categoryDefs).isEmpty(),
                "no cross-player research visibility leak");

        // The layout is data, not player mutable state. All graph nodes must
        // retain source positions across saves.
        for (var node : LegacyResearchLayout.all()) {
            check(catalog.get(node.key()) != null,
                    "persistent source layout key must exist: " + node.key());
        }
        System.out.println("M07 research browser save/reload core parity: OK ("
                + assertions + " assertions)");
    }
}
