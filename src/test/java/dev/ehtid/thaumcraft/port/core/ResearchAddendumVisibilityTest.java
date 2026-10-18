package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** BETA26 addenda: no unread-page leaks and no cross-sibling unlocks. */
public final class ResearchAddendumVisibilityTest {
    private static int checks;
    private static void check(boolean test, String message) {
        checks++;
        if (!test) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var catalog = BuiltInResearchCatalog.create();
        var player = new PlayerKnowledgeData();
        var ores = LegacyResearchPages.get("ORE");
        check(ores.addenda().size() == 3, "three original ore addenda");
        check(ResearchAddendumVisibility.visible(ores, player, catalog).isEmpty(),
                "unknown research reveals no addenda");

        player.addResearch("ORE");
        player.setResearchFlag("ORE", ResearchFlag.PAGE);
        check(ResearchAddendumVisibility.visible(ores, player, catalog).isEmpty(),
                "PAGE alone must not unlock an unfinished research entry");
        player.setResearchStage("ORE", catalog.stageCount("ORE") + 1);
        check(ResearchAddendumVisibility.visible(ores, player, catalog).isEmpty(),
                "PAGE alone does not unlock siblings");

        player.addResearch("!OREAMBER");
        var first = ResearchAddendumVisibility.visible(ores, player, catalog);
        check(first.size() == 1
                && first.get(0).textKey().equals("research.ORE.amber"),
                "only the discovered amber entry is visible");
        player.addResearch("!ORECRYSTAL");
        var two = ResearchAddendumVisibility.visible(ores, player, catalog);
        check(two.size() == 2
                && two.get(1).textKey().equals("research.ORE.crystal"),
                "crystal cannot disclose cinnabar addendum");

        // Same attachment codec as used by NeoForge, preserving source
        // completed stage + PAGE flag + historical scan research markers.
        var nbt = LegacyPlayerDataCodec.encodeKnowledge(player);
        var reloaded = new PlayerKnowledgeData();
        LegacyPlayerDataCodec.decodeKnowledge(nbt, reloaded, catalog);
        check(ResearchAddendumVisibility.visible(ores, reloaded, catalog).equals(two),
                "two unlocked addenda survive save/load");

        reloaded.clearResearchFlag("ORE", ResearchFlag.PAGE);
        check(ResearchAddendumVisibility.visible(ores, reloaded, catalog).isEmpty(),
                "removing server-authoritative PAGE hides all addenda");

        // A different player cannot inherit scan discoveries from the first.
        var other = new PlayerKnowledgeData();
        other.addResearch("ORE");
        other.setResearchStage("ORE", catalog.stageCount("ORE") + 1);
        other.setResearchFlag("ORE", ResearchFlag.PAGE);
        check(ResearchAddendumVisibility.visible(ores, other, catalog).isEmpty(),
                "fresh player cannot see other player's scan addenda");

        // A standalone source quest addendum is also gated by a completed
        // milestone (e.g. BELLOWS for ESSENTIASMELTER).
        var smelter = LegacyResearchPages.get("ESSENTIASMELTER");
        check(smelter.addenda().size() == 1, "smelter original addendum");
        var third = new PlayerKnowledgeData();
        third.addResearch("ESSENTIASMELTER");
        third.setResearchStage("ESSENTIASMELTER",
                catalog.stageCount("ESSENTIASMELTER") + 1);
        third.setResearchFlag("ESSENTIASMELTER", ResearchFlag.PAGE);
        third.addResearch("BELLOWS");
        check(ResearchAddendumVisibility.visible(smelter, third, catalog).isEmpty(),
                "partially known BELLOWS does not meet strict research prerequisite");
        third.setResearchStage("BELLOWS", catalog.stageCount("BELLOWS") + 1);
        check(ResearchAddendumVisibility.visible(smelter, third, catalog).size() == 1,
                "completed BELLOWS unlocks only the real original addendum");

        int total = 0;
        for (var page : LegacyResearchPages.all()) total += page.addenda().size();
        check(total == 16, "exact 16 source addenda, none fabricated");

        System.out.println("M07 saved PAGE/addenda read-only parity: OK ("
                + checks + " assertions)");
    }
}
