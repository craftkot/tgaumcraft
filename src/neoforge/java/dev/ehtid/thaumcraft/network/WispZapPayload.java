package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** BETA26 PacketFXWispZap: the two entity IDs needed to draw the client-side arc. */
public record WispZapPayload(int sourceEntityId, int targetEntityId) implements CustomPacketPayload {
    public static final Type<WispZapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "wisp_zap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WispZapPayload> STREAM_CODEC =
            StreamCodec.of(WispZapPayload::encode, WispZapPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, WispZapPayload payload) {
        buffer.writeInt(payload.sourceEntityId());
        buffer.writeInt(payload.targetEntityId());
    }

    private static WispZapPayload decode(RegistryFriendlyByteBuf buffer) {
        return new WispZapPayload(buffer.readInt(), buffer.readInt());
    }
}
