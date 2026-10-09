package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;

/**
 * Bytecode-derived BETA26 theory cards:
 * CardBalance, CardNotation, CardStudy, CardExperimentation.
 *
 * This is their authentic loader-neutral logic, not a generic substitute.
 * The outer research table still decides available slots, inventory
 * requirements, inspiration cost and timing of server-side card activation.
 */
public final class LegacyAdditionalTheoryCards {
    private LegacyAdditionalTheoryCards() {}

    public static final String BALANCE_KEY =
            "thaumcraft.api.research.theorycraft.CardBalance";
    public static final String NOTATION_KEY =
            "thaumcraft.api.research.theorycraft.CardNotation";
    public static final String STUDY_KEY =
            "thaumcraft.api.research.theorycraft.CardStudy";
    public static final String EXPERIMENTATION_KEY =
            "thaumcraft.api.research.theorycraft.CardExperimentation";

    public static final int BALANCE_COST = 1;
    public static final int NOTATION_COST = 1;
    public static final int STUDY_COST = 1;
    public static final int EXPERIMENTATION_COST = 2;

    /** CardStudy and CardNotation are aid-only in the original registry. */
    public static boolean studyAidOnly() { return true; }
    public static boolean notationAidOnly() { return true; }
    public static boolean balanceAidOnly() { return false; }
    public static boolean experimentationAidOnly() { return false; }

    public interface BoundedRandom {
        int nextInt(int bound);
    }

    public static BoundedRandom fromJavaRandom(Random random) {
        Objects.requireNonNull(random, "random");
        return random::nextInt;
    }

    /** Original CardBalance.initialize; sum/count check is not a >0 test. */
    public static boolean canBalance(LegacyResearchTableData table) {
        Objects.requireNonNull(table, "table");
        int sum = 0;
        int count = 0;
        for (Map.Entry<String, Integer> entry : table.categoryTotals().entrySet()) {
            if (table.blockedCategories().contains(entry.getKey())) continue;
            sum += entry.getValue();
            count++;
        }
        return table.blockedCategories().size() < table.categoryTotals().size() - 1
                && sum >= count;
    }

    /**
     * CardBalance.activate replaces all unblocked category totals by the
     * average (INTEGER division), then adds 5 BASICS and +1 penaltyStart.
     * It uses TreeMap.put, not addTotal, so zero averages are retained.
     */
    public static boolean balance(LegacyResearchTableData table) {
        Objects.requireNonNull(table, "table");
        if (!canBalance(table)) return false;
        int sum = 0;
        int count = 0;
        for (Map.Entry<String, Integer> entry : table.categoryTotals().entrySet()) {
            if (table.blockedCategories().contains(entry.getKey())) continue;
            sum += entry.getValue();
            count++;
        }
        if (count == 0) return false;
        // ResearchTableData.addTotal would be wrong when the rounded average
        // reaches zero; the original directly writes to its TreeMap.
        table.replaceTotalsForBalance(sum / count);
        table.addTotal("BASICS", 5);
        table.addPenaltyStart(1);
        return true;
    }

    /**
     * CardNotation.initialize takes the earliest TreeMap category with
     * minimum theory points and earliest category with maximum. Equal
     * amounts at every category reject the card (same min and max key).
     */
    public record Notation(String weakest, String strongest) {
        public Notation {
            Objects.requireNonNull(weakest, "weakest");
            Objects.requireNonNull(strongest, "strongest");
        }
    }

    public static Notation initializeNotation(LegacyResearchTableData table) {
        Objects.requireNonNull(table, "table");
        if (table.categoryTotals().size() < 2) return null;
        int min = Integer.MAX_VALUE;
        int max = 0;
        String weakest = "";
        String strongest = "";
        for (Map.Entry<String, Integer> e : table.categoryTotals().entrySet()) {
            int value = e.getValue();
            if (value < min) {
                min = value;
                weakest = e.getKey();
            }
            if (value > max) {
                max = value;
                strongest = e.getKey();
            }
        }
        return weakest.equals(strongest) || min <= 0
                ? null : new Notation(weakest, strongest);
    }

    /**
     * CardNotation drains the entire weakest category, transfers
     * floor(weakest/2) + uniform(0..floor(weakest/2)) to the strongest.
     * No other theory category or penalty counter is modified.
     */
    public static boolean notation(LegacyResearchTableData table, Notation note,
                                   BoundedRandom random) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(random, "random");
        if (!table.hasTotal(note.weakest()) || !table.hasTotal(note.strongest())
                || note.weakest().equals(note.strongest())) return false;
        int amount = table.getTotal(note.weakest());
        if (amount <= 0) return false;
        int half = amount / 2;
        table.addTotal(note.weakest(), -amount);
        table.addTotal(note.strongest(), half + random.nextInt(half + 1));
        return true;
    }

    /**
     * CardStudy.initialize selects one available category by Java Random
     * seeded with the per-card long seed. Only research-aid draw branch can
     * produce this card.
     */
    public static String initializeStudy(long seed, List<String> availableCategories) {
        Objects.requireNonNull(availableCategories, "availableCategories");
        if (availableCategories.isEmpty()) return null; // Original nextInt(0) throws.
        Random seeded = new Random(seed);
        return availableCategories.get(seeded.nextInt(availableCategories.size()));
    }

    /** CardStudy.activate grants uniformly 15..25 points to its category. */
    public static boolean study(LegacyResearchTableData table, String category,
                                BoundedRandom playerRandom) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(playerRandom, "playerRandom");
        if (category == null || category.isEmpty()) return false;
        table.addTotal(category, 15 + playerRandom.nextInt(11));
        return true;
    }

    /**
     * CardExperimentation picks ANY category in ORIGINAL registration order,
     * independent of unlock or block; then awards 15..30 to the sampled
     * category and a separate 1..10 to BASICS (three player RNG calls).
     */
    public static boolean experiment(LegacyResearchTableData table,
                                     List<String> registeredCategoryOrder,
                                     BoundedRandom playerRandom) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(registeredCategoryOrder, "registeredCategoryOrder");
        Objects.requireNonNull(playerRandom, "playerRandom");
        if (registeredCategoryOrder.isEmpty()) return false;
        String chosen = registeredCategoryOrder.get(
                playerRandom.nextInt(registeredCategoryOrder.size()));
        if (chosen == null || chosen.isEmpty()) return false;
        table.addTotal(chosen, 15 + playerRandom.nextInt(16));
        table.addTotal("BASICS", 1 + playerRandom.nextInt(10));
        return true;
    }
}
