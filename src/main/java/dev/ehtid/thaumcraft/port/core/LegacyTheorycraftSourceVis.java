package dev.ehtid.thaumcraft.port.core;

/**
 * Exact ore-dictionary object tags from Thaumcraft 6.1.BETA26
 * thaumcraft.common.config.ConfigAspects bytecode, not heuristic tags.
 *
 * The entire generated getObjectTags / crafting graph is NOT yet ported.
 * Only return a value for the legacy registry + metadata pairs whose
 * original ore-dictionary tags were verified directly in ConfigAspects.
 * - ingotBrass: METAL 10, TOOL 5 (ItemsTC.ingots Damage 2).
 * - gemAmber: TRAP 10, CRYSTAL 10 (ItemsTC.amber).
 * - quicksilver: METAL 10, DEATH 5, ALCHEMY 5 (ItemsTC.quicksilver).
 *
 * Other items must fall back to the independently ported object aspect
 * table or FAIL CLOSED; do not silently assign a guessed Vis size.
 */
public final class LegacyTheorycraftSourceVis {
    private LegacyTheorycraftSourceVis() {}

    public static int explicitlyTaggedVisSize(String registryId, int legacyMetadata) {
        if (registryId == null) return -1;
        if (registryId.equals("thaumcraft:ingot") && legacyMetadata == 2)
            return 10 + 5;
        if (registryId.equals("thaumcraft:amber") && legacyMetadata == 0)
            return 10 + 10;
        if (registryId.equals("thaumcraft:quicksilver") && legacyMetadata == 0)
            return 10 + 5 + 5;
        return -1;
    }
}
