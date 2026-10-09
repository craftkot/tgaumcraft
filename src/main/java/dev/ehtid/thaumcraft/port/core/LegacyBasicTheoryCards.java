package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

/**
 * Exact BETA26 theorycraft CardPonder and CardRethink state transitions,
 * reconstructed from the original 1.12.2 class bytecode.
 *
 * These cards are not substitute/placeholder cards. When their original
 * registered classes are presented by drawCards, the eventual NeoForge
 * card-choice dispatcher may use these functions with the real table state.
 */
public final class LegacyBasicTheoryCards {
    private LegacyBasicTheoryCards() {}

    public static final String PONDER_KEY =
            "thaumcraft.api.research.theorycraft.CardPonder";
    public static final String RETHINK_KEY =
            "thaumcraft.api.research.theorycraft.CardRethink";
    public static final int PONDER_INSPIRATION_COST = 2;
    public static final int RETHINK_INSPIRATION_COST = -1;

    /**
     * CardPonder.initialize compares the NUMBER of blocked category entries
     * with the number of nonzero theory categories, not the intersection.
     */
    public static boolean canPonder(LegacyResearchTableData table) {
        Objects.requireNonNull(table, "table");
        return table.blockedCategories().size() < table.categoryTotals().size();
    }

    /**
     * CardPonder.activate distributes 25 points over unblocked categories
     * in TreeMap order with a 1000-pass guard, then adds 5 BASICS and one
     * bonusDraw. The original returns FALSE only if exactly 20 points remain.
     * This unusual return value must not be "fixed" or normalized.
     */
    public static boolean ponder(LegacyResearchTableData table) {
        Objects.requireNonNull(table, "table");
        int remaining = 25;
        int loops = 0;
        while (remaining > 0 && loops < 1000) {
            loops++;
            for (String key : new ArrayList<>(table.categoryTotals().keySet())) {
                if (table.blockedCategories().contains(key)) {
                    if (table.categoryTotals().size() <= 1) return false;
                } else {
                    table.addTotal(key, 1);
                    remaining--;
                    if (remaining <= 0) break;
                }
            }
        }
        table.addTotal("BASICS", 5);
        table.addBonusDraws(1);
        return remaining != 20;
    }

    /** Original CardRethink.initialize: sum of theory totals must be >=10. */
    public static boolean canRethink(LegacyResearchTableData table) {
        Objects.requireNonNull(table, "table");
        int sum = 0;
        for (Integer value : table.categoryTotals().values()) sum += value;
        return sum >= 10;
    }

    /**
     * CardRethink.activate subtracts at most ten theory points in TreeMap
     * iteration order. If an entry disappears after reaching zero, the inner
     * loop stops, so the next outer pass gets a fresh iterator.
     *
     * On success it adds a bonus draw and 1..10 BASICS with the player's
     * actual RandomSource (one nextInt(10) call). Inspiration cost -1 is
     * charged separately by ContainerResearchTable after success.
     */
    public static boolean rethink(LegacyResearchTableData table,
                                  LegacyCelestialTheoryCard.BoundedRandom random) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(random, "random");
        if (!canRethink(table)) return false;
        int total = 0;
        for (int value : table.categoryTotals().values()) total += value;
        int remaining = Math.min(total, 10);
        int loops = 0;
        while (remaining > 0 && loops < 1000) {
            loops++;
            for (String key : new ArrayList<>(table.categoryTotals().keySet())) {
                table.addTotal(key, -1);
                remaining--;
                if (remaining <= 0 || !table.hasTotal(key)) break;
            }
        }
        table.addBonusDraws(1);
        table.addTotal("BASICS", random.nextInt(10) + 1);
        return true;
    }

    public static boolean rethink(LegacyResearchTableData table, Random random) {
        Objects.requireNonNull(random, "random");
        return rethink(table, (LegacyCelestialTheoryCard.BoundedRandom) random::nextInt);
    }
}
