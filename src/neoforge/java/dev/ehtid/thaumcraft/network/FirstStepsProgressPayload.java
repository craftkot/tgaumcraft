package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.FirstStepsServerProgression;
import dev.ehtid.thaumcraft.port.core.PlayerThaumcraftData;
import dev.ehtid.thaumcraft.port.core.ResearchProgressionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Narrow C2S projection of BETA26 PacketSyncProgressToServer for FIRSTSTEPS.
 * The client supplies only the legacy packet booleans; stage/requisites are recomputed from
 * authoritative server attachment state and cannot be advanced by a forged client stage.
 */
public record FirstStepsProgressPayload(boolean expectedKnown, boolean checkRequisites, boolean noFlags)
        implements CustomPacketPayload {
    public static final Type<FirstStepsProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "firststeps_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FirstStepsProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.expectedKnown); buf.writeBoolean(p.checkRequisites); buf.writeBoolean(p.noFlags); },
            buf -> new FirstStepsProgressPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FirstStepsProgressPayload payload, ServerPlayer player) {
        PlayerThaumcraftData data = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
        FirstStepsServerProgression gate = new FirstStepsServerProgression();
        ResearchProgressionService service = new ResearchProgressionService(BuiltInResearchCatalog.create());
        var result = gate.request(data.knowledge(), data.warp(), service,
                payload.expectedKnown(), payload.checkRequisites(), payload.noFlags());
        if (result.progressed() && result.requestSync()) ThaumcraftNetworking.sync(player);
        if (result.progressed() && result.xpAward() > 0) player.giveExperiencePoints(result.xpAward());
    }
}
