package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Exact BETA26 getInspirationCost / getResearchCategory / isAidOnly overrides
 * for all 33 registered TheorycraftCard subclasses, audited with javap -c -p.
 *
 * A dynamic category is the ORIGINAL card.cat after initialize()/deserialize(),
 * NOT a guessed category; callers must reject uninitialized cards. The
 * returned metadata alone does NOT certify a card can be drawn or activated.
 */
public final class LegacyTheorycraftCardMetadata {
    private LegacyTheorycraftCardMetadata() {}

    public record Metadata(int cost, String fixedCategory, boolean dynamicCategory,
                           boolean aidOnly) {
        public Metadata {
            if (dynamicCategory && fixedCategory != null)
                throw new IllegalArgumentException("dynamic category cannot be fixed");
        }

        /** A nullable category is legal for cards without any category restriction. */
        public String categoryAfterInitialization(String initializedCategory) {
            if (dynamicCategory) return initializedCategory;
            return fixedCategory;
        }
    }

    public static Metadata get(String key) {
        Objects.requireNonNull(key);
        if (!LegacyTheorycraftManifest.allCardKeys().contains(key))
            throw new IllegalArgumentException("Unregistered original BETA26 card: " + key);
        String name = key.substring(key.lastIndexOf('.') + 1);
        return switch (name) {
            case "CardStudy" -> dynamic(1, true);
            case "CardAnalyze" -> dynamic(2, false);
            case "CardInspired" -> dynamic(2, false);
            case "CardCelestial" -> dynamic(1, false);
            case "CardNotation" -> fixed(1, null, true);
            case "CardBalance", "CardCurio", "CardEnchantment" ->
                    fixed(1, null, "CardEnchantment".equals(name));
            case "CardExperimentation" -> fixed(2, null, false);
            case "CardPonder" -> fixed(2, null, false);
            case "CardReject" -> fixed(0, null, false);
            case "CardRethink" -> fixed(-1, null, false);
            case "CardBeacon" -> fixed(-2, null, true);
            case "CardConcentrate", "CardReactions", "CardSynthesis" ->
                    fixed(1, "ALCHEMY", false);
            case "CardCalibrate", "CardMindOverMatter", "CardTinker" ->
                    fixed(1, "ARTIFICE", false);
            case "CardMeasure", "CardChannel", "CardInfuse" ->
                    fixed(1, "INFUSION", false);
            case "CardFocus", "CardAwareness", "CardSpellbinding" ->
                    fixed(1, "AUROMANCY", false);
            case "CardSculpting", "CardScripting", "CardSynergy" ->
                    fixed(1, "GOLEMANCY", false);
            case "CardGlyphs" -> fixed(1, "ELDRITCH", true);
            case "CardDarkWhispers" -> fixed(1, "ELDRITCH", true);
            case "CardPortal" -> fixed(-1, "ELDRITCH", true);
            case "CardRevelation", "CardRealization" ->
                    fixed(1, "ELDRITCH", false);
            default -> throw new AssertionError("33-class BETA26 manifest changed: " + name);
        };
    }

    private static Metadata fixed(int cost, String category, boolean aidOnly) {
        return new Metadata(cost, category, false, aidOnly);
    }

    private static Metadata dynamic(int cost, boolean aidOnly) {
        return new Metadata(cost, null, true, aidOnly);
    }
}
