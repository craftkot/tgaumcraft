package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exactly the 14 ITheorycraftAid registrations and 33 TheorycraftCard class
 * registrations in BETA26 ConfigResearch.initTheorycraft (javap).
 *
 * The registration LIST order is not the draw order: TheorycraftManager.cards
 * is a vanilla java.util.HashMap, and ResearchTableData.drawCards samples
 * cards.keySet().toArray(), whose order is Java's hash-bucket iteration.
 * Do not sort these keys or substitute only the currently ported cards.
 */
public final class LegacyTheorycraftManifest {
    private LegacyTheorycraftManifest() {}

    public static final List<String> AID_REGISTRATION_ORDER = List.of(
            "thaumcraft.api.research.theorycraft.AidBookshelf",
            "thaumcraft.common.lib.research.theorycraft.AidBrainInAJar",
            "thaumcraft.common.lib.research.theorycraft.AidGlyphedStone",
            "thaumcraft.common.lib.research.theorycraft.AidPortal$AidPortalEnd",
            "thaumcraft.common.lib.research.theorycraft.AidPortal$AidPortalNether",
            "thaumcraft.common.lib.research.theorycraft.AidPortal$AidPortalCrimson",
            "thaumcraft.common.lib.research.theorycraft.AidBasicAlchemy",
            "thaumcraft.common.lib.research.theorycraft.AidBasicArtifice",
            "thaumcraft.common.lib.research.theorycraft.AidBasicInfusion",
            "thaumcraft.common.lib.research.theorycraft.AidBasicAuromancy",
            "thaumcraft.common.lib.research.theorycraft.AidBasicGolemancy",
            "thaumcraft.common.lib.research.theorycraft.AidBasicEldritch",
            "thaumcraft.common.lib.research.theorycraft.AidEnchantmentTable",
            "thaumcraft.common.lib.research.theorycraft.AidBeacon");

    public static final List<String> CARD_REGISTRATION_ORDER = List.of(
            "thaumcraft.api.research.theorycraft.CardStudy",
            "thaumcraft.api.research.theorycraft.CardAnalyze",
            "thaumcraft.api.research.theorycraft.CardBalance",
            "thaumcraft.api.research.theorycraft.CardNotation",
            "thaumcraft.api.research.theorycraft.CardPonder",
            "thaumcraft.api.research.theorycraft.CardRethink",
            "thaumcraft.api.research.theorycraft.CardReject",
            "thaumcraft.api.research.theorycraft.CardExperimentation",
            "thaumcraft.common.lib.research.theorycraft.CardCurio",
            "thaumcraft.api.research.theorycraft.CardInspired",
            "thaumcraft.common.lib.research.theorycraft.CardEnchantment",
            "thaumcraft.common.lib.research.theorycraft.CardBeacon",
            "thaumcraft.common.lib.research.theorycraft.CardCelestial",
            "thaumcraft.common.lib.research.theorycraft.CardConcentrate",
            "thaumcraft.common.lib.research.theorycraft.CardReactions",
            "thaumcraft.common.lib.research.theorycraft.CardSynthesis",
            "thaumcraft.common.lib.research.theorycraft.CardCalibrate",
            "thaumcraft.common.lib.research.theorycraft.CardMindOverMatter",
            "thaumcraft.common.lib.research.theorycraft.CardTinker",
            "thaumcraft.common.lib.research.theorycraft.CardMeasure",
            "thaumcraft.common.lib.research.theorycraft.CardChannel",
            "thaumcraft.common.lib.research.theorycraft.CardInfuse",
            "thaumcraft.common.lib.research.theorycraft.CardFocus",
            "thaumcraft.common.lib.research.theorycraft.CardAwareness",
            "thaumcraft.common.lib.research.theorycraft.CardSpellbinding",
            "thaumcraft.common.lib.research.theorycraft.CardSculpting",
            "thaumcraft.common.lib.research.theorycraft.CardScripting",
            "thaumcraft.common.lib.research.theorycraft.CardSynergy",
            "thaumcraft.common.lib.research.theorycraft.CardDarkWhispers",
            "thaumcraft.common.lib.research.theorycraft.CardGlyphs",
            "thaumcraft.common.lib.research.theorycraft.CardPortal",
            "thaumcraft.common.lib.research.theorycraft.CardRevelation",
            "thaumcraft.common.lib.research.theorycraft.CardRealization");

    public static Set<String> allCardKeys() {
        return Set.copyOf(CARD_REGISTRATION_ORDER);
    }

    /**
     * Bytecode-ported card effects/init models. These are NOT all connected to
     * the playable menu: an unimplemented original card must never be replaced
     * by another effect just to make the card-selection screen functional.
     */
    public static Set<String> portedCoreCards() {
        return Set.of(
                "thaumcraft.api.research.theorycraft.CardStudy",
                "thaumcraft.api.research.theorycraft.CardAnalyze",
                "thaumcraft.api.research.theorycraft.CardBalance",
                "thaumcraft.api.research.theorycraft.CardNotation",
                "thaumcraft.api.research.theorycraft.CardPonder",
                "thaumcraft.api.research.theorycraft.CardRethink",
                "thaumcraft.api.research.theorycraft.CardReject",
                "thaumcraft.api.research.theorycraft.CardExperimentation",
                "thaumcraft.api.research.theorycraft.CardInspired",
                LegacyLateTheoryCards.ENCHANTMENT,
                LegacyLateTheoryCards.BEACON,
                "thaumcraft.common.lib.research.theorycraft.CardCelestial",
                "thaumcraft.common.lib.research.theorycraft.CardConcentrate",
                "thaumcraft.common.lib.research.theorycraft.CardReactions",
                "thaumcraft.common.lib.research.theorycraft.CardSynthesis",
                "thaumcraft.common.lib.research.theorycraft.CardCalibrate",
                LegacyLateTheoryCards.SPELLBINDING,
                "thaumcraft.common.lib.research.theorycraft.CardMeasure",
                "thaumcraft.common.lib.research.theorycraft.CardChannel",
                "thaumcraft.common.lib.research.theorycraft.CardFocus",
                "thaumcraft.common.lib.research.theorycraft.CardAwareness",
                "thaumcraft.common.lib.research.theorycraft.CardSculpting",
                "thaumcraft.common.lib.research.theorycraft.CardScripting",
                "thaumcraft.common.lib.research.theorycraft.CardSynergy",
                "thaumcraft.common.lib.research.theorycraft.CardGlyphs",
                LegacyLateTheoryCards.DARK_WHISPERS,
                LegacyLateTheoryCards.PORTAL,
                LegacyLateTheoryCards.REVELATION,
                LegacyLateTheoryCards.REALIZATION,
                LegacyMaterialTheoryCards.CURIO,
                LegacyMaterialTheoryCards.MIND_OVER_MATTER,
                LegacyMaterialTheoryCards.TINKER,
                LegacyMaterialTheoryCards.INFUSE);
    }

    /** Unported original cards remain explicit development blockers. */
    public static List<String> missingCoreCards() {
        return CARD_REGISTRATION_ORDER.stream()
                .filter(key -> !portedCoreCards().contains(key))
                .toList();
    }

    /**
     * Use the same java.util.HashMap container as the original manager.
     * Iteration order depends on exact key hashing and resizing and must not
     * be substituted with alphabetical or insertion ordering for random draws.
     */
    public static List<String> cardsInHashMapIterationOrder() {
        HashMap<String, Boolean> map = new HashMap<>();
        for (String card : CARD_REGISTRATION_ORDER) map.putIfAbsent(card, Boolean.TRUE);
        return List.copyOf(map.keySet());
    }

    public static List<String> aidsInHashMapIterationOrder() {
        HashMap<String, Boolean> map = new HashMap<>();
        for (String aid : AID_REGISTRATION_ORDER) map.putIfAbsent(aid, Boolean.TRUE);
        return List.copyOf(map.keySet());
    }
}
