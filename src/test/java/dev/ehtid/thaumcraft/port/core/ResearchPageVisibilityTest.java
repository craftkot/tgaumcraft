package dev.ehtid.thaumcraft.port.core;

/** Exact BETA26 saved-stage bounds, not creative-mode fabricated unlocks. */
public final class ResearchPageVisibilityTest {
    private static int checks;
    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        var catalog = BuiltInResearchCatalog.create();
        var page = LegacyResearchPages.get("FIRSTSTEPS");
        var knowledge = new PlayerKnowledgeData();
        check(page.stages().size() == 3, "BETA26 FIRSTSTEPS has three source stages");
        check(ResearchPageVisibility.maximumStage(page, knowledge, catalog) == -1,
                "undiscovered page cannot expose any stage");
        knowledge.addResearch("FIRSTSTEPS");
        check(ResearchPageVisibility.maximumStage(page, knowledge, catalog) == 0,
                "fresh discovered research can read only its first stage");
        check(ResearchPageVisibility.clampRequestedStage(80, 0) == 0,
                "cannot navigate to future unreached stages");
        knowledge.setResearchStage("FIRSTSTEPS", 2);
        check(ResearchPageVisibility.maximumStage(page, knowledge, catalog) == 2,
                "known stage 2 grants read access through third source page");
        check(ResearchPageVisibility.clampRequestedStage(8, 2) == 2,
                "bound index by saved progress");
        check(ResearchPageVisibility.clampRequestedStage(-3, 2) == 0,
                "negative stage index clamped");
        knowledge.setResearchStage("FIRSTSTEPS", catalog.stageCount("FIRSTSTEPS") + 1);
        check(ResearchPageVisibility.maximumStage(page, knowledge, catalog) == 2,
                "finished research grants last source page, never beyond");

        // Source file has 148 complete entries with no invented per-stage IDs.
        check(LegacyResearchPages.size() == 148, "source pages count");
        int stages = 0;
        int addons = 0;
        for (var source : LegacyResearchPages.all()) {
            check(LegacyResearchLayout.get(source.key()) != null,
                    "each source page has an actual source node");
            check(source.titleKey().equals(LegacyResearchLayout.get(source.key()).titleKey()),
                    "source title key parity");
            check(source.stages().size() == catalog.stageCount(source.key()),
                    "source stage count matches core catalog");
            for (var stage : source.stages()) {
                check(stage.textKey() != null && !stage.textKey().isBlank(),
                        "stage retains the original localization reference");
                stages++;
            }
            addons += source.addenda().size();
        }
        check(stages == 271, "all BETA26 source stage records accounted for");
        check(addons == 16, "all original research addenda accounted for");
        check(ResearchPageVisibility.clampRequestedStage(1, -1) == -1,
                "unknown research remains inaccessible");
        System.out.println("M07 original research stage visibility: OK ("
                + checks + " assertions)");
    }
}
