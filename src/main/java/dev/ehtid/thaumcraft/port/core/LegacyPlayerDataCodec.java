package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact loader-neutral projection of PlayerKnowledge.DefaultImpl and PlayerWarp.DefaultImpl NBT behavior.
 * The NeoForge integration layer can translate these rows to CompoundTag/ListTag without re-implementing
 * legacy semantics a second time.
 */
public final class LegacyPlayerDataCodec {
    private LegacyPlayerDataCodec() {}

    public static LegacyKnowledgePayload encodeKnowledge(PlayerKnowledgeData data) {
        Map<String, Integer> stages = data.copyStages();
        Map<String, Set<ResearchFlag>> flags = data.copyFlags();
        List<LegacyKnowledgePayload.ResearchRow> researchRows = new ArrayList<>();

        for (String key : data.getResearchList()) {
            Integer stage = stages.containsKey(key) ? stages.get(key) : null;
            String flagsCsv = null;
            if (flags.containsKey(key)) {
                Set<ResearchFlag> values = flags.get(key);
                StringBuilder csv = new StringBuilder();
                if (values != null) {
                    // HashSet order in BETA26 was unspecified. Enum declaration order gives deterministic modern saves
                    // without changing the decoded state.
                    for (ResearchFlag value : ResearchFlag.values()) {
                        if (!values.contains(value)) continue;
                        if (!csv.isEmpty()) csv.append(',');
                        csv.append(value.name());
                    }
                }
                flagsCsv = csv.toString();
            }
            researchRows.add(new LegacyKnowledgePayload.ResearchRow(key, stage, flagsCsv));
        }

        List<LegacyKnowledgePayload.KnowledgeRow> knowledgeRows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : data.copyKnowledgeRaw().entrySet()) {
            Integer amount = e.getValue();
            String key = e.getKey();
            // Exact BETA26 serialization filter.
            if (amount != null && amount > 0 && key != null && !key.isEmpty()) {
                knowledgeRows.add(new LegacyKnowledgePayload.KnowledgeRow(key, amount));
            }
        }
        return new LegacyKnowledgePayload(researchRows, knowledgeRows);
    }

    public static void decodeKnowledge(LegacyKnowledgePayload payload, PlayerKnowledgeData out, ResearchCatalog catalog) {
        if (payload == null) return; // BETA26 deserializeNBT(null) returns without clearing.

        Set<String> research = new HashSet<>();
        Map<String, Integer> stages = new HashMap<>();
        Map<String, Set<ResearchFlag>> flags = new HashMap<>();
        Map<String, Integer> rawKnowledge = new HashMap<>();

        for (LegacyKnowledgePayload.ResearchRow row : payload.research()) {
            if (row == null) continue;
            String key = row.key();
            // NBTTagCompound#getString never returns null in 1.12; malformed/missing key becomes "".
            if (key == null) key = "";
            if (isResearchKnown(research, stages, key)) continue;
            research.add(key);

            int stage = row.stage() == null ? 0 : row.stage();
            if (stage > 0) stages.put(key, stage);

            String csv = row.flags();
            if (csv != null && !csv.isEmpty()) {
                EnumSet<ResearchFlag> set = EnumSet.noneOf(ResearchFlag.class);
                for (String token : csv.split(",")) {
                    try {
                        set.add(ResearchFlag.valueOf(token));
                    } catch (Exception ignored) {
                        // BETA26 ignores unknown enum names while loading old/corrupt data.
                    }
                }
                if (!set.isEmpty()) flags.put(key, set);
            }
        }

        for (LegacyKnowledgePayload.KnowledgeRow row : payload.knowledge()) {
            if (row == null) continue;
            String key = row.key() == null ? "" : row.key();
            // Exact BETA26 deserializer: no validation and later duplicates overwrite earlier values.
            rawKnowledge.put(key, row.amount());
        }

        out.loadSnapshot(research, stages, flags, rawKnowledge);
        if (catalog != null) catalog.addAutoUnlockResearch(out);
    }

    public static LegacyWarpPayload encodeWarp(PlayerWarpData data) {
        int[] values = data.copyLegacyWarpArray();
        List<Integer> warp = new ArrayList<>(values.length);
        for (int value : values) warp.add(value);
        return new LegacyWarpPayload(warp, data.getCounter());
    }

    public static void decodeWarp(LegacyWarpPayload payload, PlayerWarpData out) {
        if (payload == null) return; // BETA26 null NBT leaves existing state untouched.
        out.clear();
        int[] values = new int[WarpType.values().length];
        int count = Math.min(values.length, payload.warp().size());
        for (int i = 0; i < count; i++) values[i] = payload.warp().get(i);
        // Legacy deserialize writes the raw int array directly without 0..500 clamping.
        out.loadLegacyWarpArray(values);
        out.setCounter(payload.counter());
    }

    private static boolean isResearchKnown(Set<String> research, Map<String, Integer> stages, String key) {
        if (key == null) return false;
        if (key.isEmpty()) return true;
        String[] split = key.split("@", 2);
        if (split.length > 1) {
            int required = parseIntOrZero(split[1]);
            int stage = research.contains(split[0]) ? stages.getOrDefault(split[0], 0) : -1;
            if (stage < required) return false;
        }
        return research.contains(split[0]);
    }

    private static int parseIntOrZero(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }
}
