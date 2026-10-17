package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import dev.ehtid.thaumcraft.registry.ThaumcraftParticles;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.client.PedestalItemRenderer;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.block.TallowCandleBlock;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.world.item.BlockItem;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyNitorColors;
import net.minecraft.world.item.DyeColor;
import java.util.Locale;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client-only screen registration. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ThaumcraftClientEvents {
    private ThaumcraftClientEvents() {}

    @SubscribeEvent
    public static void registerCrucibleRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ThaumcraftBlockEntities.CRUCIBLE.get(), CrucibleFluidRenderer::new);
        event.registerBlockEntityRenderer(
                ThaumcraftBlockEntities.PEDESTAL.get(), PedestalItemRenderer::new);
    }

    @SubscribeEvent
    public static void registerAlumentumRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ThaumcraftEntities.ALUMENTUM.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ThaumcraftMenus.ARCANE_WORKBENCH.get(), ArcaneWorkbenchScreen::new);
        event.register(ThaumcraftMenus.RESEARCH_TABLE.get(), ResearchTableScreen::new);
        event.register(ThaumcraftMenus.SMELTER_BASIC.get(), SmelterScreen::new);
    }

    /** BETA26 BlockCrystal paints its original uncolored mesh by Aspect. */
    @SubscribeEvent
    public static void registerCrystalBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        AspectRegistry aspects = new AspectRegistry();
        event.register((state, world, pos, tint) -> {
            if (tint != 0 || !(state.getBlock() instanceof CrystalClusterBlock crystal))
                return 0xFFFFFFFF;
            var aspect = aspects.get(crystal.aspectTag());
            return aspect == null ? 0xFFFFFFFF : (0xFF000000 | aspect.color());
        }, ThaumcraftBlocks.CRYSTAL_AER.get(), ThaumcraftBlocks.CRYSTAL_IGNIS.get(),
           ThaumcraftBlocks.CRYSTAL_AQUA.get(), ThaumcraftBlocks.CRYSTAL_TERRA.get(),
           ThaumcraftBlocks.CRYSTAL_ORDO.get(), ThaumcraftBlocks.CRYSTAL_PERDITIO.get(),
           ThaumcraftBlocks.CRYSTAL_VITIUM.get());
    }

    @SubscribeEvent
    public static void registerCandleBlockColors(RegisterColorHandlersEvent.Block event) {
        var candles = ThaumcraftBlocks.CANDLES.values().stream()
                .map(holder -> holder.get()).toArray(net.minecraft.world.level.block.Block[]::new);
        event.register((state, world, pos, tint) ->
                        tint == 0 && state.getBlock() instanceof TallowCandleBlock candle
                                ? 0xFF000000 | candle.waxRgb() : 0xFFFFFFFF, candles);
    }

    /** BETA26 phial overlay is tinted with its contained Aspect color. */
    @SubscribeEvent
    public static void registerItemColorHandlers(RegisterColorHandlersEvent.Item event) {
        AspectRegistry aspects = new AspectRegistry();
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return 0xFFFFFFFF; // Keep the glass unchanged.
            String tag = PhialItem.containedAspect(stack);
            var aspect = tag == null ? null : aspects.get(tag);
            return aspect == null ? 0xFFFFFFFF : (0xFF000000 | aspect.color());
        }, ThaumcraftItems.PHIAL.get());

        // Wax tinted to the original dye; wick texture remains untinted.
        for (LegacyNitorColors.Variant color : LegacyNitorColors.variants()) {
            DyeColor dye = DyeColor.valueOf(color.modernDye().toUpperCase(Locale.ROOT));
            int rgba = 0xFF000000 | color.particleRgb();
            event.register((stack, tint) -> tint == 0 ? rgba : 0xFFFFFFFF,
                    ThaumcraftItems.CANDLES.get(dye).get());
        }
        // Original BETA26 greyscale flame layer is dyed per BlockNitor,
        // while the bright white core is always un-tinted.
        for (LegacyNitorColors.Variant variant : LegacyNitorColors.variants()) {
            DyeColor dye = DyeColor.valueOf(variant.modernDye()
                    .toUpperCase(Locale.ROOT));
            int rgba = 0xFF000000 | variant.particleRgb();
            event.register((stack, tintIndex) ->
                    tintIndex == 0 ? rgba : 0xFFFFFFFF,
                    ThaumcraftItems.NITOR.get(dye).get());
        }

        // All original seven placer icons are dyed by their owning block Aspect.
        for (var item : java.util.List.of(
                ThaumcraftItems.CRYSTAL_AER.get(), ThaumcraftItems.CRYSTAL_IGNIS.get(),
                ThaumcraftItems.CRYSTAL_AQUA.get(), ThaumcraftItems.CRYSTAL_TERRA.get(),
                ThaumcraftItems.CRYSTAL_ORDO.get(), ThaumcraftItems.CRYSTAL_PERDITIO.get(),
                ThaumcraftItems.CRYSTAL_VITIUM.get())) {
            if (item.getBlock() instanceof CrystalClusterBlock crystal) {
                var aspect = aspects.get(crystal.aspectTag());
                final int color = aspect == null ? 0xFFFFFFFF : (0xFF000000 | aspect.color());
                event.register((stack, tintIndex) -> tintIndex==0 ? color : 0xFFFFFFFF, item);
            }
        }

        // TC6 crystals tint their original white translucent icon by the
        // one contained primal/compound Aspect, not a hard-coded color.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return 0xFFFFFFFF;
            String tag = CrystalEssenceItem.legacyAspect(stack);
            var aspect = tag == null ? null : aspects.get(tag);
            return aspect == null ? 0xFFFFFFFF : (0xFF000000 | aspect.color());
        }, ThaumcraftItems.CRYSTAL_ESSENCE.get());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ThaumcraftParticles.SCAN_RUNE.get(), ScanRuneParticle.Provider::new);
    }
}
