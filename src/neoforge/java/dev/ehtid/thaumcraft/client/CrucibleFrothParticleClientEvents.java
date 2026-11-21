package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.CrucibleFrothParticleRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client sprite-set bindings for BETA26 Crucible froth atlas cells 64 and 73. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrucibleFrothParticleClientEvents {
    private CrucibleFrothParticleClientEvents() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CrucibleFrothParticleRegistration.SURFACE,
                sprites -> new CrucibleFrothParticle.Provider(sprites, false));
        event.registerSpriteSet(CrucibleFrothParticleRegistration.OVERFLOW,
                sprites -> new CrucibleFrothParticle.Provider(sprites, true));
    }
}
