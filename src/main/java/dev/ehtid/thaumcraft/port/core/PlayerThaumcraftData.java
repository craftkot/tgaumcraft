package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregate player state that will back the NeoForge AttachmentType. */
public final class PlayerThaumcraftData {
    private final PlayerKnowledgeData knowledge = new PlayerKnowledgeData();
    private final PlayerWarpData warp = new PlayerWarpData();

    public PlayerKnowledgeData knowledge() { return knowledge; }
    public PlayerWarpData warp() { return warp; }

    public PlayerThaumcraftSnapshot snapshot() {
        Map<String, Set<ResearchFlag>> copiedFlags = new LinkedHashMap<>();
        for (Map.Entry<String, Set<ResearchFlag>> e : knowledge.copyFlags().entrySet()) {
            copiedFlags.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        int[] rawWarp = warp.copyLegacyWarpArray();
        List<Integer> warpList = new ArrayList<>(rawWarp.length);
        for (int v : rawWarp) warpList.add(v);
        return new PlayerThaumcraftSnapshot(
                new LinkedHashSet<>(knowledge.getResearchList()),
                new LinkedHashMap<>(knowledge.copyStages()),
                copiedFlags,
                new LinkedHashMap<>(knowledge.copyKnowledgeRaw()),
                List.copyOf(warpList),
                warp.getCounter());
    }

    public void load(PlayerThaumcraftSnapshot snapshot) {
        knowledge.loadSnapshot(snapshot.research(), snapshot.stages(), snapshot.flags(), snapshot.knowledgeRaw());
        int[] rawWarp = new int[WarpType.values().length];
        for (int i = 0; i < rawWarp.length && i < snapshot.warp().size(); i++) rawWarp[i] = snapshot.warp().get(i);
        warp.loadLegacyWarpArray(rawWarp);
        warp.setCounter(snapshot.warpCounter());
    }
}
