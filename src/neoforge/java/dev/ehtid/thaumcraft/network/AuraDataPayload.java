package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Exact payload fields of BETA26 PacketAuraToClient: short base, float vis, float flux. */
public record AuraDataPayload(short base, float vis, float flux) implements CustomPacketPayload {
    public static final Type<AuraDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "aura_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuraDataPayload> STREAM_CODEC =
            StreamCodec.of(AuraDataPayload::encode, AuraDataPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, AuraDataPayload payload) {
        buf.writeShort(payload.base());
        buf.writeFloat(payload.vis());
        buf.writeFloat(payload.flux());
    }

    private static AuraDataPayload decode(RegistryFriendlyByteBuf buf) {
        return new AuraDataPayload(buf.readShort(), buf.readFloat(), buf.readFloat());
    }
}
