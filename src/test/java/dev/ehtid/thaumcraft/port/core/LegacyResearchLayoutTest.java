package dev.ehtid.thaumcraft.port.core;

import java.util.HashSet;
import java.util.Map;

/** Source geometry/catalog integrity; no fabricated x/y test fixtures. */
public final class LegacyResearchLayoutTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var source = BuiltInResearchCatalog.create();
        check(source.size() == 148, "source catalog count");
        check(LegacyResearchLayout.size() == 148, "all 148 original positions");
        check(LegacyResearchLayout.all().size() == 148, "all() preserves entries");
        check(LegacyResearchLayout.get("missing") == null, "unknown node absent");
        check(LegacyResearchLayout.get("FIRSTSTEPS").x() == 0
                        && LegacyResearchLayout.get("FIRSTSTEPS").y() == 0,
                "BETA26 first steps source origin");
        check(LegacyResearchLayout.get("BASEALCHEMY").x() == 0
                        && LegacyResearchLayout.get("BASEALCHEMY").y() == 0,
                "each category has its own independent source coordinate origin");
        check(LegacyResearchLayout.get("CRYSTALFARMER").x() == -5
                        && LegacyResearchLayout.get("CRYSTALFARMER").y() == 1,
                "original nontrivial research position");
        check(LegacyResearchLayout.category("unknown").isEmpty(), "unknown category empty");

        Map<String, Integer> categoryCounts = Map.of(
                "ALCHEMY", 22, "ARTIFICE", 20, "AUROMANCY", 23,
                "BASICS", 32, "ELDRITCH", 5, "GOLEMANCY", 29,
                "INFUSION", 17);
        for (var cat : categoryCounts.entrySet())
            check(LegacyResearchLayout.category(cat.getKey()).size() == cat.getValue(),
                    "original source category count: " + cat.getKey());

        var seenKeys = new HashSet<String>();
        var occupied = new HashSet<String>();
        for (var node : LegacyResearchLayout.all()) {
            check(seenKeys.add(node.key()), "duplicate research key");
            check(occupied.add(node.category() + ":" + node.x() + ":" + node.y()),
                    "overlapping source nodes in the same category");
            var c = source.get(node.key());
            check(c != null && c.categoryKey().equals(node.category()),
                    "layout node must be from same source BETA26 category");
            check(node.titleKey() != null && !node.titleKey().isBlank(),
                    "source title localization key");
            check(node.iconSpec() != null && !node.iconSpec().isBlank(),
                    "source icon metadata");
        }
        System.out.println("M07 BETA26 exact research layout: OK (" + checks + " assertions)");
    }
}
