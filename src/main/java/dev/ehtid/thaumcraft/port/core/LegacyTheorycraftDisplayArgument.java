package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only BETA26 card-localized-name argument synchronization.
 * Original ResearchTableData stores full "cat"/"aspect" strings in CardChoice
 * NBT. Vanilla container DataSlot only transmits a short; the codebook maps
 * 7 original research categories and all 37 original aspect tags into 1..44.
 *
 * A missing/unknown old or mod-added argument deliberately maps to UNKNOWN,
 * NOT to a made-up category or aspect. This is display-only, never used
 * to authorize card activation or reconstruct original ItemStack/NBT data.
 */
public final class LegacyTheorycraftDisplayArgument {
    private LegacyTheorycraftDisplayArgument() {}

    public static final int EMPTY = 0;
    public static final int UNKNOWN = 63;
    private static final List<String> CATEGORIES = List.of(
            "BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE",
            "INFUSION", "GOLEMANCY", "ELDRITCH");
    private static final List<String> ASPECTS;

    static {
        ArrayList<String> aspectTags = new ArrayList<>();
        for (AspectData aspect : new AspectRegistry().values()) aspectTags.add(aspect.tag());
        if (aspectTags.size() != 37 || CATEGORIES.size() != 7)
            throw new ExceptionInInitializerError("BETA26 built-in category/aspect count");
        ASPECTS = List.copyOf(aspectTags);
    }

    public static List<String> categories() { return CATEGORIES; }
    public static List<String> aspects() { return ASPECTS; }

    public static String fieldKey(String cardKey) {
        if (cardKey == null) return null;
        return switch (cardKey.substring(cardKey.lastIndexOf('.') + 1)) {
            case "CardStudy", "CardReject", "CardAnalyze" -> "cat";
            case "CardChannel" -> "aspect";
            default -> null;
        };
    }

    public static int encode(String cardKey, String originalValue) {
        String key = fieldKey(cardKey);
        if (key == null) return EMPTY;
        if (originalValue == null || originalValue.isEmpty()) return UNKNOWN;
        if (key.equals("cat")) {
            int index = CATEGORIES.indexOf(originalValue);
            return index < 0 ? UNKNOWN : 1 + index;
        }
        int index = ASPECTS.indexOf(originalValue);
        return index < 0 ? UNKNOWN : 1 + CATEGORIES.size() + index;
    }

    public static String translationKey(int code) {
        if (code >= 1 && code <= CATEGORIES.size())
            return "tc.research_category." + CATEGORIES.get(code - 1);
        int index = code - CATEGORIES.size() - 1;
        if (index >= 0 && index < ASPECTS.size())
            return "tc.aspect." + ASPECTS.get(index);
        return null;
    }

    /** Called on client only for presentation; never resolve server actions. */
    public static boolean unknown(int code) { return code == UNKNOWN; }
}
