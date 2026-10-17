package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
import java.util.Objects;

/**
 * Validate card initialization fields derived from the CURRENT theory totals
 * instead of trusting shape-valid saved NBT to assign a forged reward.
 *
 * These checks apply only BEFORE the first choice activation. The caller
 * must reject already-selected offers before granting any card effect.
 * Other cards can depend on historical player observations, research unlocks
 * or inventory changes and must not be validated with today's values.
 */
public final class LegacyTheorycraftSessionFieldIntegrity {
    private LegacyTheorycraftSessionFieldIntegrity() {}

    public static boolean matches(String cardName, long seed, Map<String, String> texts,
                                  Map<String, Integer> numbers,
                                  LegacyResearchTableData session) {
        if (cardName == null || texts == null || numbers == null || session == null)
            return false;
        return switch (cardName) {
            case "CardInspired" -> {
                var expected = LegacyTheorycraftMiscCards.initializeInspired(session);
                yield expected != null && Objects.equals(expected.category(), texts.get("cat"))
                        && Objects.equals(expected.amount(), numbers.get("amt"));
            }
            case "CardNotation" -> {
                var expected = LegacyAdditionalTheoryCards.initializeNotation(session);
                yield expected != null && Objects.equals(expected.weakest(), texts.get("cat1"))
                        && Objects.equals(expected.strongest(), texts.get("cat2"));
            }
            case "CardReject" -> {
                String expected = LegacyTheorycraftMiscCards.initializeReject(session, seed);
                yield expected != null && expected.equals(texts.get("cat"));
            }
            case "CardCelestial" -> {
                // Two note metadata values are checked independently against
                // the seed. Its category depended on historical TreeMap totals.
                var expected = LegacyCelestialTheoryCard.initialize(
                        true, session.categoryTotals(), seed);
                yield expected != null && expected.category().equals(texts.get("cat"));
            }
            default -> false;
        };
    }
}
