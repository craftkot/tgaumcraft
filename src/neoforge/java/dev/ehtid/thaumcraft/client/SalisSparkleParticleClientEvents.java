package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.SalisSparkleParticleRegistration;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client provider for the original BETA26 FXGeneric Salis sparkle atlas. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SalisSparkleParticleClientEvents {
    private SalisSparkleParticleClientEvents() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        SimpleParticleType type = SalisSparkleParticleRegistration.TYPE;
        event.registerSpriteSet(type, SalisSparkleParticle.Provider::new);
    }
}
