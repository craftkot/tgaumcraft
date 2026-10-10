package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Exact ORIGINAL ItemStack[] option arrays in BETA26 CardMindOverMatter,
 * CardTinker and CardInfuse static initializers. Entries use historical
 * field identities, not guessed 1.21 registry names. Array order is part
 * of the seeded research-card behavior; duplicate entries must survive.
 *
 * Metadata is -1 when source bytecode used ItemStack(item/block), otherwise
 * 0 or 2 when it explicitly called ItemStack(item, 1, metadata).
 * For nitor the source used BlocksTC.nitor.get(EnumDyeColor.YELLOW).
 */
public final class LegacyTheorycraftOptionPools {
    private LegacyTheorycraftOptionPools() {}

    public enum SourceKind { TC_ITEM, TC_COLORED_BLOCK, VANILLA_BLOCK, VANILLA_ITEM }

    public record Option(SourceKind kind, String historicalField, int metadata, String variant) {
        public Option {
            Objects.requireNonNull(kind);
            if (historicalField == null || historicalField.isBlank())
                throw new IllegalArgumentException("original item field");
            if (metadata < -1) throw new IllegalArgumentException("historical metadata");
            if (variant != null && kind != SourceKind.TC_COLORED_BLOCK)
                throw new IllegalArgumentException("colored-block variant on other item");
        }
    }

    private static Option tc(String field) { return new Option(SourceKind.TC_ITEM, field, -1, null); }
    private static Option tc(String field, int damage) {
        return new Option(SourceKind.TC_ITEM, field, damage, null);
    }
    private static Option color(String field, String dye) {
        return new Option(SourceKind.TC_COLORED_BLOCK, field, -1, dye);
    }
    private static Option block(String field) {
        return new Option(SourceKind.VANILLA_BLOCK, field, -1, null);
    }
    private static Option item(String field) {
        return new Option(SourceKind.VANILLA_ITEM, field, -1, null);
    }

    /**
     * Both original ARTIFICE cards define precisely the SAME 18 slots,
     * but each card's initializer draws separately from its own seeded RNG.
     */
    public static final List<Option> ARTIFICE = List.of(
            tc("ItemsTC.visResonator"),
            tc("ItemsTC.thaumometer"),
            block("Blocks.field_150467_bQ"),
            block("Blocks.field_150408_cc"),
            block("Blocks.field_150367_z"),
            block("Blocks.field_150409_cd"),
            block("Blocks.field_150381_bn"),
            block("Blocks.field_150477_bB"),
            block("Blocks.field_150421_aI"),
            block("Blocks.field_150453_bW"),
            block("Blocks.field_150331_J"),
            block("Blocks.field_150438_bZ"),
            block("Blocks.field_150320_F"),
            item("Items.field_151148_bJ"),
            item("Items.field_151111_aL"),
            item("Items.field_151142_bV"),
            item("Items.field_151132_bS"),
            item("Items.field_151113_aN")
    );

    /** Exact 31-entry CardInfuse.options; nitor = YELLOW, ingot damage 0/2. */
    public static final List<Option> INFUSION = List.of(
            tc("ItemsTC.alumentum"),
            color("BlocksTC.nitor", "YELLOW"),
            tc("ItemsTC.amber"),
            tc("ItemsTC.brain"),
            tc("ItemsTC.fabric"),
            tc("ItemsTC.salisMundus"),
            tc("ItemsTC.ingots", 0),
            tc("ItemsTC.ingots", 2),
            tc("ItemsTC.quicksilver"),
            item("Items.field_151043_k"),
            item("Items.field_151042_j"),
            item("Items.field_151045_i"),
            item("Items.field_151166_bC"),
            item("Items.field_151072_bj"),
            item("Items.field_151116_aA"),
            block("Blocks.field_150325_L"),
            item("Items.field_151118_aC"),
            item("Items.field_151032_g"),
            item("Items.field_151110_aK"),
            item("Items.field_151008_G"),
            item("Items.field_151114_aO"),
            item("Items.field_151137_ax"),
            item("Items.field_151073_bk"),
            item("Items.field_151016_H"),
            item("Items.field_151031_f"),
            item("Items.field_151010_B"),
            item("Items.field_151040_l"),
            item("Items.field_151035_b"),
            item("Items.field_151005_D"),
            item("Items.field_151128_bU"),
            item("Items.field_151034_e")
    );

    static {
        if (ARTIFICE.size() != 18 || INFUSION.size() != 31)
            throw new ExceptionInInitializerError("original BETA26 card array count");
    }

    public static Option artifice(long cardSeed) {
        return ARTIFICE.get(LegacyMaterialTheoryCards.initializeArtificeOption(cardSeed));
    }

    public record InfusionPick(AspectData compoundAspect, Option requiredItem) {
        public InfusionPick {
            Objects.requireNonNull(compoundAspect);
            Objects.requireNonNull(requiredItem);
        }
    }

    public static InfusionPick infusion(AspectRegistry aspects, long cardSeed) {
        var draw = LegacyMaterialTheoryCards.initializeInfuse(aspects, cardSeed);
        return draw == null ? null : new InfusionPick(
                draw.aspect(), INFUSION.get(draw.itemOptionIndex()));
    }
}
