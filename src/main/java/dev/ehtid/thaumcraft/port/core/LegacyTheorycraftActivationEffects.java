package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Actual BETA26 card.activate() dispatch for all 33 registered card classes.
 *
 * Pure transactional EFFECT STAGING: table, knowledge and warp are cloned
 * BEFORE applying any effect. A false/invalid card returns null and CANNOT
 * leave the live session with partly granted theory, lost observation or warp.
 * The random source follows the original living player's RNG call ordering.
 *
 * This class never consumes player items/XP, table ink/paper or spawns reward
 * items. Call it ONLY after NeoForgeTheorycraftActivationPreflight passed;
 * the SERVER commit bridge must recheck/debit the original materials/XP
 * and Scripting's two ink plus paper before publishing this outcome.
 *
 * Deliberate BETA26 bug fixes are delegated to the already audited source
 * card models (Analyze uses the actual category, Ponder cannot grant bonus
 * for an impossible all-blocked state).
 */
public final class LegacyTheorycraftActivationEffects {
    private LegacyTheorycraftActivationEffects() {}

    public record Card(String key, Map<String, String> text, Map<String, Integer> number,
                       int savedCurioMetadata) {
        public Card {
            Objects.requireNonNull(key);
            text = Map.copyOf(text);
            number = Map.copyOf(number);
        }
        public String text(String field) { return text.get(field); }
        public int number(String field) { return number.getOrDefault(field, 0); }
    }

    public record Outcome(LegacyResearchTableData.Snapshot theory,
                          Map<String, Integer> playerKnowledgeRaw, int[] warpArray,
                          int warpCounter, String spawnedCrystalAspect) {
        public Outcome {
            Objects.requireNonNull(theory);
            playerKnowledgeRaw = Map.copyOf(playerKnowledgeRaw);
            warpArray = warpArray.clone();
        }
        @Override public int[] warpArray() { return warpArray.clone(); }
    }

    /**
     * Amount of experience that was present BEFORE the validated XP debit.
     * The original DarkWhispers card uses this pre-debit value for rolls;
     * Spellbinding uses min(5, original levels). Set -1 for unavailable item
     * vis size to use the original exception/fallback reward formulas.
     */
    public static Outcome stage(Card card, LegacyResearchTableData originalTable,
                                PlayerKnowledgeData player, PlayerWarpData playerWarp,
                                List<String> registeredCategories, AspectRegistry aspects,
                                Random playerRandom, int xpBeforeDebit, int itemVisSize) {
        Objects.requireNonNull(card);
        Objects.requireNonNull(originalTable);
        Objects.requireNonNull(player);
        Objects.requireNonNull(playerWarp);
        Objects.requireNonNull(registeredCategories);
        Objects.requireNonNull(aspects);
        Objects.requireNonNull(playerRandom);
        if (!LegacyTheorycraftManifest.allCardKeys().contains(card.key())) return null;
        if (xpBeforeDebit < 0) return null;

        LegacyResearchTableData table =
                LegacyResearchTableData.fromSnapshot(originalTable.snapshot());
        PlayerKnowledgeData knowledge = new PlayerKnowledgeData();
        knowledge.loadSnapshot(player.getResearchList(), player.copyStages(),
                player.copyFlags(), player.copyKnowledgeRaw());
        PlayerWarpData warp = new PlayerWarpData();
        warp.loadLegacyWarpArray(playerWarp.copyLegacyWarpArray());
        warp.setCounter(playerWarp.getCounter());

        String crystal = null;
        boolean success;
        switch (card.key().substring(card.key().lastIndexOf('.') + 1)) {
            case "CardStudy" -> success = LegacyAdditionalTheoryCards.study(
                    table, card.text("cat"), playerRandom::nextInt);
            case "CardAnalyze" -> success = LegacyCardAnalyze.activate(
                    card.text("cat"), knowledge, table, playerRandom::nextInt);
            case "CardBalance" -> success = LegacyAdditionalTheoryCards.balance(table);
            case "CardNotation" -> {
                String weakest = card.text("cat1"), strongest = card.text("cat2");
                success = weakest != null && strongest != null
                        && LegacyAdditionalTheoryCards.notation(table,
                        new LegacyAdditionalTheoryCards.Notation(weakest, strongest),
                        playerRandom::nextInt);
            }
            case "CardPonder" -> success = LegacyBasicTheoryCards.ponder(table);
            case "CardRethink" -> success = LegacyBasicTheoryCards.rethink(table, playerRandom);
            case "CardReject" -> success = LegacyTheorycraftMiscCards.reject(
                    table, card.text("cat"));
            case "CardExperimentation" -> success = LegacyAdditionalTheoryCards.experiment(
                    table, registeredCategories, playerRandom::nextInt);
            case "CardCurio" -> success = LegacyMaterialTheoryCards.curioAfterItemConsumed(
                    table, card.savedCurioMetadata(), registeredCategories, playerRandom);
            case "CardInspired" -> success = card.text("cat") != null
                    && LegacyTheorycraftMiscCards.inspire(table,
                    new LegacyTheorycraftMiscCards.Inspired(
                            card.text("cat"), card.number("amt")));
            case "CardEnchantment" -> {
                success = xpBeforeDebit >= 5;
                if (success) LegacyLateTheoryCards.enchantmentAfterFiveLevelsSpent(
                        table, playerRandom);
            }
            case "CardBeacon" -> {
                LegacyLateTheoryCards.beacon(table);
                success = true;
            }
            case "CardCelestial" -> {
                String category = card.text("cat");
                int first = card.number("md1"), second = card.number("md2");
                success = category != null && first >= 0 && first <= 12
                        && second >= 0 && second <= 12 && first != second;
                if (success) {
                    var selection = new LegacyCelestialTheoryCard.Selection(
                            first, second, category);
                    var outcome = LegacyCelestialTheoryCard.activate(selection,
                            (LegacyCelestialTheoryCard.BoundedRandom) playerRandom::nextInt,
                            new int[]{0, 1});
                    table.applyCelestialEffects(outcome, warp);
                }
            }
            case "CardConcentrate" -> {
                success = aspects.get(card.text("aspect")) != null;
                if (success) LegacyAlchemyTheoryCards.concentrate(table,
                        LegacyTheorycraftMiscCards.fromJavaRandom(playerRandom));
            }
            case "CardReactions" -> {
                String a = card.text("aspect1"), b = card.text("aspect2");
                success = a != null && b != null && !a.equals(b)
                        && aspects.get(a) != null && aspects.get(b) != null;
                if (success) LegacyAlchemyTheoryCards.reactions(table,
                        LegacyTheorycraftMiscCards.fromJavaRandom(playerRandom));
            }
            case "CardSynthesis" -> {
                String a = card.text("aspect1"), b = card.text("aspect2");
                String c = card.text("aspect3");
                AspectData compound = c == null ? null : aspects.get(c);
                success = a != null && b != null && compound != null
                        && compound.components() != null
                        && compound.components().size() == 2
                        && compound.components().get(0).tag().equals(a)
                        && compound.components().get(1).tag().equals(b);
                if (success) {
                    var selection = new LegacyAlchemyTheoryCards.Synthesis(
                            compound.components().get(0),
                            compound.components().get(1), compound);
                    crystal = LegacyAlchemyTheoryCards.synthesisAfterInputsConsumed(
                            table, selection,
                            LegacyTheorycraftMiscCards.fromJavaRandom(playerRandom)).tag();
                }
            }
            case "CardCalibrate" -> {
                LegacySpecialtyTheoryCards.calibrate(table);
                success = true;
            }
            case "CardMindOverMatter" -> {
                table.addTotal("ARTIFICE", itemVisSize < 0
                        ? LegacyMaterialTheoryCards.fallbackReward(card.key())
                        : LegacyMaterialTheoryCards.mindOverMatterReward(itemVisSize));
                success = true;
            }
            case "CardTinker" -> {
                int base = itemVisSize < 0 ? 0
                        : 2 * LegacyMaterialTheoryCards.tinkerBaseValue(itemVisSize);
                table.addTotal("ARTIFICE", base + playerRandom.nextInt(11));
                success = true;
            }
            case "CardMeasure" -> {
                LegacyTheorycraftMiscCards.measure(table);
                success = true;
            }
            case "CardChannel" -> {
                success = card.text("aspect") != null && aspects.get(card.text("aspect")) != null;
                if (success) LegacyAlchemyTheoryCards.channel(table);
            }
            case "CardInfuse" -> {
                success = card.text("aspect") != null && aspects.get(card.text("aspect")) != null;
                if (success) table.addTotal("INFUSION", itemVisSize < 0
                        ? LegacyMaterialTheoryCards.fallbackReward(card.key())
                        : LegacyMaterialTheoryCards.infuseReward(itemVisSize));
            }
            case "CardFocus" -> {
                LegacySpecialtyTheoryCards.focus(table);
                success = true;
            }
            case "CardAwareness" -> {
                LegacyTheorycraftMiscCards.awareness(table, warp,
                        LegacyTheorycraftMiscCards.fromJavaRandom(playerRandom));
                success = true;
            }
            case "CardSpellbinding" -> {
                int spent = LegacyLateTheoryCards.spellbindingLevels(xpBeforeDebit);
                success = spent > 0;
                if (success) LegacyLateTheoryCards.spellbindingAfterLevelsSpent(table, spent);
            }
            case "CardSculpting" -> {
                LegacyTheorycraftMiscCards.sculpt(table);
                success = true;
            }
            case "CardScripting" -> {
                LegacySpecialtyTheoryCards.scriptingAfterMaterialsConsumed(table);
                success = true;
            }
            case "CardSynergy" -> success = LegacySpecialtyTheoryCards.synergy(table);
            case "CardDarkWhispers" -> {
                LegacyLateTheoryCards.darkWhispersAfterXpDebited(
                        table, warp, xpBeforeDebit, registeredCategories, playerRandom);
                success = true;
            }
            case "CardGlyphs" -> success = LegacySpecialtyTheoryCards.glyphs(
                    table, warp, registeredCategories, playerRandom::nextInt);
            case "CardPortal" -> success = LegacyLateTheoryCards.portal(
                    table, warp, registeredCategories, playerRandom);
            case "CardRevelation" -> success = LegacyLateTheoryCards.revelation(
                    table, warp, registeredCategories, playerRandom);
            case "CardRealization" -> success = LegacyLateTheoryCards.realization(
                    table, warp, registeredCategories, playerRandom);
            default -> throw new AssertionError("unhandled BETA26 theory card: " + card.key());
        }

        if (!success) return null;
        // BETA26 ContainerResearchTable subtracts signed inspiration cost
        // only AFTER card.activate() returns true. The table fields and
        // player state are still only private copies at this point.
        table.consumeInspirationAfterActivation(
                LegacyTheorycraftCardMetadata.get(card.key()).cost());
        return new Outcome(table.snapshot(), knowledge.copyKnowledgeRaw(),
                warp.copyLegacyWarpArray(), warp.getCounter(), crystal);
    }
}
