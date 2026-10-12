package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import dev.ehtid.thaumcraft.registry.ThaumcraftParticles;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
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
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ThaumcraftMenus.ARCANE_WORKBENCH.get(), ArcaneWorkbenchScreen::new);
        event.register(ThaumcraftMenus.RESEARCH_TABLE.get(), ResearchTableScreen::new);
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

        // The original Nitor item has two greyscale icon layers. The
        // yellow default block is dyed at layer 0, leaving the glowing
        // core layer 1 bright just like the BETA26 dual-layer icon.
        event.register((stack, tintIndex) ->
                        tintIndex == 0 ? 0xFFFFD64A : 0xFFFFFFFF,
                ThaumcraftItems.NITOR_YELLOW.get());

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
