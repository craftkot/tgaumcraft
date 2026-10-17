package dev.ehtid.thaumcraft.port.core;

import java.util.Map;

/** Original seeded options cannot be replaced by shape-valid forged NBT. */
public final class LegacyTheorycraftSeedDataIntegrityParityTest {
    private LegacyTheorycraftSeedDataIntegrityParityTest() {}

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    public static void main(String[] ignored) {
        AspectRegistry registry = new AspectRegistry();
        int synthesisCases = 0;
        for (long seed = 0; seed < 200; seed++) {
            var one = LegacyAlchemyTheoryCards.selectCompound(registry, seed);
            check(one != null, "no compound aspects");
            for (String key : new String[]{"CardConcentrate", "CardChannel"}) {
                check(LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(key, seed,
                        Map.of("aspect", one.tag()), registry), key + " exact");
                check(!LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(key, seed,
                        Map.of("aspect", "forged_invalid_aspect"), registry), key + " forged");
            }

            var reaction = LegacyAlchemyTheoryCards.initializeReactions(registry, seed);
            check(reaction != null, "no reaction aspects");
            check(LegacyTheorycraftSeedDataIntegrity.matchesAlchemy("CardReactions", seed,
                    Map.of("aspect1", reaction.first().tag(),
                           "aspect2", reaction.second().tag()), registry), "reaction exact");
            check(!LegacyTheorycraftSeedDataIntegrity.matchesAlchemy("CardReactions", seed,
                    Map.of("aspect1", reaction.second().tag(),
                           "aspect2", reaction.first().tag()), registry), "reaction swapped");

            var synthesis = LegacyAlchemyTheoryCards.initializeSynthesis(registry, seed);
            if (synthesis != null) {
                synthesisCases++;
                var actual = Map.of("aspect1", synthesis.component1().tag(),
                        "aspect2", synthesis.component2().tag(),
                        "aspect3", synthesis.result().tag());
                check(LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(
                        "CardSynthesis", seed, actual, registry), "synthesis exact");
                check(!LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(
                        "CardSynthesis", seed, Map.of(
                                "aspect1", synthesis.component1().tag(),
                                "aspect2", synthesis.component2().tag(),
                                "aspect3", "forged_invalid_aspect"), registry),
                        "synthesis forged result");
            }

            var notes = LegacyCelestialTheoryCard.initialize(true,
                    Map.of("BASICS", 20), seed);
            check(notes != null, "no celestial notes");
            check(LegacyTheorycraftSeedDataIntegrity.matchesCelestialNotes(seed,
                    notes.firstMetadata(), notes.secondMetadata()), "celestial exact");
            check(!LegacyTheorycraftSeedDataIntegrity.matchesCelestialNotes(seed,
                    notes.secondMetadata(), notes.firstMetadata()), "celestial swapped");
            check(!LegacyTheorycraftSeedDataIntegrity.matchesCelestialNotes(seed,
                    notes.firstMetadata(), notes.firstMetadata()), "celestial duplicate");
        }
        check(synthesisCases > 0, "no synthesis exercised");
        check(!LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(
                "CardUnknown", 0L, Map.of(), registry), "unknown card");
        check(!LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(
                "CardChannel", 0L, null, registry), "null fields");
        check(!LegacyTheorycraftSeedDataIntegrity.matchesCelestialNotes(
                0L, -1, 13), "invalid celestial metadata");
        System.out.println("BETA26 seeded alchemy and celestial card integrity: OK");
    }
}
