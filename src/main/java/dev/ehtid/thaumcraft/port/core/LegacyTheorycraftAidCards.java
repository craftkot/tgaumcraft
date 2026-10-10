package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BETA26 ITheorycraftAid.getCards() as read from the original aid bytecode.
 * Keys and class names are historical FQCNs, not new NeoForge identifiers.
 * Aids expose weighted arrays: do not use a Set for the returned cards.
 */
public final class LegacyTheorycraftAidCards {
    private LegacyTheorycraftAidCards() {}

    private static final String API = "thaumcraft.api.research.theorycraft.";
    private static final String COMMON = "thaumcraft.common.lib.research.theorycraft.";
    private static String api(String name) { return API + name; }
    private static String common(String name) { return COMMON + name; }

    private static final Map<String, List<String>> CARDS = build();

    private static Map<String, List<String>> build() {
        Map<String, List<String>> aids = new HashMap<>();
        aids.put(api("AidBookshelf"), List.of(
                api("CardBalance"), api("CardNotation"), api("CardNotation"),
                api("CardStudy"), api("CardStudy"), api("CardStudy")));
        aids.put(common("AidBrainInAJar"), List.of(common("CardDarkWhispers")));
        aids.put(common("AidGlyphedStone"), List.of(common("CardGlyphs")));
        for (String variant : List.of("End", "Nether", "Crimson"))
            aids.put(common("AidPortal$AidPortal" + variant), List.of(common("CardPortal")));
        aids.put(common("AidBasicAlchemy"), List.of(
                common("CardConcentrate"), common("CardReactions"), common("CardSynthesis")));
        aids.put(common("AidBasicArtifice"), List.of(
                common("CardCalibrate"), common("CardTinker"), common("CardMindOverMatter")));
        aids.put(common("AidBasicInfusion"), List.of(
                common("CardMeasure"), common("CardChannel"), common("CardInfuse")));
        aids.put(common("AidBasicAuromancy"), List.of(
                common("CardFocus"), common("CardAwareness"), common("CardSpellbinding")));
        aids.put(common("AidBasicGolemancy"), List.of(
                common("CardSculpting"), common("CardScripting"), common("CardSynergy")));
        aids.put(common("AidBasicEldritch"), List.of(
                common("CardRealization"), common("CardRevelation"), common("CardTruth")));
        aids.put(common("AidEnchantmentTable"), List.of(common("CardEnchantment")));
        aids.put(common("AidBeacon"), List.of(common("CardBeacon")));
        if (aids.size() != 14) throw new IllegalStateException("BETA26 aid count");
        return Map.copyOf(aids);
    }

    /** Null means no original aid class was registered with this key. */
    public static List<String> forAid(String key) { return CARDS.get(key); }

    /**
     * BETA26 ResearchTableData.initialize() iterates the selected aid Set
     * and appends EVERY Class.getName() returned by getCards(), without
     * deduplication. Unknown keys still count against inspiration in 1.12.
     */
    public static List<String> forSelected(Set<String> selected) {
        Objects.requireNonNull(selected);
        ArrayList<String> result = new ArrayList<>();
        for (String key : selected) {
            List<String> classes = CARDS.get(key);
            if (classes != null) result.addAll(classes);
        }
        return List.copyOf(result);
    }

    public static Map<String, List<String>> all() { return CARDS; }
}
