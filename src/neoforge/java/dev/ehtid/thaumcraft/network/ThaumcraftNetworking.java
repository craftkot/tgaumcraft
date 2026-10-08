package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyPlayerTransfer;
import dev.ehtid.thaumcraft.port.core.PlayerThaumcraftData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ThaumcraftNetworking {
    private ThaumcraftNetworking() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(PlayerDataPayload.TYPE, PlayerDataPayload.STREAM_CODEC,
                (payload, context) -> {
                    PlayerThaumcraftData data = context.player().getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
                    LegacyPlayerTransfer.applyClientSnapshot(data, payload.snapshot(), BuiltInResearchCatalog.create());
                });
        registrar.playToClient(AuraDataPayload.TYPE, AuraDataPayload.STREAM_CODEC,
                (payload, context) -> ClientAuraState.set(payload.base(), payload.vis(), payload.flux()));
        registrar.playToServer(FirstStepsProgressPayload.TYPE, FirstStepsProgressPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.enqueueWork(() -> FirstStepsProgressPayload.handle(payload, player));
                });
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new PlayerDataPayload(player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).snapshot()));
    }

    public static void sendAura(ServerPlayer player, dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData.AuraChunk aura) {
        PacketDistributor.sendToPlayer(player, new AuraDataPayload(aura.base(), aura.vis(), aura.flux()));
    }
}
