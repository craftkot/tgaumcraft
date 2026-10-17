package dev.ehtid.thaumcraft.port.core;

import java.util.Map;

public final class LegacyTheorycraftSessionFieldIntegrityParityTest {
    private LegacyTheorycraftSessionFieldIntegrityParityTest() {}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        LegacyResearchTableData session = new LegacyResearchTableData(5);
        session.addTotal("BASICS", 30);
        session.addTotal("ARTIFICE", 15);
        session.addTotal("ALCHEMY", 5);
        var inspired = LegacyTheorycraftMiscCards.initializeInspired(session);
        var notation = LegacyAdditionalTheoryCards.initializeNotation(session);
        check(inspired != null && notation != null, "test totals");
        for (long seed = 0; seed < 200; seed++) {
            check(LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardInspired", seed, Map.of("cat", inspired.category()),
                    Map.of("amt", inspired.amount()), session), "inspired exact");
            check(!LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardInspired", seed, Map.of("cat", inspired.category()),
                    Map.of("amt", inspired.amount() + 100), session), "inspired forged amount");
            check(LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardNotation", seed, Map.of("cat1", notation.weakest(),
                            "cat2", notation.strongest()), Map.of(), session), "notation exact");
            check(!LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardNotation", seed, Map.of("cat1", notation.strongest(),
                            "cat2", notation.weakest()), Map.of(), session), "notation swapped");
            String reject = LegacyTheorycraftMiscCards.initializeReject(session, seed);
            check(reject != null, "reject category exists");
            check(LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardReject", seed, Map.of("cat", reject), Map.of(), session), "reject exact");
            check(!LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardReject", seed, Map.of("cat", "FORGED"), Map.of(), session), "reject forged");
            var celestial = LegacyCelestialTheoryCard.initialize(true,
                    session.categoryTotals(), seed);
            check(celestial != null, "celestial");
            check(LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardCelestial", seed, Map.of("cat", celestial.category()),
                    Map.of(), session), "celestial exact");
            check(!LegacyTheorycraftSessionFieldIntegrity.matches(
                    "CardCelestial", seed, Map.of("cat", "FORGED"), Map.of(), session),
                    "celestial forged category");
        }
        session.addTotal("ARTIFICE", 500);
        check(!LegacyTheorycraftSessionFieldIntegrity.matches(
                "CardInspired", 0, Map.of("cat", inspired.category()),
                Map.of("amt", inspired.amount()), session), "changed totals rejected");
        check(!LegacyTheorycraftSessionFieldIntegrity.matches(
                "CardUnknown", 0, Map.of(), Map.of(), session), "unknown card");
        System.out.println("BETA26 session-dependent theory card integrity: OK");
    }
}
