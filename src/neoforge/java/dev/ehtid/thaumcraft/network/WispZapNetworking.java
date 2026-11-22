package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Isolated BETA26 PacketFXWispZap delivery; client rendering is kept on Dist.CLIENT. */
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

        // Keep client-only renderer classes out of dedicated-server class loading.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.ehtid.thaumcraft.client.WispZapClientEffects.accept(level, source, target);
        }
    }
}
