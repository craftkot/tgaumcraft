package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * A BETA26 theory-card selection request MUST be fully payable before any
 * item, XP, ink, research-point or warp mutation. Unlike the original
 * ContainerResearchTable, this prevents consuming duplicate ingredients
 * separately while ignoring a failed debit or activating Scripting without
 * both extra crafting-table resources.
 *
 * The original button 4/5/6 outer call charges exactly one Scribing Tools
 * durability after successful activation. CardScripting ALSO consumes one
 * ink durability and one paper internally, so its full requirement is TWO
 * ink durability units and ONE paper sheet on a single atomic preflight.
 *
 * This is a preflight recipe, NOT an activation. The server adapter must
 * validate original saved cardNBT, ownership and exact item identities and
 * then commit all costs/effects as one server-side game action.
 */
public final class LegacyTheorycraftActivationPreflight {
    private LegacyTheorycraftActivationPreflight() {}

    public enum Failure {
        NONE, UNKNOWN_CARD, INVALID_CARD_NBT, INVALID_THEORY, ALREADY_SELECTED,
        INSPIRATION, INK, PAPER, EXPERIENCE, REQUIRED_ITEMS
    }

    public record Inputs(String cardKey, boolean validCardNbt, boolean activeTheory,
                         boolean selectedAlready, boolean otherCardSelected,
                         int inspiration, int inkDurabilityLeft, int tablePaper,
                         int playerExperienceLevels, boolean requiredItemsAvailable) {}

    public record Plan(Failure failure, int inspirationCost, int inkUnits,
                       int tablePaperUnits, int experienceLevelsDebited,
                       boolean debitAllPlusTenLevels, boolean hasInventoryMaterials) {
        public Plan {
            Objects.requireNonNull(failure);
        }
        public boolean accepted() { return failure == Failure.NONE; }
    }

    public static Plan check(Inputs input) {
        Objects.requireNonNull(input);
        String key = input.cardKey();
        if (key == null || !LegacyTheorycraftManifest.allCardKeys().contains(key))
            return denied(Failure.UNKNOWN_CARD);
        if (!input.validCardNbt()) return denied(Failure.INVALID_CARD_NBT);
        if (!input.activeTheory()) return denied(Failure.INVALID_THEORY);
        if (input.selectedAlready() || input.otherCardSelected())
            return denied(Failure.ALREADY_SELECTED);

        int cost = LegacyTheorycraftCardMetadata.get(key).cost();
        if (input.inspiration() <= 0 || cost > input.inspiration())
            return denied(Failure.INSPIRATION);

        boolean scripting = key.endsWith(".CardScripting");
        int ink = scripting ? 2 : 1;
        int paper = scripting ? 1 : 0;
        if (input.inkDurabilityLeft() < ink) return denied(Failure.INK);
        if (input.tablePaper() < paper) return denied(Failure.PAPER);

        int xp = Math.max(0, input.playerExperienceLevels());
        int levels = 0;
        boolean allPlusTen = false;
        if (key.equals(LegacyLateTheoryCards.ENCHANTMENT)) {
            if (xp < 5) return denied(Failure.EXPERIENCE);
            levels = 5;
        } else if (key.equals(LegacyLateTheoryCards.SPELLBINDING)) {
            if (xp == 0) return denied(Failure.EXPERIENCE);
            levels = Math.min(xp, 5);
        } else if (key.equals(LegacyLateTheoryCards.DARK_WHISPERS)) {
            // Original permits 0 experience and clamps the overdraft.
            levels = xp + 10;
            allPlusTen = true;
        }

        boolean hasRequirements = LegacyTheorycraftRequiredItems.requiredItemCount(key) != 0;
        if (hasRequirements && !input.requiredItemsAvailable())
            return denied(Failure.REQUIRED_ITEMS);
        return new Plan(Failure.NONE, cost, ink, paper, levels,
                allPlusTen, hasRequirements);
    }

    private static Plan denied(Failure failure) {
        return new Plan(failure, 0, 0, 0, 0, false, false);
    }
}
