package dev.ehtid.thaumcraft.port.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * BETA26 M07 helper -> inspiration -> original weighted card pool -> draw.
 * This is a core parity/NeoForge wiring regression, NOT a gameplay proof.
 */
public final class ResearchAidTheoryFlowParityTest {
    private static final String COMMON = "thaumcraft.common.lib.research.theorycraft.";
    private static final String ALCHEMY = COMMON + "AidBasicAlchemy";
    private static final String INFUSION = COMMON + "AidBasicInfusion";
    private static int assertions;

    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }

    private static final class FixedRolls implements LegacyTheorycraftDrawEngine.RandomSource {
        private long seed = 100L;
        @Override public float nextFloat() { return 0.0F; } // try a selected aid
        @Override public int nextInt(int bound) { return 0; } // first remaining aid
        @Override public long nextLong() { return seed++; }
    }

    private static void checkOriginalAidFlow(String aid, List<String> originalPool) {
        var theory = LegacyTheorycraftInspiration.start(new PlayerKnowledgeData(),
                BuiltInResearchCatalog.create(), Set.of(aid));
        check(theory.inspirationStart() == 5, "new BETA26 player's initial inspiration");
        check(theory.inspiration() == 4, "exactly one inspiration debited for one aid");
        check(theory.aidCards().equals(originalPool), "exact original helper card pool/order");
        check(theory.aidCards().size() == 3, "three available source cards");

        // Allow only original helper cards: never manufacture a normal-pool
        // substitute when testing this branch of drawCards().
        var result = LegacyTheorycraftDrawEngine.draw(
                2, theory.bonusDraws(), theory.inspiration(),
                theory.aidCards(), List.of(), Set.of(), List.of(),
                new FixedRolls(),
                (key, rng) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key, rng.nextLong(), 1, null, true));
        check(result.choices().size() == 2, "two playable original aid choices");
        check(result.attempts() == 2, "one successful attempt per choice");
        check(result.choices().stream().allMatch(c -> c.fromAid() && !c.selected()),
                "each offer remains identified as a real helper card");
        check(result.choices().get(0).card().key().equals(originalPool.get(0)),
                "first original weighted helper card");
        check(result.choices().get(1).card().key().equals(originalPool.get(1)),
                "second original weighted helper card");
        check(result.choices().get(0).card().seed() == 100L
                        && result.choices().get(1).card().seed() == 101L,
                "independent original card seeds");
        check(result.remainingAidCards().equals(originalPool.subList(2, 3)),
                "only actually offered aids are removed");
        check(theory.aidCards().equals(originalPool),
                "preview engine must not mutate live theory aid pool");

        var degraded = LegacyTheorycraftDrawEngine.draw(
                3, 0, theory.inspiration(), originalPool, List.of(),
                Set.of(), List.of(), new FixedRolls(),
                (key, rng) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key, rng.nextLong(), 1, null, true));
        check(degraded.choices().size() == 2,
                "requesting three without a bonus must degrade to two");
        var bonus = LegacyTheorycraftDrawEngine.draw(
                3, 1, theory.inspiration(), originalPool, List.of(),
                Set.of(), List.of(), new FixedRolls(),
                (key, rng) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key, rng.nextLong(), 1, null, true));
        check(bonus.choices().size() == 3 && bonus.remainingBonusDraws() == 0
                        && bonus.remainingAidCards().isEmpty(),
                "bonus third choice consumes exactly one bonus and one card");
    }

    public static void main(String[] args) throws Exception {
        checkOriginalAidFlow(ALCHEMY, List.of(COMMON + "CardConcentrate",
                COMMON + "CardReactions", COMMON + "CardSynthesis"));
        checkOriginalAidFlow(INFUSION, List.of(COMMON + "CardMeasure",
                COMMON + "CardChannel", COMMON + "CardInfuse"));
        check(LegacyTheorycraftInspiration.start(new PlayerKnowledgeData(),
                BuiltInResearchCatalog.create(), Set.of()).inspiration() == 5,
                "no selected helper means no inspiration debit");

        // Confirm each gameplay marker is emitted from the authoritative
        // block entity, never by passive client GUI rendering.
        String entity = Files.readString(Path.of(
                "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/ResearchTableBlockEntity.java"));
        for (String lane : List.of("ALCHEMY", "INFUSION")) {
            check(entity.contains("M07_" + lane + "_AID_THEORY_CREATED"),
                    lane + " creation evidence hook");
            check(entity.contains("M07_" + lane + "_AID_CARD_DRAWN"),
                    lane + " paid draw evidence hook");
            check(entity.contains("M07_" + lane + "_AID_CARD_ACTIVATED"),
                    lane + " committed activation evidence hook");
        }
        check(entity.indexOf("M07_ALCHEMY_AID_CARD_ACTIVATED")
                        > entity.indexOf("THEORY_CARD_ACTIVATED"),
                "specific aid activation marker must follow successful commit");
        System.out.println("M07 original aid theory flow: OK (" + assertions + " assertions)");
    }
}
