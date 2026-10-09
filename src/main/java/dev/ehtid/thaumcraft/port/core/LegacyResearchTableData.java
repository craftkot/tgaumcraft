package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Loader-independent implementation of the BETA26 ResearchTableData bookkeeping
 * and TileResearchTable.finishTheory reward arithmetic.
 *
 * Does not pretend to be a complete research-table block/menu: this is the
 * original logic for a future NeoForge TileResearchTable compatibility layer.
 */
public final class LegacyResearchTableData {
    private final TreeMap<String, Integer> categoryTotals = new TreeMap<>();
    private int inspiration;
    private int inspirationStart;
    private int penaltyStart;
    private int bonusDraws;
    private int placedCards;
    private int aidsChosen;

    public LegacyResearchTableData(int inspirationStart) {
        this.inspirationStart = inspirationStart;
        this.inspiration = inspirationStart;
    }

    /**
     * Persistence subset of the original ResearchTableData compound:
     * inspiration, inspirationStart, placedCards, bonusDraws, aidsChosen,
     * penaltyStart and categoryTotals. CardChoice and aid-card instances
     * require their separate full theorycraft object port.
     */
    public record Snapshot(int inspiration, int inspirationStart,
                           int placedCards, int bonusDraws, int aidsChosen,
                           int penaltyStart, Map<String, Integer> categoryTotals) {
        public Snapshot {
            Objects.requireNonNull(categoryTotals, "categoryTotals");
            categoryTotals = Collections.unmodifiableMap(new TreeMap<>(categoryTotals));
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(inspiration, inspirationStart, placedCards,
                bonusDraws, aidsChosen, penaltyStart, categoryTotals);
    }

    public static LegacyResearchTableData fromSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LegacyResearchTableData data = new LegacyResearchTableData(snapshot.inspirationStart());
        data.inspiration = snapshot.inspiration();
        data.placedCards = snapshot.placedCards();
        data.bonusDraws = snapshot.bonusDraws();
        data.aidsChosen = snapshot.aidsChosen();
        data.penaltyStart = snapshot.penaltyStart();
        // Original NBT deserialize performs TreeMap.put without filtering.
        data.categoryTotals.putAll(snapshot.categoryTotals());
        return data;
    }

    public int getTotal(String category) { return categoryTotals.getOrDefault(category, 0); }
    public Map<String, Integer> categoryTotals() {
        return Collections.unmodifiableMap(categoryTotals);
    }

    /** Original ResearchTableData.addTotal removes keys when sum <= 0. */
    public void addTotal(String category, int amount) {
        Objects.requireNonNull(category, "category");
        int total = getTotal(category) + amount;
        if (total <= 0) categoryTotals.remove(category);
        else categoryTotals.put(category, total);
    }

    /** Original addInspiration caps only the upper bound; negative is allowed. */
    public void addInspiration(int amount) {
        inspiration += amount;
        if (inspiration > inspirationStart) inspiration = inspirationStart;
    }

    public boolean isComplete() { return inspiration <= 0; }
    public int inspiration() { return inspiration; }
    public int inspirationStart() { return inspirationStart; }
    public int penaltyStart() { return penaltyStart; }
    public int bonusDraws() { return bonusDraws; }
    public int placedCards() { return placedCards; }
    public int aidsChosen() { return aidsChosen; }

    /**
     * Effects of a confirmed CardCelestial activation. The caller has already
     * validated/debited the two celestial notes and the card's one inspiration;
     * the original CardCelestial.activate itself changes neither counter.
     */
    public void applyCelestialEffects(LegacyCelestialTheoryCard.Activation outcome,
                                      PlayerWarpData playerWarp) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(playerWarp, "playerWarp");
        addTotal(outcome.category(), outcome.categoryGain());
        if (outcome.eldritchGain() != 0) addTotal("ELDRITCH", outcome.eldritchGain());
        playerWarp.add(WarpType.TEMPORARY, outcome.temporaryWarp());
        penaltyStart += outcome.penaltyStartIncrement();
        bonusDraws += outcome.bonusDrawsIncrement();
    }

    /**
     * Called by the card-selection layer after successfully activating a card.
     * This accounting is separate from CardCelestial.activate itself.
     */
    public void consumeInspirationAndPlaceCard(int cost) {
        addInspiration(-cost);
        placedCards++;
    }

    /**
     * Original TileResearchTable.finishTheory: sort categories by decreasing
     * accumulated total, grant round(total / 100f * THEO.progression) raw THEORY
     * points per category. For rank > penaltyStart apply
     * (int)max(1.0, rounded * 0.666666667). No penalty for first
     * penaltyStart + 1 categories. Insertion tie sorting comes from a TreeMap.
     */
    public Map<String, Integer> calculateTheoryRewards() {
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(categoryTotals.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue())
                .reversed());
        LinkedHashMap<String, Integer> rewards = new LinkedHashMap<>();
        int rank = 0;
        for (Map.Entry<String, Integer> entry : ranked) {
            int awarded = Math.round((entry.getValue() / 100.0f) * KnowledgeType.THEORY.progression());
            if (rank > penaltyStart) {
                awarded = (int) Math.max(1.0, awarded * 0.666666667);
            }
            rewards.put(entry.getKey(), awarded);
            rank++;
        }
        return Collections.unmodifiableMap(rewards);
    }

    public void grantFinishedTheory(PlayerKnowledgeData knowledge) {
        Objects.requireNonNull(knowledge, "knowledge");
        for (Map.Entry<String, Integer> reward : calculateTheoryRewards().entrySet()) {
            knowledge.addKnowledge(KnowledgeType.THEORY, reward.getKey(), reward.getValue());
        }
    }
}
