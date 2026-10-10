package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Original BETA26 TheorycraftCard.getRequiredItems() and
 * getRequiredItemsConsumed() blueprint for ALL 33 registered cards.
 *
 * These records are raw historical item requirements, NOT live ItemStacks:
 * they preserve source-item identity and field names without inventing
 * replacements for unported items, metadata or legacy 1.12 NBT.
 *
 * Absent getRequiredItems overrides return null; an inherited
 * getRequiredItemsConsumed returns one FALSE for each present item.
 * Values were checked against actual BETA26 javap bytecode.
 */
public final class LegacyTheorycraftRequiredItems {
    private LegacyTheorycraftRequiredItems() {}

    public enum Source {
        /** The original Thaumcraft celestialNotes item with md1/md2 metadata. */
        CELESTIAL_NOTE,
        /** ItemPhial.makeFilledPhial(aspect). */
        FILLED_PHIAL,
        /** ThaumcraftApiHelper.makeCrystal(aspect). */
        ASPECT_CRYSTAL,
        /** Saved original ItemStack NBT chosen by initialize(). */
        SAVED_ITEM_STACK,
        /** Original Items.field_151119_aD = Minecraft 1.12 clay_ball. */
        VANILLA_CLAY_BALL
    }

    public record Requirement(Source source, String cardField, boolean consumed) {
        public Requirement {
            Objects.requireNonNull(source);
            if (source != Source.VANILLA_CLAY_BALL) {
                if (cardField == null || cardField.isBlank())
                    throw new IllegalArgumentException("original card field");
            }
        }
    }

    private static Requirement consumed(Source type, String field) {
        return new Requirement(type, field, true);
    }

    private static Requirement kept(Source type, String field) {
        return new Requirement(type, field, false);
    }

    public static List<Requirement> get(String cardKey) {
        if (cardKey == null || !LegacyTheorycraftManifest.allCardKeys().contains(cardKey))
            throw new IllegalArgumentException("unregistered original BETA26 card: " + cardKey);
        String cls = cardKey.substring(cardKey.lastIndexOf('.') + 1);
        return switch (cls) {
            case "CardCelestial" -> List.of(
                    consumed(Source.CELESTIAL_NOTE, "md1"),
                    consumed(Source.CELESTIAL_NOTE, "md2"));
            case "CardConcentrate" -> List.of(
                    kept(Source.ASPECT_CRYSTAL, "aspect"));
            case "CardChannel" -> List.of(
                    kept(Source.FILLED_PHIAL, "aspect"));
            case "CardCurio" -> List.of(
                    consumed(Source.SAVED_ITEM_STACK, "stack"));
            case "CardInfuse" -> List.of(
                    consumed(Source.SAVED_ITEM_STACK, "stack"),
                    consumed(Source.FILLED_PHIAL, "aspect"));
            case "CardMindOverMatter" -> List.of(
                    consumed(Source.SAVED_ITEM_STACK, "stack"));
            case "CardReactions" -> List.of(
                    kept(Source.ASPECT_CRYSTAL, "aspect1"),
                    kept(Source.ASPECT_CRYSTAL, "aspect2"));
            case "CardSculpting" -> List.of(
                    consumed(Source.VANILLA_CLAY_BALL, null));
            case "CardSynthesis" -> List.of(
                    consumed(Source.ASPECT_CRYSTAL, "aspect1"),
                    consumed(Source.ASPECT_CRYSTAL, "aspect2"));
            case "CardTinker" -> List.of(
                    kept(Source.SAVED_ITEM_STACK, "stack"));
            default -> List.of();
        };
    }

    public static boolean[] consumedFlags(String cardKey) {
        var req = get(cardKey);
        boolean[] flags = new boolean[req.size()];
        for (int i = 0; i < flags.length; i++) flags[i] = req.get(i).consumed();
        return flags;
    }

    public static int requiredItemCount(String cardKey) { return get(cardKey).size(); }
}
