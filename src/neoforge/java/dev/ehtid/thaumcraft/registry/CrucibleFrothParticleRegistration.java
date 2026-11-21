package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated BETA26 Crucible froth particle types; no shared particle registry edit. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CrucibleFrothParticleRegistration {
    public static final ResourceLocation SURFACE_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "crucible_froth");
    public static final ResourceLocation OVERFLOW_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "crucible_froth_down");
    public static final SimpleParticleType SURFACE = new SimpleParticleType(false);
    public static final SimpleParticleType OVERFLOW = new SimpleParticleType(false);

    private CrucibleFrothParticleRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> {
            helper.register(SURFACE_ID, SURFACE);
            helper.register(OVERFLOW_ID, OVERFLOW);
        });
    }
}
