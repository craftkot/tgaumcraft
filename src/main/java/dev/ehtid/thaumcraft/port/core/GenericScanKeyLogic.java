package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/** Exact research-key construction used by BETA26 ScanGeneric. */
public final class GenericScanKeyLogic {
    private GenericScanKeyLogic() {}

    /**
     * Restore the unambiguous pre-flattening identity of 1.12 dye:4 (lapis).
     * BETA26 ScanGeneric persists the numeric item metadata in its research key.
     * Other modern aliases stay unchanged until their original metadata is verified.
     */
    public static String forModernItem(String modernRegistryId, boolean damageable, int modernMetadata) {
        if ("minecraft:lapis_lazuli".equals(modernRegistryId)) {
            return forItem("minecraft:dye", false, 4);
        }
        return forItem(modernRegistryId, damageable, modernMetadata);
    }

    public static String forEntity(String entityId) {
        return "!" + entityId;
    }

    /**
     * Non-damageable stacks append legacy metadata directly after the registry id with no separator.
     * Damageable stacks omit metadata entirely.
     */
    public static String forItem(String registryId, boolean damageable, int legacyMetadata) {
        Objects.requireNonNull(registryId, "registryId");
        String key = "!" + registryId;
        return damageable ? key : key + legacyMetadata;
    }
}
