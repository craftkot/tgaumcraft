package dev.ehtid.thaumcraft.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.port.core.PlayerThaumcraftSnapshot;
import dev.ehtid.thaumcraft.port.core.ResearchFlag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Full authoritative server -> client player Thaumcraft state. */
public record PlayerDataPayload(PlayerThaumcraftSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<PlayerDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "player_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDataPayload> STREAM_CODEC = StreamCodec.of(
            PlayerDataPayload::encode, PlayerDataPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, PlayerDataPayload payload) {
        PlayerThaumcraftSnapshot s = payload.snapshot();
        buf.writeVarInt(s.research().size());
        for (String key : s.research()) buf.writeUtf(key == null ? "" : key);

        buf.writeVarInt(s.stages().size());
        s.stages().forEach((key, value) -> { buf.writeUtf(key); buf.writeVarInt(value); });

        buf.writeVarInt(s.flags().size());
        s.flags().forEach((key, values) -> {
            buf.writeUtf(key);
            int mask = 0;
            for (ResearchFlag flag : values) mask |= 1 << flag.ordinal();
            buf.writeVarInt(mask);
        });

        buf.writeVarInt(s.knowledgeRaw().size());
        s.knowledgeRaw().forEach((key, value) -> { buf.writeUtf(key); buf.writeInt(value); });

        buf.writeVarInt(s.warp().size());
        for (int value : s.warp()) buf.writeInt(value);
        buf.writeInt(s.warpCounter());
    }

    private static PlayerDataPayload decode(RegistryFriendlyByteBuf buf) {
        Set<String> research = new LinkedHashSet<>();
        for (int i = 0, n = buf.readVarInt(); i < n; i++) research.add(buf.readUtf());

        Map<String, Integer> stages = new LinkedHashMap<>();
        for (int i = 0, n = buf.readVarInt(); i < n; i++) stages.put(buf.readUtf(), buf.readVarInt());

        Map<String, Set<ResearchFlag>> flags = new LinkedHashMap<>();
        for (int i = 0, n = buf.readVarInt(); i < n; i++) {
            String key = buf.readUtf();
            int mask = buf.readVarInt();
            EnumSet<ResearchFlag> values = EnumSet.noneOf(ResearchFlag.class);
            for (ResearchFlag flag : ResearchFlag.values()) if ((mask & (1 << flag.ordinal())) != 0) values.add(flag);
            flags.put(key, values);
        }

        Map<String, Integer> knowledge = new LinkedHashMap<>();
        for (int i = 0, n = buf.readVarInt(); i < n; i++) knowledge.put(buf.readUtf(), buf.readInt());

        List<Integer> warp = new ArrayList<>();
        for (int i = 0, n = buf.readVarInt(); i < n; i++) warp.add(buf.readInt());
        return new PlayerDataPayload(new PlayerThaumcraftSnapshot(research, stages, flags, knowledge, warp, buf.readInt()));
    }
}
