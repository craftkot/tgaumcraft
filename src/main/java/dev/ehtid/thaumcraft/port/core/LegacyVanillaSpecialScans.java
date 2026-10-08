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
    public static final String ARROW = "f_arrow";
    public static final String FIREBALL = "f_fireball";
    public static final String SPIT = "f_spit";

    public static final List<String> REGISTRATION_ORDER = List.of(
            DRAGON_BREATH, TOTEM_UNDYING, TELEPORT, DISPENSER, MAT_CLAY,
            ARROW, FIREBALL, SPIT);

    public static Set<String> forItem(String itemId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if ("minecraft:dragon_breath".equals(itemId)) out.add(DRAGON_BREATH);
        if ("minecraft:totem_of_undying".equals(itemId)) out.add(TOTEM_UNDYING);
        if ("minecraft:ender_pearl".equals(itemId)) out.add(TELEPORT);
        if ("minecraft:clay_ball".equals(itemId)) out.add(MAT_CLAY);
        if ("minecraft:arrow".equals(itemId)) out.add(ARROW);
        return Set.copyOf(out);
    }

    public static Set<String> forEntity(String entityId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if ("minecraft:enderman".equals(entityId)) out.add(TELEPORT);

        // BETA26 ScanEntity(..., EntityArrow.class, true): vanilla 1.12 arrow subclasses
        // represented by normal and spectral arrows in the modern registry.
        if ("minecraft:arrow".equals(entityId) || "minecraft:spectral_arrow".equals(entityId))
            out.add(ARROW);

        // BETA26 ScanEntity(..., EntityFireball.class, true) included every vanilla
        // 1.12 subclass: large/small/dragon fireballs and wither skulls.
        if ("minecraft:fireball".equals(entityId)
                || "minecraft:small_fireball".equals(entityId)
                || "minecraft:dragon_fireball".equals(entityId)
                || "minecraft:wither_skull".equals(entityId))
            out.add(FIREBALL);

        if ("minecraft:llama_spit".equals(entityId)) out.add(SPIT);
        return Set.copyOf(out);
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
