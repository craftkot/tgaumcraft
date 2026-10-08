package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Main client event-bus hooks, kept separate from MOD-bus screen registration. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ThaumcraftClientGameEvents {
    private ThaumcraftClientGameEvents() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ThaumometerHudRenderer.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
