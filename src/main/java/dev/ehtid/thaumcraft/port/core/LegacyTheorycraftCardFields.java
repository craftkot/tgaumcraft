package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Exact BETA26 TheorycraftCard.serialize() and subclass serialized NBT fields.
 * Decompiled from all 33 registered card classes in the original beta26 JAR.
 *
 * The base superclass writes the long "seed". The 14 overriding subclasses
 * extend that tag with historical field names and types shown here. Do NOT
 * translate old 1.12 ItemStack NBT in this loader-neutral descriptor.
 */
public final class LegacyTheorycraftCardFields {
    private LegacyTheorycraftCardFields() {}

    public enum Type { STRING, INT, LONG, ITEM_STACK }
    public record Field(String key, Type type) {
        public Field {
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("NBT key");
            Objects.requireNonNull(type);
        }
    }

    private static Field text(String name) { return new Field(name, Type.STRING); }
    private static Field number(String name) { return new Field(name, Type.INT); }
    private static Field stack(String name) { return new Field(name, Type.ITEM_STACK); }

    public static List<Field> additionalFields(String cardKey) {
        Objects.requireNonNull(cardKey);
        if (!LegacyTheorycraftManifest.allCardKeys().contains(cardKey))
            throw new IllegalArgumentException("Unregistered BETA26 card: " + cardKey);
        String card = cardKey.substring(cardKey.lastIndexOf('.') + 1);
        return switch (card) {
            case "CardStudy", "CardAnalyze", "CardReject" -> List.of(text("cat"));
            case "CardInspired" -> List.of(text("cat"), number("amt"));
            case "CardNotation" -> List.of(text("cat1"), text("cat2"));
            case "CardCurio", "CardMindOverMatter", "CardTinker" ->
                    List.of(stack("stack"));
            case "CardCelestial" -> List.of(
                    number("md1"), number("md2"), text("cat"));
            case "CardConcentrate", "CardChannel" -> List.of(text("aspect"));
            case "CardReactions" -> List.of(text("aspect1"), text("aspect2"));
            case "CardSynthesis" -> List.of(
                    text("aspect1"), text("aspect2"), text("aspect3"));
            case "CardInfuse" -> List.of(text("aspect"), stack("stack"));
            default -> List.of();
        };
    }

    /** CardChoice.cardNBT always includes superclass seed (NBT long). */
    public static Field baseSeed() { return new Field("seed", Type.LONG); }

    /** Exactly fourteen registered BETA26 subclasses extend serialization. */
    public static boolean hasAdditionalFields(String cardKey) {
        return !additionalFields(cardKey).isEmpty();
    }
}
