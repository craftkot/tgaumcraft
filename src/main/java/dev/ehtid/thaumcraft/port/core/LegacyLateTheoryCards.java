package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * BETA26 bytecode-verified effects of the seven XP, aid and ELDRITCH theory cards.
 * Each method models card activation only: it does NOT make a card drawable or
 * clickable in the NeoForge research-table GUI. Costs are paid by the caller,
 * not from this loader-independent table state.
 *
 * References: original CardEnchantment, CardBeacon, CardSpellbinding,
 * CardDarkWhispers, CardPortal, CardRevelation and CardRealization.activate.
 */
public final class LegacyLateTheoryCards {
    private LegacyLateTheoryCards() {}

    public static final String ENCHANTMENT = "thaumcraft.common.lib.research.theorycraft.CardEnchantment";
    public static final String BEACON = "thaumcraft.common.lib.research.theorycraft.CardBeacon";
    public static final String SPELLBINDING = "thaumcraft.common.lib.research.theorycraft.CardSpellbinding";
    public static final String DARK_WHISPERS = "thaumcraft.common.lib.research.theorycraft.CardDarkWhispers";
    public static final String PORTAL = "thaumcraft.common.lib.research.theorycraft.CardPortal";
    public static final String REVELATION = "thaumcraft.common.lib.research.theorycraft.CardRevelation";
    public static final String REALIZATION = "thaumcraft.common.lib.research.theorycraft.CardRealization";

    /** Exact per-card inspiration costs; negative values refund inspiration. */
    public static int inspirationCost(String card) {
        return switch (card) {
            case ENCHANTMENT, SPELLBINDING, DARK_WHISPERS, REVELATION, REALIZATION -> 1;
            case BEACON -> -2;
            case PORTAL -> -1;
            default -> throw new IllegalArgumentException("Unrecognized BETA26 card: " + card);
        };
    }

    /** In BETA26 these four cards can only be drawn from nearby aid blocks. */
    public static boolean aidOnly(String card) {
        return ENCHANTMENT.equals(card) || BEACON.equals(card)
                || DARK_WHISPERS.equals(card) || PORTAL.equals(card);
    }

    /** CardEnchantment spends five player XP levels or fails without rolling. */
    public static boolean canEnchant(int playerLevels) {
        return playerLevels >= 5;
    }

    /** Call ONLY after the server has successfully deducted five XP levels. */
    public static void enchantmentAfterFiveLevelsSpent(LegacyResearchTableData table, Random random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(random);
        table.addTotal("INFUSION", 15 + random.nextInt(6));
        table.addTotal("AUROMANCY", 15 + random.nextInt(6));
    }

    /** CardBeacon is aid-only, adds a bonus draw and a finishing penalty. */
    public static void beacon(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addBonusDraws(1);
        table.addPenaltyStart(1);
    }

    /** CardSpellbinding.initialize requires strictly positive player XP levels. */
    public static int spellbindingLevels(int playerLevels) {
        return Math.min(5, Math.max(0, playerLevels));
    }

    /** Call ONLY after the server has deducted the specified 1..5 XP levels. */
    public static void spellbindingAfterLevelsSpent(LegacyResearchTableData table, int spent) {
        Objects.requireNonNull(table);
        if (spent < 1 || spent > 5) {
            throw new IllegalArgumentException("BETA26 Spellbinding spends 1..5 levels");
        }
        table.addTotal("AUROMANCY", spent * 5);
    }

    /**
     * Original CardDarkWhispers computes gains from player XP level BEFORE it
     * removes all those levels plus ten, so there is no affordability gate.
     * The server adapter must snapshot XP and debit -(levels + 10) first.
     * Returns the original debit amount for the adapter.
     */
    public static int darkWhispersLevelDebit(int originalLevels) {
        if (originalLevels < 0) throw new IllegalArgumentException("XP levels");
        return originalLevels + 10;
    }

    public static void darkWhispersAfterXpDebited(
            LegacyResearchTableData table, PlayerWarpData warp,
            int originalLevels, List<String> registeredCategories, Random random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(registeredCategories);
        Objects.requireNonNull(random);
        if (originalLevels < 0) throw new IllegalArgumentException("XP levels");

        // Original LinkedHashMap keySet iteration; a nextBoolean per category
        // only when originalLevels>0, and an extra nextInt on a false result.
        if (originalLevels > 0) {
            int max = Math.max(1, (int) Math.sqrt(originalLevels));
            for (String category : registeredCategories) {
                if (!random.nextBoolean()) {
                    table.addTotal(category, random.nextInt(max + 1));
                }
            }
        }
        int minEldritch = Math.max(1, originalLevels / 5);
        int maxEldritch = Math.max(5, originalLevels / 2);
        table.addTotal("ELDRITCH", minEldritch + random.nextInt(maxEldritch - minEldritch + 1));
        warp.add(WarpType.NORMAL, Math.max(1, (int) Math.sqrt(originalLevels)));
        if (random.nextBoolean()) table.addBonusDraws(1);
    }

    /** CardPortal: two independent registered-category rolls, then ELDRITCH. */
    public static boolean portal(LegacyResearchTableData table, PlayerWarpData warp,
                                 List<String> registeredCategories, Random random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(registeredCategories);
        Objects.requireNonNull(random);
        if (registeredCategories.isEmpty()) return false;
        for (int i = 0; i < 2; i++) {
            String category = registeredCategories.get(random.nextInt(registeredCategories.size()));
            table.addTotal(category, 5 + random.nextInt(6));
        }
        table.addTotal("ELDRITCH", 5 + random.nextInt(6));
        table.addBonusDraws(2);
        warp.add(WarpType.TEMPORARY, 5);
        warp.add(WarpType.NORMAL, 1);
        return true;
    }

    /** CardRevelation: one category roll, +30 ELDRITCH and unavoidable warp. */
    public static boolean revelation(LegacyResearchTableData table, PlayerWarpData warp,
                                     List<String> registeredCategories, Random random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(registeredCategories);
        Objects.requireNonNull(random);
        if (registeredCategories.isEmpty()) return false;
        table.addTotal(registeredCategories.get(random.nextInt(registeredCategories.size())),
                5 + random.nextInt(6));
        table.addTotal("ELDRITCH", 30);
        warp.add(WarpType.TEMPORARY, 5);
        warp.add(WarpType.NORMAL, 1);
        table.addPenaltyStart(1);
        return true;
    }

    /** CardRealization: two independent category rolls and 50% normal warp. */
    public static boolean realization(LegacyResearchTableData table, PlayerWarpData warp,
                                      List<String> registeredCategories, Random random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(registeredCategories);
        Objects.requireNonNull(random);
        if (registeredCategories.isEmpty()) return false;
        for (int i = 0; i < 2; i++) {
            table.addTotal(registeredCategories.get(random.nextInt(registeredCategories.size())),
                    5 + random.nextInt(6));
        }
        table.addTotal("ELDRITCH", 15);
        warp.add(WarpType.TEMPORARY, 5);
        if (random.nextBoolean()) warp.add(WarpType.NORMAL, 1);
        return true;
    }
}
