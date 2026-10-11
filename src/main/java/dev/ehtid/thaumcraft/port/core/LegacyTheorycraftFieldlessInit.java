package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Original BETA26 initialize() result for precisely the 19 registered
 * TheorycraftCard classes that add no custom serialized cardNBT fields.
 *
 * In the 1.12 JAR, 14 of these inherit TheorycraftCard.initialize()=true.
 * The other five override initialize(): Balance, Ponder, Rethink,
 * Spellbinding, Synergy. The XP-dependent Spellbinding check is made at
 * initialization/draw time (original player.experienceLevel > 0);
 * it does NOT deduct XP. Actual activation must validate again.
 *
 * Return false for an original initializer rejection, but never imply
 * that an unported field-bearing card is simple or can be drawn.
 */
public final class LegacyTheorycraftFieldlessInit {
    private LegacyTheorycraftFieldlessInit() {}

    public static boolean isFieldless(String cardKey) {
        return cardKey != null
                && LegacyTheorycraftManifest.allCardKeys().contains(cardKey)
                && LegacyTheorycraftCardFields.additionalFields(cardKey).isEmpty();
    }

    public static boolean initialize(String cardKey, LegacyResearchTableData table,
                                     int playerExperienceLevels) {
        Objects.requireNonNull(table, "theory");
        if (!isFieldless(cardKey))
            throw new IllegalArgumentException("unregistered/field-bearing BETA26 card: " + cardKey);
        if (playerExperienceLevels < 0)
            throw new IllegalArgumentException("negative experience level");
        return switch (cardKey.substring(cardKey.lastIndexOf('.') + 1)) {
            case "CardBalance" -> LegacyAdditionalTheoryCards.canBalance(table);
            case "CardPonder" -> LegacyBasicTheoryCards.canPonder(table);
            case "CardRethink" -> LegacyBasicTheoryCards.canRethink(table);
            case "CardSpellbinding" -> playerExperienceLevels > 0;
            case "CardSynergy" -> LegacySpecialtyTheoryCards.canSynergize(table);

            // Exactly the 14 remaining original registered overrides
            // inherit TheorycraftCard.initialize() returning true.
            case "CardExperimentation", "CardEnchantment", "CardBeacon",
                    "CardCalibrate", "CardMeasure", "CardFocus",
                    "CardAwareness", "CardSculpting", "CardScripting",
                    "CardDarkWhispers", "CardGlyphs", "CardPortal",
                    "CardRevelation", "CardRealization" -> true;
            default -> throw new AssertionError("original BETA26 fieldless registry changed");
        };
    }
}
