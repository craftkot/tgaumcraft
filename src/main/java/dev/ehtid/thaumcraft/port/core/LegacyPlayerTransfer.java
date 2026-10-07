package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader-neutral player-copy and client-sync rules extracted from BETA26.
 *
 * The old capability clone path was effectively a persistence round-trip rather than a field copy.
 * Keeping that distinction matters because knowledge serialization filters non-positive rows and
 * deserialization performs post-load AUTOUNLOCK processing.
 */
public final class LegacyPlayerTransfer {
    private LegacyPlayerTransfer() {}

    public record ClientApplyResult(List<String> popupResearch, PlayerThaumcraftSnapshot appliedSnapshot) {
        public ClientApplyResult {
            popupResearch = popupResearch == null ? List.of() : List.copyOf(popupResearch);
        }
    }

    /** Copy using the same encode -> decode boundary as the legacy capability clone path. */
    public static void cloneViaLegacyPersistence(PlayerThaumcraftData source,
                                                 PlayerThaumcraftData destination,
                                                 ResearchCatalog catalog) {
        LegacyKnowledgePayload knowledge = LegacyPlayerDataCodec.encodeKnowledge(source.knowledge());
        LegacyWarpPayload warp = LegacyPlayerDataCodec.encodeWarp(source.warp());
        LegacyPlayerDataCodec.decodeKnowledge(knowledge, destination.knowledge(), catalog);
        LegacyPlayerDataCodec.decodeWarp(warp, destination.warp());
    }

    /**
     * Applies a server snapshot to the client and returns the research keys whose POPUP flag should
     * produce a toast. POPUP is consumed locally after the notification, matching the old sync path.
     */
    public static ClientApplyResult applyClientSnapshot(PlayerThaumcraftData client,
                                                        PlayerThaumcraftSnapshot snapshot,
                                                        ResearchCatalog catalog) {
        client.load(snapshot);
        List<String> popups = new ArrayList<>();
        for (String key : List.copyOf(client.knowledge().getResearchList())) {
            if (key == null || !client.knowledge().hasResearchFlag(key, ResearchFlag.POPUP)) continue;
            if (catalog != null && catalog.get(key) != null) popups.add(key);
            client.knowledge().clearResearchFlag(key, ResearchFlag.POPUP);
        }
        return new ClientApplyResult(popups, client.snapshot());
    }
}
