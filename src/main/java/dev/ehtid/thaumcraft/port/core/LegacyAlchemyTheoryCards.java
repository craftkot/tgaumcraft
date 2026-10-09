package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Exact BETA26 CardConcentrate, CardChannel, CardReactions, CardSynthesis:
 * the aspect selection uses new Random(card.seed) on the original ordered
 * Aspect.getCompoundAspects registry. ItemStack requirements are returned as
 * AspectData signatures, not guessed or consumed by this pure Java layer.
 */
public final class LegacyAlchemyTheoryCards {
    private LegacyAlchemyTheoryCards() {}

    public static final String CONCENTRATE =
            "thaumcraft.common.lib.research.theorycraft.CardConcentrate";
    public static final String CHANNEL =
            "thaumcraft.common.lib.research.theorycraft.CardChannel";
    public static final String REACTIONS =
            "thaumcraft.common.lib.research.theorycraft.CardReactions";
    public static final String SYNTHESIS =
            "thaumcraft.common.lib.research.theorycraft.CardSynthesis";

    public static final int INSPIRATION_COST = 1;

    public static AspectData selectCompound(AspectRegistry aspects, long cardSeed) {
        Objects.requireNonNull(aspects, "aspects");
        List<AspectData> compounds = aspects.compoundAspects();
        if (compounds.isEmpty()) return null;
        return compounds.get(new Random(cardSeed).nextInt(compounds.size()));
    }

    /** BETA26 CardReactions uses two DISTINCT compound aspect crystal notes. */
    public record Reaction(AspectData first, AspectData second) {
        public Reaction {
            Objects.requireNonNull(first);
            Objects.requireNonNull(second);
            if (first == second) throw new IllegalArgumentException("distinct aspects");
        }
    }

    public static Reaction initializeReactions(AspectRegistry aspects, long seed) {
        Objects.requireNonNull(aspects);
        List<AspectData> compounds = aspects.compoundAspects();
        if (compounds.size() < 2) return null;
        Random random = new Random(seed);
        int firstIndex = random.nextInt(compounds.size());
        int secondIndex = firstIndex;
        while (secondIndex == firstIndex) secondIndex = random.nextInt(compounds.size());
        return new Reaction(compounds.get(firstIndex), compounds.get(secondIndex));
    }

    /**
     * CardSynthesis picks a compound aspect and its two EXACT component
     * aspects for consumed crystals; on activation returns a crystal of the
     * compound aspect. The eventual NeoForge bridge must consume BOTH
     * component crystals, add/drop the resultant one, and commit theory.
     */
    public record Synthesis(AspectData component1, AspectData component2,
                            AspectData result) {
        public Synthesis {
            Objects.requireNonNull(component1);
            Objects.requireNonNull(component2);
            Objects.requireNonNull(result);
        }
    }

    public static Synthesis initializeSynthesis(AspectRegistry aspects, long seed) {
        AspectData result = selectCompound(aspects, seed);
        if (result == null || result.components() == null
                || result.components().size() != 2) return null;
        return new Synthesis(result.components().get(0),
                result.components().get(1), result);
    }

    /** Match Java's float->double < 0.33 check from the BETA26 bytecode. */
    public static boolean refundInspiration(float randomFloat) {
        return (double) randomFloat < 0.33D;
    }

    /**
     * CardConcentrate requires one aspect crystal (NOT consumed according to
     * the inherited default); +15 ALCHEMY, +1 bonusDraw, 33% +1 inspiration.
     */
    public static void concentrate(LegacyResearchTableData table,
                                   LegacyTheorycraftMiscCards.AwarenessRandom random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(random);
        table.addTotal("ALCHEMY", 15);
        table.addBonusDraws(1);
        if (refundInspiration(random.nextFloat())) table.addInspiration(1);
    }

    /** CardChannel requires a filled phial of chosen aspect, +25 INFUSION. */
    public static void channel(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addTotal("INFUSION", 25);
    }

    /**
     * CardReactions requires two distinct aspect crystals, +25 ALCHEMY,
     * 33% +1 inspiration. Items are NON-CONSUMABLE by default.
     */
    public static void reactions(LegacyResearchTableData table,
                                 LegacyTheorycraftMiscCards.AwarenessRandom random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(random);
        table.addTotal("ALCHEMY", 25);
        if (refundInspiration(random.nextFloat())) table.addInspiration(1);
    }

    /**
     * CardSynthesis requires and CONSUMES two component crystals; +40 ALCHEMY,
     * 33% +1 inspiration and returns its selected COMPOUND crystal.
     *
     * Caller must validate both component items (including same-aspect count
     * when appropriate) before invoking and handle inventory/drop atomically.
     */
    public static AspectData synthesisAfterInputsConsumed(
            LegacyResearchTableData table, Synthesis synthesis,
            LegacyTheorycraftMiscCards.AwarenessRandom random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(random);
        if (synthesis == null) return null;
        table.addTotal("ALCHEMY", 40);
        if (refundInspiration(random.nextFloat())) table.addInspiration(1);
        return synthesis.result();
    }
}
