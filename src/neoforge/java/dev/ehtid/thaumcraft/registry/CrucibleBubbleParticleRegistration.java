package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated BETA26 Crucible bubble particle type; no shared particle registry edit. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CrucibleBubbleParticleRegistration {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "crucible_bubble");
    public static final SimpleParticleType TYPE = new SimpleParticleType(false);

    private CrucibleBubbleParticleRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> helper.register(ID, TYPE));
    }
}
