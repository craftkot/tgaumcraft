package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyLateTheoryCards;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Original BETA26 XP debit rules for the three experience-driven cards.
 * Only call from a server-validated card activation after preconditions
 * and material costs are known; it does not grant any theory on its own.
 */
public final class NeoForgeTheorycraftExperience {
    private NeoForgeTheorycraftExperience() {}

    /**
     * Returns exact spent levels for Enchantment (5) and Spellbinding (1..5),
     * or -1 if not payable. One method prevents accidentally double-charging.
     */
    public static int debitForCard(ServerPlayer player, String card) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(card);
        int levels = Math.max(0, player.experienceLevel);
        if (card.equals(LegacyLateTheoryCards.ENCHANTMENT)) {
            if (!LegacyLateTheoryCards.canEnchant(levels)) return -1;
            player.giveExperienceLevels(-5);
            return 5;
        }
        if (card.equals(LegacyLateTheoryCards.SPELLBINDING)) {
            int spent = LegacyLateTheoryCards.spellbindingLevels(levels);
            if (spent == 0) return -1;
            player.giveExperienceLevels(-spent);
            return spent;
        }
        throw new IllegalArgumentException("not an XP-spending card: " + card);
    }

    /**
     * CardDarkWhispers snapshots XP, then requests an original debit of
     * XP+10. Minecraft clamps levels at zero if this exceeds the balance;
     * this is intentional, not a 10-level affordability gate.
     *
     * Returns ORIGINAL levels (before charging), which the original effect
     * uses for category rolls, ELDRITCH knowledge and normal warp.
     */
    public static int debitDarkWhispers(ServerPlayer player) {
        Objects.requireNonNull(player);
        int originalLevels = Math.max(0, player.experienceLevel);
        int requestedDebit = LegacyLateTheoryCards.darkWhispersLevelDebit(originalLevels);
        player.giveExperienceLevels(-requestedDebit);
        return originalLevels;
    }
}
