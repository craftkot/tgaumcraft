package dev.ehtid.thaumcraft.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyKnowledgePayload;
import dev.ehtid.thaumcraft.port.core.LegacyPlayerDataCodec;
import dev.ehtid.thaumcraft.port.core.LegacyWarpPayload;
import dev.ehtid.thaumcraft.port.core.PlayerThaumcraftData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Persistent player Thaumcraft state. Persistence deliberately passes through the BETA26 row format. */
public final class ThaumcraftPlayerAttachments {
    private ThaumcraftPlayerAttachments() {}

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ThaumcraftPort.MOD_ID);

    private record ResearchRow(String key, int stage, boolean hasStage, String flags, boolean hasFlags) {
        private static final Codec<ResearchRow> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(ResearchRow::key),
                Codec.INT.fieldOf("stage").forGetter(ResearchRow::stage),
                Codec.BOOL.fieldOf("has_stage").forGetter(ResearchRow::hasStage),
                Codec.STRING.fieldOf("flags").forGetter(ResearchRow::flags),
                Codec.BOOL.fieldOf("has_flags").forGetter(ResearchRow::hasFlags)
        ).apply(i, ResearchRow::new));
    }

    private record KnowledgeRow(String key, int amount) {
        private static final Codec<KnowledgeRow> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(KnowledgeRow::key),
                Codec.INT.fieldOf("amount").forGetter(KnowledgeRow::amount)
        ).apply(i, KnowledgeRow::new));
    }

    private record Persisted(List<ResearchRow> research, List<KnowledgeRow> knowledge,
                             List<Integer> warp, int warpCounter) {
        private static final Codec<Persisted> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResearchRow.CODEC.listOf().fieldOf("research").forGetter(Persisted::research),
                KnowledgeRow.CODEC.listOf().fieldOf("knowledge").forGetter(Persisted::knowledge),
                Codec.INT.listOf().fieldOf("warp").forGetter(Persisted::warp),
                Codec.INT.fieldOf("warp_counter").forGetter(Persisted::warpCounter)
        ).apply(i, Persisted::new));
    }

    private static final Codec<PlayerThaumcraftData> DATA_CODEC = Persisted.CODEC.xmap(
            ThaumcraftPlayerAttachments::decode,
            ThaumcraftPlayerAttachments::encode);

    public static final Supplier<AttachmentType<PlayerThaumcraftData>> PLAYER_DATA = TYPES.register(
            "player_data", () -> AttachmentType.builder(PlayerThaumcraftData::new).serialize(DATA_CODEC).build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    private static Persisted encode(PlayerThaumcraftData data) {
        LegacyKnowledgePayload k = LegacyPlayerDataCodec.encodeKnowledge(data.knowledge());
        List<ResearchRow> research = new ArrayList<>(k.research().size());
        for (LegacyKnowledgePayload.ResearchRow row : k.research()) {
            // A null research key is an invalid save in the old implementation as well; fail instead of inventing a mapping.
            if (row.key() == null) throw new IllegalStateException("BETA26 null research key cannot be persisted");
            research.add(new ResearchRow(row.key(), row.stage() == null ? 0 : row.stage(), row.stage() != null,
                    row.flags() == null ? "" : row.flags(), row.flags() != null));
        }
        List<KnowledgeRow> knowledge = k.knowledge().stream().map(r -> new KnowledgeRow(r.key(), r.amount())).toList();
        LegacyWarpPayload w = LegacyPlayerDataCodec.encodeWarp(data.warp());
        return new Persisted(List.copyOf(research), knowledge, w.warp(), w.counter());
    }

    private static PlayerThaumcraftData decode(Persisted persisted) {
        List<LegacyKnowledgePayload.ResearchRow> research = persisted.research().stream()
                .map(r -> new LegacyKnowledgePayload.ResearchRow(r.key(), r.hasStage() ? r.stage() : null,
                        r.hasFlags() ? r.flags() : null))
                .toList();
        List<LegacyKnowledgePayload.KnowledgeRow> knowledge = persisted.knowledge().stream()
                .map(r -> new LegacyKnowledgePayload.KnowledgeRow(r.key(), r.amount())).toList();
        PlayerThaumcraftData out = new PlayerThaumcraftData();
        LegacyPlayerDataCodec.decodeKnowledge(new LegacyKnowledgePayload(research, knowledge), out.knowledge(),
                BuiltInResearchCatalog.create());
        LegacyPlayerDataCodec.decodeWarp(new LegacyWarpPayload(persisted.warp(), persisted.warpCounter()), out.warp());
        return out;
    }
}
