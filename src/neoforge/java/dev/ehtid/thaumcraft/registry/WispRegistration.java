package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated Wisp registration; does not modify the shared ThaumcraftEntities registry. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class WispRegistration {
    private static EntityType<WispEntity> wisp;

    private WispRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!Registries.ENTITY_TYPE.equals(event.getRegistryKey())) {
            return;
        }

        EntityType<WispEntity> type = EntityType.Builder
                .<WispEntity>of(WispEntity::new, MobCategory.MONSTER)
                .sized(0.9F, 0.9F)
                .clientTrackingRange(64)
                .updateInterval(3)
                .build("thaumcraft:wisp");
        wisp = type;
        event.register(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "wisp"),
                () -> type
        );
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(get(), WispEntity.createAttributes().build());
    }

    public static EntityType<WispEntity> get() {
        if (wisp == null) {
            throw new IllegalStateException("Wisp EntityType was requested before entity registration");
        }
        return wisp;
    }
}
