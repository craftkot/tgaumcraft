package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * BETA26 CardCalibrate, CardFocus, CardGlyphs, CardSynergy and CardScripting.
 * These are original theory effects, not replacement/placeholder cards.
 */
public final class LegacySpecialtyTheoryCards {
    private LegacySpecialtyTheoryCards() {}

    public static final String CALIBRATE =
            "thaumcraft.common.lib.research.theorycraft.CardCalibrate";
    public static final String FOCUS =
            "thaumcraft.common.lib.research.theorycraft.CardFocus";
    public static final String GLYPHS =
            "thaumcraft.common.lib.research.theorycraft.CardGlyphs";
    public static final String SYNERGY =
            "thaumcraft.common.lib.research.theorycraft.CardSynergy";
    public static final String SCRIPTING =
            "thaumcraft.common.lib.research.theorycraft.CardScripting";

    /** All five original cards cost exactly one inspiration. */
    public static final int INSPIRATION_COST = 1;

    public static void calibrate(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addTotal("ARTIFICE", 15);
        table.addBonusDraws(1);
    }

    public static void focus(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addTotal("AUROMANCY", 15);
        table.addBonusDraws(1);
    }

    /**
     * CardGlyphs: choose ANY registered category in insertion order; add
     * 10..20 theory there and separately 10..20 ELDRITCH, plus 5 TEMPORARY
     * warp. The player's RNG is consumed 3 times.
     */
    public static boolean glyphs(LegacyResearchTableData table, PlayerWarpData warp,
                                 List<String> registeredCategories,
                                 LegacyAdditionalTheoryCards.BoundedRandom random) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(registeredCategories);
        Objects.requireNonNull(random);
        if (registeredCategories.isEmpty()) return false;
        String chosen = registeredCategories.get(random.nextInt(registeredCategories.size()));
        if (chosen == null || chosen.isEmpty()) return false;
        table.addTotal(chosen, 10 + random.nextInt(11));
        table.addTotal("ELDRITCH", 10 + random.nextInt(11));
        warp.add(WarpType.TEMPORARY, 5);
        return true;
    }

    /**
     * CardSynergy.initialize checks 15 or more theory points among ARTIFICE,
     * ALCHEMY, INFUSION; activation removes exactly 15 (cycling in that order),
     * grants +30 GOLEMANCY and adds 1 penaltyStart.
     */
    public static boolean canSynergize(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        return table.getTotal("ARTIFICE")
                + table.getTotal("ALCHEMY")
                + table.getTotal("INFUSION") >= 15;
    }

    public static boolean synergy(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        if (!canSynergize(table)) return false;
        int remaining = 15;
        final String[] categories = {"ARTIFICE", "ALCHEMY", "INFUSION"};
        int turns = 0;
        while (remaining > 0 && turns < 1000) {
            turns++;
            for (String key : categories) {
                if (table.getTotal(key) <= 0) continue;
                table.addTotal(key, -1);
                remaining--;
                if (remaining <= 0) break;
            }
        }
        if (remaining > 0) return false;
        table.addTotal("GOLEMANCY", 30);
        table.addPenaltyStart(1);
        return true;
    }

    /**
     * CardScripting rewards 25 GOLEMANCY for paper+ink. The original code
     * checks only whether table inventory slots are nonnull, ignores the
     * consume*() return values, then awards theory regardless. Minecraft
     * 1.21 uses nonnull ItemStack.EMPTY; callers must first SUCCESSFULLY
     * consume both paper and a unit of ink in one validated transaction.
     */
    public static void scriptingAfterMaterialsConsumed(LegacyResearchTableData table) {
        Objects.requireNonNull(table);
        table.addTotal("GOLEMANCY", 25);
    }
}
