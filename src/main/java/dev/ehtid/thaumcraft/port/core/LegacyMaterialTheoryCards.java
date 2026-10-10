package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The four remaining BETA26 theory-card initialization/effect engines.
 * Verified against original CardCurio, CardMindOverMatter, CardTinker and
 * CardInfuse bytecode from Thaumcraft 6.1.BETA26 (Minecraft 1.12.2).
 *
 * ItemStack identity is deliberately NOT faked here: option indices refer to
 * exact historical arrays, and the future NeoForge item bridge must resolve
 * those indices to real items, preserve original stack NBT, and atomically
 * debit consumed inputs before calling an activation method.
 */
public final class LegacyMaterialTheoryCards {
    private LegacyMaterialTheoryCards() {}

    public static final String CURIO =
            "thaumcraft.common.lib.research.theorycraft.CardCurio";
    public static final String MIND_OVER_MATTER =
            "thaumcraft.common.lib.research.theorycraft.CardMindOverMatter";
    public static final String TINKER =
            "thaumcraft.common.lib.research.theorycraft.CardTinker";
    public static final String INFUSE =
            "thaumcraft.common.lib.research.theorycraft.CardInfuse";
    public static final int INSPIRATION_COST = 1;

    // The 1.12 static initializer has 18 entries for both ARTIFICE cards,
    // and 31 for CardInfuse. Their entries must never be replaced with
    // only the currently implemented NeoForge items.
    public static final int ARTIFICE_OPTION_COUNT = 18;
    public static final int INFUSE_OPTION_COUNT = 31;

    /** Exact ItemCurio.getVariantNames() metadata order, 0..6. */
    public static final List<String> CURIO_VARIANTS = List.of(
            "arcane", "preserved", "ancient", "eldritch",
            "knowledge", "twisted", "rites");

    public record CurioSelection(int mainInventorySlot, int variantMetadata) {
        public CurioSelection {
            if (mainInventorySlot < 0 || variantMetadata < 0
                    || variantMetadata >= CURIO_VARIANTS.size())
                throw new IllegalArgumentException("original curio slot/metadata");
        }
        public String variantName() {
            return CURIO_VARIANTS.get(variantMetadata);
        }
    }

    /**
     * Original CardCurio.initialize searches InventoryPlayer.mainInventory,
     * adds one copy of EACH qualifying curio stack, including duplicate
     * variants in separate slots, and chooses by new Random(cardSeed).
     * Caller represents non-curio/empty stacks with -1.
     */
    public static CurioSelection initializeCurio(List<Integer> mainInventoryCurioVariants,
                                                   long cardSeed) {
        Objects.requireNonNull(mainInventoryCurioVariants);
        List<CurioSelection> eligible = new ArrayList<>();
        for (int slot = 0; slot < mainInventoryCurioVariants.size(); slot++) {
            Integer variant = mainInventoryCurioVariants.get(slot);
            if (variant != null && variant >= 0 && variant < CURIO_VARIANTS.size())
                eligible.add(new CurioSelection(slot, variant));
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(new Random(cardSeed).nextInt(eligible.size()));
    }

    /**
     * Original CardCurio.activate: +5 BASICS, +5 any registered category
     * in LinkedHashMap order, variant-specific bonuses, then TWO INDEPENDENT
     * nextBoolean calls for 0..2 bonus draws.
     * Only call AFTER a validated Curio item has been consumed on server.
     */
    public static boolean curioAfterItemConsumed(LegacyResearchTableData table,
                                                  int variant, List<String> categories,
                                                  Random playerRandom) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(categories);
        Objects.requireNonNull(playerRandom);
        if (variant < 0 || variant >= CURIO_VARIANTS.size() || categories.isEmpty())
            return false;

        table.addTotal("BASICS", 5);
        table.addTotal(categories.get(playerRandom.nextInt(categories.size())), 5);
        switch (variant) {
            case 0 -> table.addTotal("AUROMANCY", 25 + playerRandom.nextInt(11));
            case 1 -> table.addTotal("ALCHEMY", 25 + playerRandom.nextInt(11));
            case 2 -> table.addTotal("GOLEMANCY", 25 + playerRandom.nextInt(11));
            case 3 -> table.addTotal("ELDRITCH", 25 + playerRandom.nextInt(11));
            case 4 -> table.addTotal("INFUSION", 25 + playerRandom.nextInt(11));
            case 5 -> table.addTotal("ARTIFICE", 25 + playerRandom.nextInt(11));
            case 6 -> {
                table.addTotal("ELDRITCH", 15 + playerRandom.nextInt(6));
                table.addTotal("AUROMANCY", 10 + playerRandom.nextInt(6));
            }
            default -> throw new AssertionError("validated variant");
        }
        if (playerRandom.nextBoolean()) table.addBonusDraws(1);
        if (playerRandom.nextBoolean()) table.addBonusDraws(1);
        return true;
    }

    /** Both CardMindOverMatter and CardTinker choose one of 18 entries. */
    public static int initializeArtificeOption(long cardSeed) {
        return new Random(cardSeed).nextInt(ARTIFICE_OPTION_COUNT);
    }

    /**
     * CardInfuse initializes compound aspect FIRST and 31-entry item SECOND,
     * using the very same Random seeded with card.seed.
     */
    public record InfuseSelection(AspectData aspect, int itemOptionIndex) {
        public InfuseSelection {
            Objects.requireNonNull(aspect, "aspect");
            if (itemOptionIndex < 0 || itemOptionIndex >= INFUSE_OPTION_COUNT)
                throw new IllegalArgumentException("infusion option index");
        }
    }

    public static InfuseSelection initializeInfuse(AspectRegistry aspects, long cardSeed) {
        Objects.requireNonNull(aspects);
        List<AspectData> compound = aspects.compoundAspects();
        if (compound.isEmpty()) return null;
        Random random = new Random(cardSeed);
        AspectData aspect = compound.get(random.nextInt(compound.size()));
        return new InfuseSelection(aspect, random.nextInt(INFUSE_OPTION_COUNT));
    }

    /** The original getObjectTags().visSize() is supplied by the item adapter. */
    public static int mindOverMatterReward(int itemVisSize) {
        return (int) (10.0D + Math.sqrt(itemVisSize));
    }

    public static int tinkerBaseValue(int itemVisSize) {
        return (int) Math.sqrt(itemVisSize);
    }

    public static int infuseReward(int itemVisSize) {
        return (int) (10.0D + Math.sqrt(itemVisSize) * 1.5D);
    }

    /** Original exception fallback: Mind/Infuse return 10; Tinker returns 0. */
    public static int fallbackReward(String card) {
        return switch (card) {
            case MIND_OVER_MATTER, INFUSE -> 10;
            case TINKER -> 0;
            default -> throw new IllegalArgumentException(card);
        };
    }

    /** CardMindOverMatter consumes its selected item and grants ARTIFICE. */
    public static void mindOverMatterAfterItemConsumed(LegacyResearchTableData table,
                                                        int itemVisSize) {
        Objects.requireNonNull(table);
        table.addTotal("ARTIFICE", mindOverMatterReward(itemVisSize));
    }

    /**
     * CardTinker requires an item but INHERITS non-consumption of that item;
     * grants random integer inclusive [2*sqrt(vis), 2*sqrt(vis)+10].
     */
    public static void tinkerWithRequiredItem(LegacyResearchTableData table, int itemVisSize,
                                               Random playerRandom) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(playerRandom);
        int base = 2 * tinkerBaseValue(itemVisSize);
        table.addTotal("ARTIFICE", base + playerRandom.nextInt(11));
    }

    /** CardInfuse requires AND consumes chosen item + filled-aspect phial. */
    public static void infuseAfterBothItemsConsumed(LegacyResearchTableData table,
                                                     int itemVisSize) {
        Objects.requireNonNull(table);
        table.addTotal("INFUSION", infuseReward(itemVisSize));
    }
}
