package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.FluxTaintMobEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated BETA26 Flux Taint effect registration; no shared registry aggregator edit. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FluxTaintEffectRegistration {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "flux_taint");
    private static final ResourceKey<MobEffect> KEY = ResourceKey.create(Registries.MOB_EFFECT, ID);

    private FluxTaintEffectRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.MOB_EFFECT, helper -> helper.register(ID, new FluxTaintMobEffect()));
    }

    public static Holder<MobEffect> get() {
        return BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(KEY);
    }
}
