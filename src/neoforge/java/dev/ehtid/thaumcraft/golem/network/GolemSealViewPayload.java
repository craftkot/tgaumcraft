package dev.ehtid.thaumcraft.golem.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** One bounded entry per packet; reset+entries on chunk transmission, ordered deltas thereafter. */
public record GolemSealViewPayload(ResourceLocation dimension, Action action, GolemSeals.SealKey key,
                                   GolemSealView view) implements CustomPacketPayload {
    public enum Action { UPSERT, REMOVE, CLEAR_CHUNK }
    public static final Type<GolemSealViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID,"golem_seal_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GolemSealViewPayload> STREAM_CODEC = StreamCodec.of(GolemSealViewPayload::encode,GolemSealViewPayload::decode);
    public GolemSealViewPayload {
        Objects.requireNonNull(dimension); Objects.requireNonNull(action); Objects.requireNonNull(key);
        if (action == Action.UPSERT ? view == null || !view.key().equals(key) : view != null)
            throw new IllegalArgumentException("Mismatched display operation");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void encode(RegistryFriendlyByteBuf b,GolemSealViewPayload p) {
        b.writeResourceLocation(p.dimension); b.writeEnum(p.action); b.writeBlockPos(p.key.pos()); b.writeEnum(p.key.face());
        if (p.view == null) return;
        var v=p.view;
        b.writeEnum(v.type()); b.writeBlockPos(v.area()); b.writeByte(v.color()); b.writeByte(v.priority());
        b.writeUUID(v.owner()); b.writeUUID(v.instance()); b.writeBoolean(v.locked()); b.writeBoolean(v.redstone());
    }
    private static GolemSealViewPayload decode(RegistryFriendlyByteBuf b) {
        var dim=b.readResourceLocation(); var action=b.readEnum(Action.class);
        var key=new GolemSeals.SealKey(b.readBlockPos(),b.readEnum(Direction.class));
        try {
            var view=action==Action.UPSERT ? new GolemSealView(key,b.readEnum(GolemSeals.Type.class),b.readBlockPos(),
                    b.readUnsignedByte(),b.readByte(),b.readUUID(),b.readUUID(),b.readBoolean(),b.readBoolean()) : null;
            return new GolemSealViewPayload(dim,action,key,view);
        } catch (IllegalArgumentException ex) { throw new DecoderException("Invalid seal display packet",ex); }
    }
}
