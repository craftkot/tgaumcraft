package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.SalisMundusItem;
import dev.ehtid.thaumcraft.item.ThaumometerItem;
import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.item.ScribingToolsItem;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.item.CurioItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
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
    public static final DeferredItem<Item> THAUMONOMICON = ITEMS.registerSimpleItem(
            "thaumonomicon", new Item.Properties().stacksTo(1));
    public static final DeferredItem<ThaumometerItem> THAUMOMETER = ITEMS.registerItem(
            "thaumometer", ThaumometerItem::new, new Item.Properties().stacksTo(1));
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

    // Original plate ItemTCBase metadata 0..3: brass, iron, thaumium, void.
    // Original single ItemsTC.ingots registry with metadata 0/1/2.
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

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
