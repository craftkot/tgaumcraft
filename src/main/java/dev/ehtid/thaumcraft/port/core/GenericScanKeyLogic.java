package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/** Exact research-key construction used by BETA26 ScanGeneric. */
public final class GenericScanKeyLogic {
    private GenericScanKeyLogic() {}

    /**
     * BETA26 1.12 item identity for the dye damage-value family. Flattening in
     * 1.13 split these sixteen variants into independent registry entries.
     * Restore the former minecraft:dye metadata only for those exact variants;
     * modern item families without a proven predecessor retain their identity.
     */
    public static String forModernItem(String modernRegistryId, boolean damageable, int modernMetadata) {
        Objects.requireNonNull(modernRegistryId, "modernRegistryId");
        int oldDye = switch (modernRegistryId) {
            case "minecraft:ink_sac", "minecraft:black_dye" -> 0;
            case "minecraft:red_dye" -> 1;
            case "minecraft:green_dye" -> 2;
            case "minecraft:cocoa_beans", "minecraft:brown_dye" -> 3;
            case "minecraft:lapis_lazuli", "minecraft:blue_dye" -> 4;
            case "minecraft:purple_dye" -> 5;
            case "minecraft:cyan_dye" -> 6;
            case "minecraft:light_gray_dye" -> 7;
            case "minecraft:gray_dye" -> 8;
            case "minecraft:pink_dye" -> 9;
            case "minecraft:lime_dye" -> 10;
            case "minecraft:yellow_dye" -> 11;
            case "minecraft:light_blue_dye" -> 12;
            case "minecraft:magenta_dye" -> 13;
            case "minecraft:orange_dye" -> 14;
            case "minecraft:bone_meal", "minecraft:white_dye" -> 15;
            default -> -1;
        };
        return oldDye >= 0
                ? forItem("minecraft:dye", false, oldDye)
                : forItem(modernRegistryId, damageable, modernMetadata);
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
