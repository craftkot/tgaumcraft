package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Compact read-only BETA26 CardChoice identity for vanilla 16-bit menu
 * DataSlots. It is NOT a card factory or replacement for original cardNBT.
 *
 * Key indexes 1..33 match ConfigResearch.initTheorycraft registration order;
 * 0 = no card and 63 = unknown/unreadable saved card. Keep original NBT
 * untouched on the server and do not enable activation from this preview.
 */
public final class LegacyTheorycraftChoicePreview {
    private LegacyTheorycraftChoicePreview() {}

    private static final List<String> REGISTERED = LegacyTheorycraftManifest.CARD_REGISTRATION_ORDER;
    private static final int ID_MASK = 63;
    private static final int AID_FLAG = 64;
    private static final int SELECTED_FLAG = 128;
    public static final int UNKNOWN_ID = 63;

    public static int encode(String key, boolean fromAid, boolean selected) {
        if (key == null) return 0;
        int index = REGISTERED.indexOf(key);
        int id = index < 0 ? UNKNOWN_ID : index + 1;
        return id | (fromAid ? AID_FLAG : 0) | (selected ? SELECTED_FLAG : 0);
    }

    public static int id(int value) { return value & ID_MASK; }

    public static boolean fromAid(int value) {
        return id(value) != 0 && (value & AID_FLAG) != 0;
    }

    public static boolean selected(int value) {
        return id(value) != 0 && (value & SELECTED_FLAG) != 0;
    }

    /** Never infer unknown card type or rebuild subclass state from this ID. */
    public static String key(int value) {
        int id = id(value);
        return id >= 1 && id <= REGISTERED.size() ? REGISTERED.get(id - 1) : null;
    }

    public static boolean unknown(int value) { return id(value) == UNKNOWN_ID; }

    public static String displayClassName(int value) {
        String key = key(value);
        if (key == null) return unknown(value) ? "Unknown card" : "";
        int i = key.lastIndexOf('.');
        String simple = key.substring(i + 1);
        return simple.startsWith("Card") ? simple.substring(4) : simple;
    }
}
