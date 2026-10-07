package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.SalisMundusItem;
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
    public static final DeferredItem<Item> THAUMOMETER = ITEMS.registerSimpleItem(
            "thaumometer", new Item.Properties().stacksTo(1));
    public static final DeferredItem<BlockItem> ARCANE_WORKBENCH = ITEMS.registerSimpleBlockItem(
            ThaumcraftBlocks.ARCANE_WORKBENCH, new Item.Properties());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
