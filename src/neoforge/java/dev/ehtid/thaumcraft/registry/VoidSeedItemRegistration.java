package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.VoidSeedItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated BETA26 void-seed item registration; no shared item-registry aggregator edit. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VoidSeedItemRegistration {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "void_seed");
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);
    private static final ResourceKey<CreativeModeTab> THAUMCRAFT_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "thaumcraft"));

    private VoidSeedItemRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> helper.register(
                ID, new VoidSeedItem(new Item.Properties().setId(ITEM_KEY).stacksTo(64))));
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (THAUMCRAFT_TAB_KEY.equals(event.getTabKey())) {
            event.accept(get());
        }
    }

    public static Item get() {
        Holder<Item> holder = BuiltInRegistries.ITEM.getHolderOrThrow(ITEM_KEY);
        return holder.value();
    }
}
