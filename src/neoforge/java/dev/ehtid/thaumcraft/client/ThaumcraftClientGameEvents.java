package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.FluxRiftEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Main client event-bus hooks, kept separate from MOD-bus screen registration. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ThaumcraftClientGameEvents {
    private ThaumcraftClientGameEvents() {}

    @SubscribeEvent
    public static void onFluxRiftAmbientSound(PlayLevelSoundEvent.AtEntity event) {
        if (!"1".equals(System.getenv("THAUMCRAFT_SMOKE"))) {
            return;
        }
        if (event.getEntity() instanceof FluxRiftEntity rift
                && event.getSound().value() == ThaumcraftSounds.EVILPORTAL.get()) {
            System.out.println("TC_SMOKE_RIFT_SOUND_CLIENT_EVENT=YES entityId=" + rift.getId()
                    + " volume=" + event.getOriginalVolume()
                    + " pitch=" + event.getOriginalPitch()
                    + " source=" + event.getSource()
                    + " canceled=" + event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ThaumometerHudRenderer.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
