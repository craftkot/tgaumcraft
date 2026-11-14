package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Isolated BETA26 Nitor particle types.
 * Kept separate from the shared particle/bootstrap registry so this M16
 * renderer port does not collide with another feature lane.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NitorParticleRegistration {
    public static final ResourceLocation FLAME_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "nitor_flame");
    public static final ResourceLocation CORE_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "nitor_core");

    public static final SimpleParticleType FLAME_TYPE = new SimpleParticleType(false);
    public static final SimpleParticleType CORE_TYPE = new SimpleParticleType(false);

    private NitorParticleRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> {
            helper.register(FLAME_ID, FLAME_TYPE);
            helper.register(CORE_ID, CORE_TYPE);
        });
    }
}
