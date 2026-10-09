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
    // BETA26 ResearchTableData.serialize() persists these in separate compound
    // lists. ArrayList ordering and duplicate entries are significant.
    private String player = "";
    private final ArrayList<Long> savedCards = new ArrayList<>();
    private final ArrayList<String> aidCards = new ArrayList<>();
    private final ArrayList<String> categoriesBlocked = new ArrayList<>();
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
     * The original ResearchTableData scalar fields, TreeMap and ordered
     * savedCards/aidCards/categoriesBlocked ArrayLists.
     *
     * CardChoice's nested cardNBT (which varies by subclass) is retained as
     * raw NBT by the NeoForge adapter rather than being flattened or discarded.
     */
    public record Snapshot(int inspiration, int inspirationStart,
                           int placedCards, int bonusDraws, int aidsChosen,
                           int penaltyStart, Map<String, Integer> categoryTotals,
                           String player, List<Long> savedCards, List<String> aidCards,
                           List<String> categoriesBlocked) {
        public Snapshot {
            Objects.requireNonNull(categoryTotals, "categoryTotals");
            categoryTotals = Collections.unmodifiableMap(new TreeMap<>(categoryTotals));
            player = player == null ? "" : player;
            savedCards = List.copyOf(savedCards);
            aidCards = List.copyOf(aidCards);
            categoriesBlocked = List.copyOf(categoriesBlocked);
        }

        /** Compatibility with the earlier seven-field scalar-only snapshot. */
        public Snapshot(int inspiration, int inspirationStart, int placedCards,
                        int bonusDraws, int aidsChosen, int penaltyStart,
                        Map<String, Integer> categoryTotals) {
            this(inspiration, inspirationStart, placedCards, bonusDraws,
                    aidsChosen, penaltyStart, categoryTotals, "", List.of(), List.of(), List.of());
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(inspiration, inspirationStart, placedCards,
                bonusDraws, aidsChosen, penaltyStart, categoryTotals,
                player, savedCards, aidCards, categoriesBlocked);
    }

    public static LegacyResearchTableData fromSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LegacyResearchTableData data = new LegacyResearchTableData(snapshot.inspirationStart());
        data.inspiration = snapshot.inspiration();
        data.placedCards = snapshot.placedCards();
        data.bonusDraws = snapshot.bonusDraws();
        data.aidsChosen = snapshot.aidsChosen();
        data.penaltyStart = snapshot.penaltyStart();
        data.categoryTotals.putAll(snapshot.categoryTotals());
        data.player = snapshot.player();
        data.savedCards.addAll(snapshot.savedCards());
        data.aidCards.addAll(snapshot.aidCards());
        data.categoriesBlocked.addAll(snapshot.categoriesBlocked());
        return data;
    }

    public String player() { return player; }
    public void setPlayer(String name) { player = Objects.requireNonNull(name); }

    public List<Long> savedCards() { return List.copyOf(savedCards); }
    public void replaceSavedCards(List<Long> seeds) {
        savedCards.clear();
        savedCards.addAll(List.copyOf(seeds));
    }

    public List<String> aidCards() { return List.copyOf(aidCards); }
    public void replaceAidCards(List<String> keys) {
        aidCards.clear();
        aidCards.addAll(List.copyOf(keys));
    }

    public List<String> blockedCategories() { return List.copyOf(categoriesBlocked); }
    public void replaceBlockedCategories(List<String> categories) {
        categoriesBlocked.clear();
        categoriesBlocked.addAll(List.copyOf(categories));
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

    /**
     * ResearchTableData.getAvailableCategories iterates registration order,
     * omits blocked category keys and requires strict completion of a
     * category's optional researchKey. A null key is always accessible.
     */
    public static List<String> availableCategories(
            PlayerKnowledgeData knowledge, ResearchCatalog catalog,
            ResearchCategoryRegistry categories, java.util.Set<String> blocked) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(categories, "categories");
        Objects.requireNonNull(blocked, "blocked");
        List<String> available = new ArrayList<>();
        for (ResearchCategoryData category : categories.values()) {
            if (blocked.contains(category.key())) continue;
            if (category.researchKey() == null
                    || knowledge.isResearchComplete(category.researchKey(), catalog)) {
                available.add(category.key());
            }
        }
        return List.copyOf(available);
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
