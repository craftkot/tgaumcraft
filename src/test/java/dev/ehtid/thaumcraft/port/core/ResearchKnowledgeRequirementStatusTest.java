package dev.ehtid.thaumcraft.port.core;

public final class ResearchKnowledgeRequirementStatusTest {
    private static int checks;
    private static void check(boolean ok, String detail) {
        checks++;
        if (!ok) throw new AssertionError(detail);
    }

    public static void main(String[] args) {
        var categories = BuiltInResearchCategories.create(new AspectRegistry());
        var player = new PlayerKnowledgeData();
        String alchemy = "OBSERVATION;ALCHEMY;1";
        var initial = ResearchKnowledgeRequirementStatus.inspect(alchemy, player, categories);
        check(initial != null && !initial.satisfied(), "fresh player cannot satisfy knowledge gate");
        check(initial.type() == KnowledgeType.OBSERVATION, "original observation type");
        check(initial.category().equals("ALCHEMY"), "original category retained");
        check(initial.currentLevels() == 0 && initial.requiredLevels() == 1,
                "source amount is measured in LEVELS, not raw points");

        player.addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 15);
        var partial = ResearchKnowledgeRequirementStatus.inspect(alchemy, player, categories);
        check(!partial.satisfied() && partial.currentLevels() == 0,
                "15/16 raw observation points are not yet one level");
        player.addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 1);
        check(ResearchKnowledgeRequirementStatus.inspect(alchemy, player, categories).satisfied(),
                "16/16 raw points unlock one observation level");

        var theory = ResearchKnowledgeRequirementStatus.inspect("THEORY;ALCHEMY;2", player, categories);
        check(theory != null && !theory.satisfied() && theory.requiredLevels() == 2,
                "distinct THEORY counter requires TWO whole levels");
        player.addKnowledge(KnowledgeType.THEORY, "ALCHEMY", 63);
        check(ResearchKnowledgeRequirementStatus.inspect("THEORY;ALCHEMY;2", player, categories)
                        .currentLevels() == 1, "63/32 yields one whole theory level");
        player.addKnowledge(KnowledgeType.THEORY, "ALCHEMY", 1);
        check(ResearchKnowledgeRequirementStatus.inspect("THEORY;ALCHEMY;2", player, categories)
                        .satisfied(), "64/32 yields two theory levels");

        check(ResearchKnowledgeRequirementStatus.inspect("OBSERVATION;INFUSION;1", player, categories)
                        .currentLevels() == 0, "different categories must not share points");
        check(ResearchKnowledgeRequirementStatus.inspect("OBSERVATION;UNKNOWN;1", player, categories)
                        == null, "unknown category rejected");
        check(ResearchKnowledgeRequirementStatus.inspect("WRONG;BASICS;1", player, categories)
                        == null, "unknown knowledge type rejected");
        check(ResearchKnowledgeRequirementStatus.inspect("THEORY;BASICS;0", player, categories)
                        == null, "non-positive original requirement rejected");
        check(ResearchKnowledgeRequirementStatus.inspect("THEORY;BASICS;abc", player, categories)
                        == null, "non-number amount cannot imply ready");
        check(ResearchKnowledgeRequirementStatus.inspect(null, player, categories) == null,
                "null source rejected");

        // Exercise ALL 171 knowledge requirements from the actual original
        // 148-page / 271-stage BETA26 manifest projection, including its
        // unusual required_knowledgeauram source field.
        var untouched = new PlayerKnowledgeData();
        int sourceKnowledgeGates = 0;
        int auramGates = 0;
        for (var page : LegacyResearchPages.all()) {
            for (var stage : page.stages()) {
                for (String encoded : stage.requiredKnowledge()) {
                    var status = ResearchKnowledgeRequirementStatus.inspect(
                            encoded, untouched, categories);
                    check(status != null && !status.satisfied(),
                            "source knowledge gate must parse and start locked: " + encoded);
                    sourceKnowledgeGates++;
                }
                for (String encoded : stage.auramKnowledge()) {
                    var status = ResearchKnowledgeRequirementStatus.inspect(
                            encoded, untouched, categories);
                    check(status != null && !status.satisfied(),
                            "source auram knowledge gate must parse: " + encoded);
                    sourceKnowledgeGates++;
                    auramGates++;
                }
            }
        }
        check(sourceKnowledgeGates == 171, "171 original BETA26 knowledge gate records");
        check(auramGates == 1, "preserve the one original knowledgeauram field");
        check(untouched.copyKnowledgeRaw().isEmpty(),
                "reading original page requirements must not award knowledge");

        // Roundtrip via the SAME BETA26 knowledge rows consumed by the
        // persistent NeoForge player attachment. No client-side unlocking.
        var decoded = new PlayerKnowledgeData();
        LegacyPlayerDataCodec.decodeKnowledge(
                LegacyPlayerDataCodec.encodeKnowledge(player), decoded,
                BuiltInResearchCatalog.create());
        check(ResearchKnowledgeRequirementStatus.inspect(alchemy, decoded, categories).satisfied(),
                "observation knowledge survives save/reload");
        check(ResearchKnowledgeRequirementStatus.inspect("THEORY;ALCHEMY;2", decoded, categories)
                        .satisfied(), "theory knowledge survives save/reload");
        check(decoded.getResearchList().stream().noneMatch(x -> "ALUMENTUM".equals(x)),
                "inspecting gates must not grant research keys");

        System.out.println("M07 original knowledge requirement status: OK ("
                + checks + " assertions)");
    }
}
