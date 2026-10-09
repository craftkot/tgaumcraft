package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Original BETA26 CardInspired, CardReject, CardMeasure, CardSculpting and
 * CardAwareness, transcribed from their class bytecode.
 *
 * Their card state/activation results are exact. The full ResearchTable GUI
 * must still use the original item-consumption rules and card selection;
 * these methods are not an alternative mini-game.
 */
public final class LegacyTheorycraftMiscCards {
    private LegacyTheorycraftMiscCards() {}

    public static final String INSPIRED =
            "thaumcraft.api.research.theorycraft.CardInspired";
    public static final String REJECT =
            "thaumcraft.api.research.theorycraft.CardReject";
    public static final String MEASURE =
            "thaumcraft.common.lib.research.theorycraft.CardMeasure";
    public static final String SCULPTING =
            "thaumcraft.common.lib.research.theorycraft.CardSculpting";
    public static final String AWARENESS =
            "thaumcraft.common.lib.research.theorycraft.CardAwareness";

    public static final int INSPIRED_COST = 2;
    public static final int REJECT_COST = 0;
    public static final int MEASURE_COST = 1;
    public static final int SCULPTING_COST = 1;
    public static final int AWARENESS_COST = 1;

    public record Inspired(String category, int amount) {
        public Inspired { Objects.requireNonNull(category); }
    }

    /**
     * CardInspired chooses the highest TreeMap total, earliest key for ties,
     * and stores 10 + (max / 2) in `amt` for persistent cardNBT.
     */
    public static Inspired initializeInspired(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        if (table.categoryTotals().isEmpty()) return null;
        int highest = 0;
        String chosen = "";
        for (Map.Entry<String, Integer> e : table.categoryTotals().entrySet()) {
            if (e.getValue() > highest) {
                highest = e.getValue();
                chosen = e.getKey();
            }
        }
        return new Inspired(chosen, 10 + highest / 2);
    }

    /** CardInspired.activate grants only the amount already saved on the card. */
    public static boolean inspire(LegacyResearchTableData table, Inspired card) {
        Objects.requireNonNull(table);
        if (card == null) return false;
        table.addTotal(card.category(), card.amount());
        return true;
    }

    /** CardReject chooses any currently unblocked existing category. */
    public static String initializeReject(LegacyResearchTableData table, long cardSeed) {
        Objects.requireNonNull(table);
        List<String> choices = new ArrayList<>();
        for (String key : table.categoryTotals().keySet()) {
            if (!table.blockedCategories().contains(key)) choices.add(key);
        }
        if (choices.isEmpty()) return null;
        return choices.get(new Random(cardSeed).nextInt(choices.size()));
    }

    /**
     * CardReject.activate: 5 BASICS, then append its saved target key to
     * categoriesBlocked. The original card does NOT deduct current points.
     */
    public static boolean reject(LegacyResearchTableData table, String category) {
        Objects.requireNonNull(table);
        if (category == null || !table.hasTotal(category)
                || table.blockedCategories().contains(category)) return false;
        table.addTotal("BASICS", 5);
        table.appendBlockedCategory(category);
        return true;
    }

    /** Original CardMeasure: +15 INFUSION and +1 bonusDraws. */
    public static void measure(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addTotal("INFUSION", 15);
        table.addBonusDraws(1);
    }

    /**
     * CardSculpting: +20 GOLEMANCY and +1 bonusDraws. Caller must verify and
     * consume the original getRequiredItems() ingredient before activation.
     */
    public static void sculpt(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addTotal("GOLEMANCY", 20);
        table.addBonusDraws(1);
    }

    public interface AwarenessRandom {
        float nextFloat();
        int nextInt(int bound);
    }

    public static AwarenessRandom fromJavaRandom(Random random) {
        Objects.requireNonNull(random);
        return new AwarenessRandom() {
            @Override public float nextFloat() { return random.nextFloat(); }
            @Override public int nextInt(int bound) { return random.nextInt(bound); }
        };
    }

    /**
     * Original CardAwareness: +20 AUROMANCY. If float RNG < 0.33, add 1..5
     * ELDRITCH theory and exactly ONE NORMAL warp (not temporary/permanent).
     * The conditional roll is only consumed on the <0.33 branch.
     */
    public static void awareness(LegacyResearchTableData table, PlayerWarpData warp,
                                 AwarenessRandom random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(random);
        table.addTotal("AUROMANCY", 20);
        if ((double) random.nextFloat() < 0.33D) {
            table.addTotal("ELDRITCH", 1 + random.nextInt(5));
            warp.add(WarpType.NORMAL, 1);
        }
    }
}
