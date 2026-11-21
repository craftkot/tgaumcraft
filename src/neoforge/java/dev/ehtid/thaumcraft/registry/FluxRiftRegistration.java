package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.FluxRiftEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Isolated registration for the first Flux Rift entity slice.
 * Kept outside ThaumcraftEntities to avoid editing the shared entity register.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class FluxRiftRegistration {
    private static EntityType<FluxRiftEntity> fluxRift;

    private FluxRiftRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!Registries.ENTITY_TYPE.equals(event.getRegistryKey())) return;

        EntityType<FluxRiftEntity> type = EntityType.Builder
                .<FluxRiftEntity>of(FluxRiftEntity::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build("thaumcraft:flux_rift");
        fluxRift = type;
        event.register(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "flux_rift"),
                () -> type
        );
    }

    public static EntityType<FluxRiftEntity> get() {
        if (fluxRift == null) {
            throw new IllegalStateException("Flux Rift EntityType was requested before entity registration");
        }
        return fluxRift;
    }
}
