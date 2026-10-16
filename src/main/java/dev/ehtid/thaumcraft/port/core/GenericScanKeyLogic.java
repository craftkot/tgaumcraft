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
        // 1.12 planks and saplings share six wood-variant metadata values.
        // Keep the exact legacy block-item registry name rather than creating
        // separate research keys for 1.13+ flattened variants.
        String[] oldWood = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int metadata = 0; metadata < oldWood.length; metadata++) {
            if (("minecraft:" + oldWood[metadata] + "_planks").equals(modernRegistryId)) {
                return forItem("minecraft:planks", false, metadata);
            }
            if (("minecraft:" + oldWood[metadata] + "_sapling").equals(modernRegistryId)) {
                return forItem("minecraft:sapling", false, metadata);
            }
        }
        // 1.12 split logs/leaves into two registry families (four + two wood
        // species). Inventory item metadata identifies species, not log axis or
        // leaf decay flags. These are scan ITEM keys, not block-state keys.
        for (int metadata = 0; metadata < oldWood.length; metadata++) {
            if (("minecraft:" + oldWood[metadata] + "_log").equals(modernRegistryId)) {
                return forItem(metadata < 4 ? "minecraft:log" : "minecraft:log2",
                        false, metadata < 4 ? metadata : metadata - 4);
            }
            if (("minecraft:" + oldWood[metadata] + "_leaves").equals(modernRegistryId)) {
                return forItem(metadata < 4 ? "minecraft:leaves" : "minecraft:leaves2",
                        false, metadata < 4 ? metadata : metadata - 4);
            }
        }
        // Pre-flattening block item variants; values are the exact Minecraft 1.12
        // item damage metadata, not state orientation or a 1.21 block property.
        // Do not map newly introduced polished/deepslate variants to old blocks.
        String oldVariant = switch (modernRegistryId) {
            // 1.12 grass was the grass-covered dirt block; tallgrass was a separate
            // block/item with subtype metadata for shrub, grass, and fern.
            case "minecraft:grass_block" -> "grass|0";
            case "minecraft:short_grass" -> "tallgrass|1";
            case "minecraft:fern" -> "tallgrass|2";
            case "minecraft:granite" -> "stone|1";
            case "minecraft:polished_granite" -> "stone|2";
            case "minecraft:diorite" -> "stone|3";
            case "minecraft:polished_diorite" -> "stone|4";
            case "minecraft:andesite" -> "stone|5";
            case "minecraft:polished_andesite" -> "stone|6";
            case "minecraft:coarse_dirt" -> "dirt|1";
            case "minecraft:podzol" -> "dirt|2";
            case "minecraft:red_sand" -> "sand|1";
            case "minecraft:prismarine_bricks" -> "prismarine|1";
            case "minecraft:dark_prismarine" -> "prismarine|2";
            case "minecraft:chiseled_quartz_block" -> "quartz_block|1";
            case "minecraft:quartz_pillar" -> "quartz_block|2";
            case "minecraft:chiseled_sandstone" -> "sandstone|1";
            case "minecraft:cut_sandstone" -> "sandstone|2";
            case "minecraft:chiseled_red_sandstone" -> "red_sandstone|1";
            case "minecraft:cut_red_sandstone" -> "red_sandstone|2";
            default -> null;
        };
        if (oldVariant != null) {
            int separator = oldVariant.indexOf('|');
            return forItem("minecraft:" + oldVariant.substring(0, separator),
                    false, Integer.parseInt(oldVariant.substring(separator + 1)));
        }
        // 1.13 item flattening removed the damage-value families below.
        // Preserve the 1.12 ScanGeneric discovery identity so a player does
        // not receive another research key for a renamed variant.
        String legacyMetaVariant = switch (modernRegistryId) {
            // ItemFishFood: fish 0..3, cooked_fish 0..1.
            case "minecraft:cod" -> "fish|0";
            case "minecraft:salmon" -> "fish|1";
            case "minecraft:tropical_fish" -> "fish|2"; // old clownfish
            case "minecraft:pufferfish" -> "fish|3";
            case "minecraft:cooked_cod" -> "cooked_fish|0";
            case "minecraft:cooked_salmon" -> "cooked_fish|1";
            // Vanilla 1.12 red_flower metas 0..8 and yellow_flower meta 0.
            case "minecraft:dandelion" -> "yellow_flower|0";
            case "minecraft:poppy" -> "red_flower|0";
            case "minecraft:blue_orchid" -> "red_flower|1";
            case "minecraft:allium" -> "red_flower|2";
            case "minecraft:azure_bluet" -> "red_flower|3";
            case "minecraft:red_tulip" -> "red_flower|4";
            case "minecraft:orange_tulip" -> "red_flower|5";
            case "minecraft:white_tulip" -> "red_flower|6";
            case "minecraft:pink_tulip" -> "red_flower|7";
            case "minecraft:oxeye_daisy" -> "red_flower|8";
            // Vanilla double_plant contained all six large flower/grass IDs.
            case "minecraft:sunflower" -> "double_plant|0";
            case "minecraft:lilac" -> "double_plant|1";
            case "minecraft:tall_grass" -> "double_plant|2";
            case "minecraft:large_fern" -> "double_plant|3";
            case "minecraft:rose_bush" -> "double_plant|4";
            case "minecraft:peony" -> "double_plant|5";
            // Original stonebrick (singular) + variant damage metadata.
            case "minecraft:stone_bricks" -> "stonebrick|0";
            case "minecraft:mossy_stone_bricks" -> "stonebrick|1";
            case "minecraft:cracked_stone_bricks" -> "stonebrick|2";
            case "minecraft:chiseled_stone_bricks" -> "stonebrick|3";
            // Metadata-bearing ItemCoal, golden_apple, block sponge and anvil.
            case "minecraft:charcoal" -> "coal|1";
            case "minecraft:enchanted_golden_apple" -> "golden_apple|1";
            case "minecraft:wet_sponge" -> "sponge|1";
            case "minecraft:chipped_anvil" -> "anvil|1";
            case "minecraft:damaged_anvil" -> "anvil|2";
            // Original 1.12 skull items shared ONE registry identity.
            case "minecraft:skeleton_skull" -> "skull|0";
            case "minecraft:wither_skeleton_skull" -> "skull|1";
            case "minecraft:zombie_head" -> "skull|2";
            case "minecraft:player_head" -> "skull|3";
            case "minecraft:creeper_head" -> "skull|4";
            case "minecraft:dragon_head" -> "skull|5";
            default -> null;
        };
        if (legacyMetaVariant != null) {
            int split = legacyMetaVariant.indexOf('|');
            return forItem("minecraft:" + legacyMetaVariant.substring(0, split), false,
                    Integer.parseInt(legacyMetaVariant.substring(split + 1)));
        }
        // BETA26 bed ITEM has color metadata 0..15, using block-color order.
        // This is the inventory item key, independent of bed head/foot/facing.
        if (modernRegistryId.startsWith("minecraft:") && modernRegistryId.endsWith("_bed")) {
            String color = modernRegistryId.substring("minecraft:".length(),
                    modernRegistryId.length() - "_bed".length());
            int oldColor = legacyDyeColorMetadata(color);
            if (oldColor >= 0) return forItem("minecraft:bed", false, oldColor);
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
