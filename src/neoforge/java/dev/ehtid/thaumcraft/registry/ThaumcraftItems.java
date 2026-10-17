package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.AlumentumItem;
import dev.ehtid.thaumcraft.item.SalisMundusItem;
import dev.ehtid.thaumcraft.item.ThaumometerItem;
import dev.ehtid.thaumcraft.item.ThaumonomiconItem;
import dev.ehtid.thaumcraft.item.BasicCasterItem;
import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.item.ScribingToolsItem;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.item.EssentiaJarItem;
import dev.ehtid.thaumcraft.item.JarBraceItem;
import dev.ehtid.thaumcraft.item.JarLabelItem;
import dev.ehtid.thaumcraft.item.CurioItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.item.ThaumcraftNuggetItem;
import dev.ehtid.thaumcraft.port.core.LegacyNitorColors;
import net.minecraft.world.item.DyeColor;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Minimal item surface required to play through FIRSTSTEPS. */
public final class ThaumcraftItems {
    private ThaumcraftItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ThaumcraftPort.MOD_ID);

    public static final DeferredItem<CrystalEssenceItem> CRYSTAL_ESSENCE = ITEMS.registerItem(
            "crystal_essence", CrystalEssenceItem::new, new Item.Properties());
    public static final DeferredItem<SalisMundusItem> SALIS_MUNDUS = ITEMS.registerItem(
            "salis_mundus", SalisMundusItem::new, new Item.Properties());
    public static final DeferredItem<ThaumonomiconItem> THAUMONOMICON = ITEMS.registerItem(
            "thaumonomicon", ThaumonomiconItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<ThaumometerItem> THAUMOMETER = ITEMS.registerItem(
            "thaumometer", ThaumometerItem::new, new Item.Properties().stacksTo(1));
    /** Original BETA26 ItemCaster basic gauntlet: matrix caster interactions. */
    public static final DeferredItem<BasicCasterItem> CASTER_BASIC = ITEMS.registerItem(
            "caster_basic", BasicCasterItem::new, new Item.Properties().stacksTo(1));
    // ScanSky uses a nonconsumable 100-durability scribing tool, one paper per
    // celestial observation, and one of thirteen BETA26 celestial notes.
    public static final DeferredItem<ScribingToolsItem> SCRIBING_TOOLS = ITEMS.registerItem(
            "scribing_tools", ScribingToolsItem::new,
            new Item.Properties().durability(100).stacksTo(1));
    public static final DeferredItem<CelestialNotesItem> CELESTIAL_NOTES = ITEMS.registerItem(
            "celestial_notes", CelestialNotesItem::new, new Item.Properties());

    // BETA26 ItemPhial: original capacity 10, empty meta 0 / filled meta 1.
    public static final DeferredItem<PhialItem> PHIAL = ITEMS.registerItem(
            "phial", PhialItem::new, new Item.Properties().stacksTo(64));

    // Original BETA26 ItemCurio has seven metadata variants (0..6).
    public static final DeferredItem<CurioItem> CURIO = ITEMS.registerItem(
            "curio", CurioItem::new,
            new Item.Properties().stacksTo(64).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    // Four original zero-variant ItemTCBase materials (verified from BETA26 ConfigItems).
    public static final DeferredItem<Item> VIS_RESONATOR = ITEMS.registerSimpleItem(
            "vis_resonator", new Item.Properties());
    public static final DeferredItem<Item> AMBER = ITEMS.registerSimpleItem(
            "amber", new Item.Properties());
    public static final DeferredItem<Item> QUICKSILVER = ITEMS.registerSimpleItem(
            "quicksilver", new Item.Properties());
    public static final DeferredItem<Item> FABRIC = ITEMS.registerSimpleItem(
            "fabric", new Item.Properties());

    // Original throwable explosive, not an inert ingredient.
    public static final DeferredItem<AlumentumItem> ALUMENTUM = ITEMS.registerItem(
            "alumentum", AlumentumItem::new, new Item.Properties().stacksTo(64));

    // Original BETA26 ItemsTC.tallow, product of HEDGEALCHEMY@1 crucible.
    public static final DeferredItem<Item> TALLOW = ITEMS.registerSimpleItem(
            "tallow", new Item.Properties());

    // Original plate ItemTCBase metadata 0..3: brass, iron, thaumium, void.
    // Original single ItemsTC.ingots registry with metadata 0/1/2.
    // The original nugget registry has eleven item metadata variants.
    public static final DeferredItem<ThaumcraftNuggetItem> NUGGET = ITEMS.registerItem(
            "nugget", ThaumcraftNuggetItem::new, new Item.Properties());

    public static final DeferredItem<ThaumcraftIngotItem> INGOT = ITEMS.registerItem(
            "ingot", ThaumcraftIngotItem::new, new Item.Properties());

    public static final DeferredItem<PlateItem> PLATE = ITEMS.registerItem(
            "plate", PlateItem::new, new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_WORKBENCH = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.ARCANE_WORKBENCH, new Item.Properties());

    public static final DeferredItem<BlockItem> TABLE_WOOD = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.TABLE_WOOD, new Item.Properties());
    public static final DeferredItem<BlockItem> RESEARCH_TABLE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.RESEARCH_TABLE, new Item.Properties());

    public static final DeferredItem<BlockItem> CRUCIBLE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRUCIBLE, new Item.Properties());

    public static final DeferredItem<BlockItem> PEDESTAL_ARCANE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.PEDESTAL_ARCANE, new Item.Properties());
    public static final DeferredItem<BlockItem> PEDESTAL_ANCIENT = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.PEDESTAL_ANCIENT, new Item.Properties());
    public static final DeferredItem<BlockItem> PEDESTAL_ELDRITCH = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.PEDESTAL_ELDRITCH, new Item.Properties());

    public static final DeferredItem<BlockItem> INFUSION_MATRIX = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.INFUSION_MATRIX, new Item.Properties());

    public static final DeferredItem<EssentiaJarItem> JAR_NORMAL = ITEMS.registerItem(
            "jar_normal", properties -> new EssentiaJarItem(ThaumcraftBlocks.JAR_NORMAL.get(), properties),
            new Item.Properties());

    public static final DeferredItem<EssentiaJarItem> JAR_VOID = ITEMS.registerItem(
            "jar_void", properties -> new EssentiaJarItem(ThaumcraftBlocks.JAR_VOID.get(), properties),
            new Item.Properties());

    public static final DeferredItem<BlockItem> TUBE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.TUBE, new Item.Properties());

    public static final DeferredItem<BlockItem> ALEMBIC = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.ALEMBIC, new Item.Properties());

    public static final DeferredItem<BlockItem> SMELTER_BASIC = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.SMELTER_BASIC, new Item.Properties());

    /** Original ItemsTC.jarBrace: applied once and recovered when jar breaks. */
    public static final DeferredItem<JarBraceItem> JAR_BRACE = ITEMS.registerItem(
            "jar_brace", JarBraceItem::new, new Item.Properties());

    /** Original ItemLabel: empty and one-aspect filtered NBT variants. */
    public static final DeferredItem<JarLabelItem> JAR_LABEL = ITEMS.registerItem(
            "label", JarLabelItem::new, new Item.Properties());

    public static final DeferredItem<BlockItem> STONE_ARCANE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.STONE_ARCANE, new Item.Properties());
    public static final DeferredItem<BlockItem> STONE_ANCIENT = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.STONE_ANCIENT, new Item.Properties());
    public static final DeferredItem<BlockItem> STONE_ELDRITCH_TILE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.STONE_ELDRITCH_TILE, new Item.Properties());

    public static final DeferredItem<BlockItem> PILLAR_ARCANE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.PILLAR_ARCANE, new Item.Properties());
    public static final DeferredItem<BlockItem> PILLAR_ANCIENT = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.PILLAR_ANCIENT, new Item.Properties());
    public static final DeferredItem<BlockItem> PILLAR_ELDRITCH = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.PILLAR_ELDRITCH, new Item.Properties());

    public static final DeferredItem<BlockItem> CRYSTAL_AER = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_AER, new Item.Properties());
    public static final DeferredItem<BlockItem> CRYSTAL_IGNIS = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_IGNIS, new Item.Properties());
    public static final DeferredItem<BlockItem> CRYSTAL_AQUA = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_AQUA, new Item.Properties());
    public static final DeferredItem<BlockItem> CRYSTAL_TERRA = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_TERRA, new Item.Properties());
    public static final DeferredItem<BlockItem> CRYSTAL_ORDO = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_ORDO, new Item.Properties());
    public static final DeferredItem<BlockItem> CRYSTAL_PERDITIO = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_PERDITIO, new Item.Properties());
    public static final DeferredItem<BlockItem> CRYSTAL_VITIUM = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.CRYSTAL_VITIUM, new Item.Properties());

    /** Every BETA26 Nitor block has its OWN matching BlockItem. */
    public static final Map<DyeColor, DeferredItem<BlockItem>> NITOR =
            registerAllNitorItems();
    public static final DeferredItem<BlockItem> NITOR_YELLOW = NITOR.get(DyeColor.YELLOW);

    public static final Map<DyeColor, DeferredItem<BlockItem>> CANDLES = registerCandleItems();
    public static final DeferredItem<BlockItem> CANDLE_WHITE = CANDLES.get(DyeColor.WHITE);
    private static Map<DyeColor, DeferredItem<BlockItem>> registerCandleItems() {
        Map<DyeColor, DeferredItem<BlockItem>> map = new EnumMap<>(DyeColor.class);
        for (LegacyNitorColors.Variant variant : LegacyNitorColors.variants()) {
            DyeColor dye = DyeColor.valueOf(variant.modernDye().toUpperCase(Locale.ROOT));
            map.put(dye, ITEMS.registerSimpleBlockItem(
                    ThaumcraftBlocks.CANDLES.get(dye), new Item.Properties()));
        }
        if (map.size() != 16) throw new IllegalStateException("Missing candle items");
        return Collections.unmodifiableMap(map);
    }

    private static Map<DyeColor, DeferredItem<BlockItem>> registerAllNitorItems() {
        Map<DyeColor, DeferredItem<BlockItem>> registered =
                new EnumMap<>(DyeColor.class);
        for (LegacyNitorColors.Variant color : LegacyNitorColors.variants()) {
            DyeColor dye = DyeColor.valueOf(color.modernDye().toUpperCase(Locale.ROOT));
            registered.put(dye, ITEMS.registerSimpleBlockItem(
                    ThaumcraftBlocks.NITOR.get(dye), new Item.Properties()));
        }
        if (registered.size() != 16) throw new IllegalStateException("Missing Nitor BlockItem");
        return Collections.unmodifiableMap(registered);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
