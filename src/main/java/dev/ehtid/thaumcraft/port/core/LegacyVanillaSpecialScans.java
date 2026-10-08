package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact vanilla-facing ScanItem/ScanBlock/ScanEntity registrations from
 * BETA26 ConfigResearch.initScannables for the currently mapped 1.21.1 targets.
 *
 * The legacy registry names for these vanilla objects survived flattening
 * except hardened/stained hardened clay, whose modern equivalents are
 * terracotta and the sixteen colored terracotta blocks.
 */
public final class LegacyVanillaSpecialScans {
    private LegacyVanillaSpecialScans() {}

    public static final String DRAGON_BREATH = "!DRAGONBREATH";
    public static final String TOTEM_UNDYING = "!TOTEMUNDYING";
    public static final String TELEPORT = "f_TELEPORT";
    public static final String DISPENSER = "f_DISPENSER";
    public static final String MAT_CLAY = "f_MATCLAY";

    public static final List<String> REGISTRATION_ORDER = List.of(
            DRAGON_BREATH, TOTEM_UNDYING, TELEPORT, DISPENSER, MAT_CLAY);

    public static Set<String> forItem(String itemId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if ("minecraft:dragon_breath".equals(itemId)) out.add(DRAGON_BREATH);
        if ("minecraft:totem_of_undying".equals(itemId)) out.add(TOTEM_UNDYING);
        if ("minecraft:ender_pearl".equals(itemId)) out.add(TELEPORT);
        if ("minecraft:clay_ball".equals(itemId)) out.add(MAT_CLAY);
        return Set.copyOf(out);
    }

    public static Set<String> forEntity(String entityId) {
        return "minecraft:enderman".equals(entityId) ? Set.of(TELEPORT) : Set.of();
    }

    public static Set<String> forBlock(String blockId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if ("minecraft:nether_portal".equals(blockId)
                || "minecraft:end_portal".equals(blockId)
                || "minecraft:end_portal_frame".equals(blockId)) out.add(TELEPORT);
        if ("minecraft:dispenser".equals(blockId)) out.add(DISPENSER);
        if (isTerracotta(blockId)) out.add(MAT_CLAY);
        return Set.copyOf(out);
    }

    private static boolean isTerracotta(String id) {
        if ("minecraft:terracotta".equals(id)) return true;
        return id != null && id.startsWith("minecraft:") && id.endsWith("_terracotta")
                && !id.endsWith("_glazed_terracotta");
    }
}
