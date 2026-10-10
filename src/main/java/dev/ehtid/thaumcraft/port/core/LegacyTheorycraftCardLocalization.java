package dev.ehtid.thaumcraft.port.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Original Thaumcraft 6.1.BETA26 TheorycraftCard.getLocalizedName resource
 * keys, recovered from each registered class's bytecode.
 *
 * Unlike the Java class short name, these keys can render the original
 * Russian/English localized card title in the NeoForge research-table UI.
 * Placeholder cards need a real initialized category/aspect at activation;
 * previews pass an ellipsis instead of inventing a category.
 */
public final class LegacyTheorycraftCardLocalization {
    private LegacyTheorycraftCardLocalization() {}

    public static String nameKey(String cardClass) {
        Objects.requireNonNull(cardClass);
        if (!LegacyTheorycraftManifest.allCardKeys().contains(cardClass))
            throw new IllegalArgumentException("Unregistered BETA26 card: " + cardClass);
        String simple = cardClass.substring(cardClass.lastIndexOf('.') + 1);
        String key = switch (simple) {
            case "CardMindOverMatter" -> "mindmatter";
            case "CardDarkWhispers" -> "darkwhisper";
            case "CardGlyphs" -> "glyph";
            default -> simple.substring(4).toLowerCase(Locale.ROOT);
        };
        return "card." + key + ".name";
    }

    /** Original four localized names contain %1$s in the 1.12 lang files. */
    public static boolean needsNameArgument(String cardClass) {
        String key = nameKey(cardClass);
        return key.equals("card.study.name") || key.equals("card.reject.name")
                || key.equals("card.analyze.name") || key.equals("card.channel.name");
    }
}
