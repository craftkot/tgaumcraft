package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.item.CurioItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.item.ThaumcraftNuggetItem;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Original Thaumcraft creative tab restored for the currently registered
 * BETA26 blocks/items. Not a cheat mechanism: only creative-mode browsing,
 * while right-click and research mechanics still enforce the real server
 * prerequisites and resources.
 *
 * Explicit all-variant stacks make creative client playtests possible
 * without manufacturing a loot/research route not present in BETA26.
 */
public final class ThaumcraftCreativeTabs {
    private ThaumcraftCreativeTabs() {}

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThaumcraftPort.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("thaumcraft",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.thaumcraft"))
                    .icon(() -> new ItemStack(ThaumcraftItems.THAUMONOMICON.get()))
                    .displayItems((parameters, output) -> {
                        // Blocks and first-steps research infrastructure.
                        output.accept(ThaumcraftItems.TABLE_WOOD.get());
                        output.accept(ThaumcraftItems.RESEARCH_TABLE.get());
                        output.accept(ThaumcraftItems.CRUCIBLE.get());
                        output.accept(ThaumcraftItems.PEDESTAL_ARCANE.get());
                        output.accept(ThaumcraftItems.PEDESTAL_ANCIENT.get());
                        output.accept(ThaumcraftItems.PEDESTAL_ELDRITCH.get());
                        output.accept(ThaumcraftItems.PILLAR_ARCANE.get());
                        output.accept(ThaumcraftItems.PILLAR_ANCIENT.get());
                        output.accept(ThaumcraftItems.PILLAR_ELDRITCH.get());
                        output.accept(ThaumcraftItems.INFUSION_MATRIX.get());
                        // All sixteen original dyed light/heater variants.
                        for (var color : dev.ehtid.thaumcraft.port.core.LegacyNitorColors.variants()) {
                            var dye = net.minecraft.world.item.DyeColor.valueOf(
                                    color.modernDye().toUpperCase(java.util.Locale.ROOT));
                            output.accept(ThaumcraftItems.NITOR.get(dye).get());
                        }
                        output.accept(ThaumcraftItems.ARCANE_WORKBENCH.get());
                        output.accept(ThaumcraftItems.THAUMONOMICON.get());
                        output.accept(ThaumcraftItems.THAUMOMETER.get());
                        output.accept(ThaumcraftItems.SCRIBING_TOOLS.get());
                        output.accept(ThaumcraftItems.SALIS_MUNDUS.get());
                        output.accept(ThaumcraftItems.CRYSTAL_ESSENCE.get());
                        // Original unvariant crafting ingredients, available
                        // for creative playtests before their source recipes
                        // and world-generation routes are fully migrated.
                        output.accept(ThaumcraftItems.VIS_RESONATOR.get());
                        output.accept(ThaumcraftItems.AMBER.get());
                        output.accept(ThaumcraftItems.QUICKSILVER.get());
                        output.accept(ThaumcraftItems.FABRIC.get());
                        output.accept(ThaumcraftItems.TALLOW.get());
                        output.accept(ThaumcraftItems.ALUMENTUM.get());
                        // Original eleven nugget variants, incl. quartz sliver
                        // used by all thirty-seven vis crystal recipes.
                        for (int metadata = 0; metadata < 11; metadata++)
                            output.accept(ThaumcraftNuggetItem.create(metadata));
                        // BETA26 single ingot item: thaumium, void metal, brass.
                        for (int metadata = 0; metadata < 3; metadata++)
                            output.accept(ThaumcraftIngotItem.create(metadata));
                        // All original BETA26 item metadata variants.
                        for (int variant = 0; variant < 4; variant++)
                            output.accept(PlateItem.create(variant));

                        // A distinct stack for every original 1.12 variant.
                        for (int variant = 0; variant < 7; variant++)
                            output.accept(CurioItem.create(variant));
                        for (int variant = 0; variant <= 12; variant++)
                            output.accept(CelestialNotesItem.create(variant));

                        output.accept(PhialItem.emptyPhial());
                        for (AspectData aspect : new AspectRegistry().values())
                            output.accept(PhialItem.filledPhial(aspect.tag()));
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
