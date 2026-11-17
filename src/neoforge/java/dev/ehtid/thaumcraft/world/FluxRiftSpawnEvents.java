package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.FluxRiftEntity;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Consumes BETA26 aura rift triggers after the level tick.
 *
 * Aura processing runs from ServerTickEvent.Post; LevelTickEvent.Post on the
 * following tick consumes its transient trigger. No shared tick dispatcher or
 * common registry is modified by this subscriber.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class FluxRiftSpawnEvents {
    private FluxRiftSpawnEvents() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.getIfPresent(level);
        if (aura != null) consumePendingTrigger(level, aura);
    }

    /** Returns true only when a pending trigger produced and paid for a Rift. */
    public static boolean consumePendingTrigger(ServerLevel level, ThaumcraftAuraSavedData aura) {
        BlockPos trigger = aura.takePendingRiftTrigger();
        return trigger != null && trySpawnRift(level, aura, trigger);
    }

    /** Package-visible for the native GameTest; this is the production spawn transaction. */
    static boolean trySpawnRift(ServerLevel level, ThaumcraftAuraSavedData aura, BlockPos trigger) {
        int triggerChunkX = trigger.getX() >> 4;
        int triggerChunkZ = trigger.getZ() >> 4;
        // Never force-load a trigger chunk after its transient request has aged.
        if (level.getChunkSource().getChunkNow(triggerChunkX, triggerChunkZ) == null) return false;

        int x = trigger.getX() + level.getRandom().nextInt(16);
        int z = trigger.getZ() + level.getRandom().nextInt(16);
        BlockPos spawnPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));

        if (!level.dimensionType().hasSkyLight()) {
            spawnPos = new BlockPos(x, 10, z);
            while (!level.isEmptyBlock(spawnPos)) {
                int nextY = spawnPos.getY() + level.getRandom().nextInt(5) + 1;
                if (nextY > level.getMaxBuildHeight() - 5) return false;
                spawnPos = new BlockPos(x, nextY, z);
            }
        }

        if (spawnPos.getY() >= level.getMaxBuildHeight() - 4) return false;
        if (!level.getEntitiesOfClass(FluxRiftEntity.class, new AABB(spawnPos).inflate(32.0D)).isEmpty()) {
            return false;
        }

        float flux = aura.getFlux(spawnPos);
        double size = Math.sqrt(flux * 3.0F);
        if (!(size > 5.0D)) return false;

        float cost = (float) size;
        if (aura.drainFlux(spawnPos, cost, true) < cost) return false;

        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.setRiftSeed(level.getRandom().nextInt());
        rift.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY() + 0.5D,
                spawnPos.getZ() + 0.5D,
                level.getRandom().nextInt(360),
                0.0F
        );
        // BETA26 only debits Flux after the entity has been accepted by the world.
        if (!level.addFreshEntity(rift)) return false;

        rift.setRiftSize((int) size);
        float drained = aura.drainFlux(spawnPos, cost, false);
        if (Float.compare(drained, cost) != 0) {
            // EntityJoinLevelEvent handlers can run during addFreshEntity. Roll
            // back both sides if one of them changed the aura before the debit.
            if (drained > 0.0F) aura.polluteFlux(spawnPos, drained);
            rift.discard();
            return false;
        }

        // The BETA26 f_toomuchflux research reward is intentionally deferred
        // until the M07 research API is integrated; do not fake an unlock here.
        return true;
    }
}
