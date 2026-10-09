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

    public static final String GOLEM = "f_golem";
    public static final String SPIDER = "f_SPIDER";
    public static final String BAT = "f_BAT";
    public static final String FLY = "f_FLY";
    public static final String DRAGON_BREATH = "!DRAGONBREATH";
    public static final String TOTEM_UNDYING = "!TOTEMUNDYING";
    public static final String TELEPORT = "f_TELEPORT";
    public static final String DISPENSER = "f_DISPENSER";
    public static final String MAT_CLAY = "f_MATCLAY";
    public static final String MAT_IRON = "f_MATIRON";
    public static final String MAT_BRASS = LegacyOreDictionaryScans.BRASS;
    public static final String MAT_THAUMIUM = LegacyOreDictionaryScans.THAUMIUM;
    public static final String MAT_VOID = LegacyOreDictionaryScans.VOID;
    public static final String ARROW = "f_arrow";
    public static final String FIREBALL = "f_fireball";
    public static final String SPIT = "f_spit";

    public static final List<String> REGISTRATION_ORDER = List.of(
            GOLEM, SPIDER, BAT, FLY,
            DRAGON_BREATH, TOTEM_UNDYING, TELEPORT, DISPENSER, MAT_CLAY,
            MAT_IRON, MAT_BRASS, MAT_THAUMIUM, MAT_VOID,
            ARROW, FIREBALL, SPIT);

    public static Set<String> forItem(String itemId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if ("minecraft:dragon_breath".equals(itemId)) out.add(DRAGON_BREATH);
        if ("minecraft:totem_of_undying".equals(itemId)) out.add(TOTEM_UNDYING);
        // End portal frames are item forms of a BETA26 ScanBlock registration.
        // Nether/end portal blocks have no corresponding obtainable inventory item.
        if ("minecraft:ender_pearl".equals(itemId)
                || "minecraft:end_portal_frame".equals(itemId)) out.add(TELEPORT);
        if ("minecraft:clay_ball".equals(itemId) || "minecraft:clay".equals(itemId)
                || isTerracotta(itemId)) out.add(MAT_CLAY);
        // ScanBlock also installs companion item handlers; dispenser has an explicit
        // ScanItem registration in BETA26 as well.
        if ("minecraft:dispenser".equals(itemId)) out.add(DISPENSER);
        // BETA26 ScanOreDictionary: oreIron, ingotIron, blockIron, plateIron.
        // Only the three identities present in vanilla 1.12 are translated here;
        // third-party ore-dictionary/tag matching remains a separate bridge.
        if ("minecraft:iron_ore".equals(itemId) || "minecraft:iron_ingot".equals(itemId)
                || "minecraft:iron_block".equals(itemId)) out.add(MAT_IRON);
        if ("minecraft:arrow".equals(itemId)) out.add(ARROW);
        return Set.copyOf(out);
    }

    public static Set<String> forEntity(String entityId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        // BETA26 used EntityGolem.class with subclasses enabled.
        if ("minecraft:iron_golem".equals(entityId) || "minecraft:snow_golem".equals(entityId))
            out.add(GOLEM);

        // EntitySpider.class with subclasses enabled also matched cave spiders.
        if ("minecraft:spider".equals(entityId) || "minecraft:cave_spider".equals(entityId))
            out.add(SPIDER);

        // Bat had two independent BETA26 registrations: f_BAT and f_FLY.
        if ("minecraft:bat".equals(entityId)) {
            out.add(BAT);
            out.add(FLY);
        }

        // Remaining vanilla members of the BETA26 f_FLY registrations.
        if ("minecraft:parrot".equals(entityId)
                || "minecraft:ghast".equals(entityId)
                || "minecraft:blaze".equals(entityId))
            out.add(FLY);

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
        if ("minecraft:clay".equals(blockId) || isTerracotta(blockId)) out.add(MAT_CLAY);
        if ("minecraft:iron_ore".equals(blockId) || "minecraft:iron_block".equals(blockId)) out.add(MAT_IRON);
        return Set.copyOf(out);
    }

    private static boolean isTerracotta(String id) {
        if ("minecraft:terracotta".equals(id)) return true;
        return id != null && id.startsWith("minecraft:") && id.endsWith("_terracotta")
                && !id.endsWith("_glazed_terracotta");
    }
}
