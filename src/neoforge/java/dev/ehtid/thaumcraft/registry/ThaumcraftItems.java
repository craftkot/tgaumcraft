package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.SalisMundusItem;
import dev.ehtid.thaumcraft.item.ThaumometerItem;
import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.item.ScribingToolsItem;
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

    public static final DeferredItem<BlockItem> ARCANE_WORKBENCH = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.ARCANE_WORKBENCH, new Item.Properties());

    public static final DeferredItem<BlockItem> TABLE_WOOD = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.TABLE_WOOD, new Item.Properties());
    public static final DeferredItem<BlockItem> RESEARCH_TABLE = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.RESEARCH_TABLE, new Item.Properties());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
