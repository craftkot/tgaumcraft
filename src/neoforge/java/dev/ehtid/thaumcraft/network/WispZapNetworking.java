package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.joml.Vector3f;

/**
 * Isolated BETA26 Wisp zap delivery and client-side aspect-coloured trace.
 * Does not modify the shared ThaumcraftNetworking registration class.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WispZapNetworking {
    private WispZapNetworking() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                WispZapPayload.TYPE,
                WispZapPayload.STREAM_CODEC,
                (payload, context) -> handleClient(payload, context.player())
        );
    }

    private static void handleClient(WispZapPayload payload, Player player) {
        Level level = player.level();
        if (!level.isClientSide) {
            return;
        }

        // Match the BETA26 handler's special case for the local player entity ID.
        Entity source = payload.sourceEntityId() == player.getId()
                ? player : level.getEntity(payload.sourceEntityId());
        Entity target = payload.targetEntityId() == player.getId()
                ? player : level.getEntity(payload.targetEntityId());
        if (source == null || target == null) {
            return;
        }

        int color = source instanceof WispEntity wisp ? wisp.getAspectColor() : 0xFFFFFF;
        spawnAspectBolt(level, source.position(), target.position(), color,
                source.getId(), target.getId());
    }

    /**
     * Recreates the BETA26 FXBolt's seeded, jagged source-to-target path.
     * The old CoreGLE polycone is currently represented by two aspect-coloured
     * dust traces plus sparse electric sparks; a custom additive tube renderer
     * is still required for exact mesh/blend parity.
     */
    private static void spawnAspectBolt(
            Level level, Vec3 start, Vec3 end, int rgb, int sourceId, int targetId) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        if (!Double.isFinite(distance) || distance < 1.0E-4D) {
            return;
        }

        int steps = Math.max(3, Math.min(64, (int) (distance * Math.PI)));
        RandomSource random = RandomSource.create(
                (((long) sourceId) << 32) ^ (targetId & 0xFFFFFFFFL) ^ level.getGameTime());
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        DustParticleOptions outer = new DustParticleOptions(
                new Vector3f(red, green, blue), 0.12F);
        DustParticleOptions inner = new DustParticleOptions(
                new Vector3f(red, green, blue), 0.05F);

        float phaseOffset = random.nextInt(50) * (float) Math.PI;
        for (int step = 0; step <= steps; step++) {
            Vec3 point;
            if (step == 0) {
                point = start;
            } else if (step == steps) {
                point = end;
            } else {
                float phase = step * ((float) distance * (float) Math.PI / steps) + phaseOffset;
                double x = delta.x / steps * step
                        + Math.sin(phase / 4.0F) * 0.1F
                        + (random.nextFloat() - random.nextFloat()) * 0.1F;
                double y = delta.y / steps * step
                        + Math.sin(phase / 3.0F) * 0.1F
                        + (random.nextFloat() - random.nextFloat()) * 0.1F;
                double z = delta.z / steps * step
                        + Math.sin(phase / 2.0F) * 0.1F
                        + (random.nextFloat() - random.nextFloat()) * 0.1F;
                point = start.add(x, y, z);
            }

            level.addParticle(outer, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            if ((step & 1) == 0) {
                level.addParticle(inner, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            }
            if (step % 4 == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        point.x, point.y, point.z,
                        (random.nextDouble() - 0.5D) * 0.015D,
                        (random.nextDouble() - 0.5D) * 0.015D,
                        (random.nextDouble() - 0.5D) * 0.015D);
            }
        }
    }
}
