package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.InfectiousVisExhaustMobEffect;
import dev.ehtid.thaumcraft.entity.VisExhaustMobEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated BETA26 Flux Phage / Flux Flu registration; no shared registry aggregator edit. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FluxInfectionEffectRegistration {
    public static final ResourceLocation FLUX_PHAGE_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "flux_phage");
    public static final ResourceLocation FLUX_FLU_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "flux_flu");
    private static final ResourceKey<MobEffect> FLUX_PHAGE_KEY =
            ResourceKey.create(Registries.MOB_EFFECT, FLUX_PHAGE_ID);
    private static final ResourceKey<MobEffect> FLUX_FLU_KEY =
            ResourceKey.create(Registries.MOB_EFFECT, FLUX_FLU_ID);

    private FluxInfectionEffectRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.MOB_EFFECT, helper -> {
            helper.register(FLUX_PHAGE_ID, new InfectiousVisExhaustMobEffect());
            helper.register(FLUX_FLU_ID, new VisExhaustMobEffect());
        });
    }

    public static Holder<MobEffect> infectious() {
        return BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(FLUX_PHAGE_KEY);
    }

    public static Holder<MobEffect> visExhaust() {
        return BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(FLUX_FLU_KEY);
    }
}
