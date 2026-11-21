package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.CrucibleBubbleParticleRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client sprite-set binding for the original particles.png bubble cells. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrucibleBubbleParticleClientEvents {
    private CrucibleBubbleParticleClientEvents() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CrucibleBubbleParticleRegistration.TYPE,
                CrucibleBubbleParticle.Provider::new);
    }
}
