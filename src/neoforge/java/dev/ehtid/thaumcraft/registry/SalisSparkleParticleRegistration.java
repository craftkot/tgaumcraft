package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Isolated BETA26 Salis sprite particle registration.
 * Kept out of the shared ThaumcraftParticles/ThaumcraftPort bootstrap so this
 * M16 visual addition does not collide with other feature lanes' registries.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SalisSparkleParticleRegistration {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "salis_sparkle");
    public static final SimpleParticleType TYPE = new SimpleParticleType(false);

    private SalisSparkleParticleRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> helper.register(ID, TYPE));
    }
}
