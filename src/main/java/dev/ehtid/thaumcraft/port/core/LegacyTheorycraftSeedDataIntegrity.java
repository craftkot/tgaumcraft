package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Verify serialized BETA26 card options derived solely from the persisted
 * seed, before granting research or spending actual inventory items.
 *
 * Other initialization fields (such as CardCelestial.cat) depend on the
 * table's historical state and must NOT be reconstructed from today's
 * mutable session totals. Missing/unported items are handled separately.
 */
public final class LegacyTheorycraftSeedDataIntegrity {
    private LegacyTheorycraftSeedDataIntegrity() {}

    public static boolean matchesAlchemy(String cardName, long seed,
                                         Map<String, String> fields,
                                         AspectRegistry registry) {
        if (cardName == null || fields == null || registry == null) return false;
        return switch (cardName) {
            case "CardConcentrate", "CardChannel" -> {
                AspectData expected = LegacyAlchemyTheoryCards.selectCompound(registry, seed);
                yield expected != null && expected.tag().equals(fields.get("aspect"));
            }
            case "CardReactions" -> {
                var expected = LegacyAlchemyTheoryCards.initializeReactions(registry, seed);
                yield expected != null
                        && expected.first().tag().equals(fields.get("aspect1"))
                        && expected.second().tag().equals(fields.get("aspect2"));
            }
            case "CardSynthesis" -> {
                var expected = LegacyAlchemyTheoryCards.initializeSynthesis(registry, seed);
                yield expected != null
                        && expected.component1().tag().equals(fields.get("aspect1"))
                        && expected.component2().tag().equals(fields.get("aspect2"))
                        && expected.result().tag().equals(fields.get("aspect3"));
            }
            default -> false;
        };
    }

    /**
     * CardCelestial.initialize draws two distinct note metadata values from
     * new java.util.Random(seed), in this exact order. The category is NOT
     * seed-derived: it was selected from historical categoryTotals.
     */
    public static boolean matchesCelestialNotes(long seed, int firstMetadata,
                                                 int secondMetadata) {
        if (firstMetadata < 0 || firstMetadata > 12
                || secondMetadata < 0 || secondMetadata > 12
                || firstMetadata == secondMetadata) return false;
        Random rng = new Random(seed);
        int first = rng.nextInt(13);
        int second;
        do {
            second = rng.nextInt(13);
        } while (second == first);
        return first == firstMetadata && second == secondMetadata;
    }
}
