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
        // BETA26 used a single stained_hardened_clay item with color metadata.
        // Modern terracotta has separate item ids; preserve its original scan key.
        if ("minecraft:terracotta".equals(modernRegistryId)) {
            return forItem("minecraft:hardened_clay", false, 0);
        }
        if (modernRegistryId.startsWith("minecraft:")
                && modernRegistryId.endsWith("_terracotta")
                && !modernRegistryId.endsWith("_glazed_terracotta")) {
            String color = modernRegistryId.substring("minecraft:".length(),
                    modernRegistryId.length() - "_terracotta".length());
            int oldColor = legacyDyeColorMetadata(color);
            if (oldColor >= 0) {
                return forItem("minecraft:stained_hardened_clay", false, oldColor);
            }
        }
        // Additional BETA26 metadata-bearing block item families flattened in 1.13.
        // Match the final suffix, preserving multiword colors such as light_blue.
        String[] oldBlockFamilies = {
            "_stained_glass_pane", "stained_glass_pane",
            "_stained_glass", "stained_glass",
            "_concrete_powder", "concrete_powder",
            "_concrete", "concrete",
            "_wool", "wool",
            "_carpet", "carpet"
        };
        for (int i = 0; i < oldBlockFamilies.length; i += 2) {
            String suffix = oldBlockFamilies[i];
            if (modernRegistryId.startsWith("minecraft:") && modernRegistryId.endsWith(suffix)) {
                String color = modernRegistryId.substring("minecraft:".length(),
                        modernRegistryId.length() - suffix.length());
                int oldColor = legacyDyeColorMetadata(color);
                if (oldColor >= 0) return forItem("minecraft:" + oldBlockFamilies[i + 1], false, oldColor);
            }
        }
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

    /** Classic Minecraft 1.12 block color order, distinct from dye item damage order. */
    private static int legacyDyeColorMetadata(String color) {
        return switch (color) {
            case "white" -> 0;
            case "orange" -> 1;
            case "magenta" -> 2;
            case "light_blue" -> 3;
            case "yellow" -> 4;
            case "lime" -> 5;
            case "pink" -> 6;
            case "gray" -> 7;
            case "light_gray" -> 8;
            case "cyan" -> 9;
            case "purple" -> 10;
            case "blue" -> 11;
            case "brown" -> 12;
            case "green" -> 13;
            case "red" -> 14;
            case "black" -> 15;
            default -> -1;
        };
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
