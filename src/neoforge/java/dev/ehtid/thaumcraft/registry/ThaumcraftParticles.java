package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ThaumcraftParticles {
    private ThaumcraftParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, ThaumcraftPort.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SCAN_RUNE =
            PARTICLES.register("scan_rune", () -> new SimpleParticleType(false));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
