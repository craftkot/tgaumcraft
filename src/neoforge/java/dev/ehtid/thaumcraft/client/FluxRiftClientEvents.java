package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Isolated client renderer registration for the Flux Rift entity. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, value = Dist.CLIENT)
public final class FluxRiftClientEvents {
    private FluxRiftClientEvents() {}

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FluxRiftRegistration.get(), FluxRiftRenderer::new);
    }
}
