package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.AlumentumProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Server/client registry for the original Alumentum throwable Entity. */
public final class ThaumcraftEntities {
    private ThaumcraftEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ThaumcraftPort.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AlumentumProjectile>> ALUMENTUM =
            ENTITIES.register("alumentum", () -> EntityType.Builder
                    .<AlumentumProjectile>of(AlumentumProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thaumcraft:alumentum"));

    public static void register(IEventBus modBus) { ENTITIES.register(modBus); }
}
