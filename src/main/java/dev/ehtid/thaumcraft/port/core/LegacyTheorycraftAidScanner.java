package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BETA26 TileResearchTable.checkSurroundingAids scan geometry and matching
 * dispatch. Actual Minecraft Block / pick-stack / entity identity must be
 * supplied by the NeoForge adapter; never manufacture missing Thaumcraft blocks.
 */
public final class LegacyTheorycraftAidScanner {
    private LegacyTheorycraftAidScanner() {}

    public enum Kind { BLOCK, PICKED_STACK, ENTITY_CLASS }

    public static Kind kind(String key) {
        if (LegacyTheorycraftAidCards.forAid(key) == null) return null;
        if (key.endsWith("AidPortal$AidPortalCrimson")) return Kind.ENTITY_CLASS;
        if (key.endsWith("AidGlyphedStone")
                || key.endsWith("AidEnchantmentTable") || key.endsWith("AidBeacon"))
            return Kind.PICKED_STACK;
        return Kind.BLOCK;
    }

    public interface Probe {
        /** Block equality or Block.getPickBlock().sameItemSameComponents. */
        boolean matches(String aidKey, Kind kind, int relativeX, int relativeY, int relativeZ);
        /** Exact entity class check, in original EntityUtils radius 5.0. */
        boolean hasEntity(String aidKey, double radius);
    }

    /**
     * Original nested loops: y=-1..1, x=-4..4, z=-4..4, and every aid in
     * TheorycraftManager.aids HashMap keySet order. Entity aid check follows.
     * A single hit records an aid ONCE. Return original HashMap keySet view
     * instead of Set.copyOf, which would alter iteration semantics.
     */
    public static Set<String> scan(Probe probe) {
        Objects.requireNonNull(probe);
        Map<String, Boolean> found = new HashMap<>();
        for (int y = -1; y <= 1; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (String aid : LegacyTheorycraftManifest.aidsInHashMapIterationOrder()) {
                        Kind kind = kind(aid);
                        if (kind != Kind.ENTITY_CLASS && probe.matches(aid, kind, x, y, z))
                            found.put(aid, Boolean.TRUE);
                    }
                }
            }
        }
        for (String aid : LegacyTheorycraftManifest.aidsInHashMapIterationOrder()) {
            if (kind(aid) == Kind.ENTITY_CLASS && probe.hasEntity(aid, 5.0D))
                found.put(aid, Boolean.TRUE);
        }
        return found.keySet();
    }
}
