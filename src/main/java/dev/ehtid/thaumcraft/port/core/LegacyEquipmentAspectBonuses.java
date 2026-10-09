package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Vanilla 1.12 ItemArmor, ItemSword, ItemBow, ItemTool, ItemHoe and ItemShears
 * clauses of BETA26 ThaumcraftCraftingManager.getBonusTags.
 *
 * BETA26 uses AspectList.merge (MAX, not addition). The following mappings
 * are limited to vanilla equipment that already existed in 1.12; later items
 * such as netherite armor, crossbows and tridents are not retroactively made
 * into historical BETA26 gear.
 */
public final class LegacyEquipmentAspectBonuses {
    private LegacyEquipmentAspectBonuses() {}

    public static AspectListData apply(AspectRegistry aspects, AspectListData base, String itemId) {
        Objects.requireNonNull(aspects, "aspects");
        Objects.requireNonNull(base, "base");
        AspectListData out = base.copy();
        if (itemId == null) return out;
        if ("minecraft:bow".equals(itemId)) {
            out.merge(aspects.AVERSION, 10).merge(aspects.FLIGHT, 5);
            return out;
        }
        int armor = armorPoints(itemId);
        if (armor > 0) {
            out.merge(aspects.PROTECT, armor * 4);
            return out;
        }

        int sword = swordBonus(itemId);
        if (sword > 0) {
            out.merge(aspects.AVERSION, sword);
            return out;
        }

        int tool = toolBonus(itemId);
        if (tool > 0) out.merge(aspects.TOOL, tool);
        return out;
    }

    private static int armorPoints(String id) {
        return switch (id) {
            // Protection amounts are vanilla 1.12 ItemArmor.damageReduceAmount.
            case "minecraft:leather_helmet", "minecraft:leather_boots",
                 "minecraft:chainmail_boots", "minecraft:golden_boots" -> 1;
            case "minecraft:leather_leggings", "minecraft:chainmail_helmet",
                 "minecraft:iron_helmet", "minecraft:iron_boots",
                 "minecraft:golden_helmet" -> 2;
            case "minecraft:leather_chestplate", "minecraft:golden_leggings",
                 "minecraft:diamond_helmet", "minecraft:diamond_boots" -> 3;
            case "minecraft:chainmail_leggings" -> 4;
            case "minecraft:chainmail_chestplate", "minecraft:iron_leggings",
                 "minecraft:golden_chestplate" -> 5;
            case "minecraft:iron_chestplate", "minecraft:diamond_leggings" -> 6;
            case "minecraft:diamond_chestplate" -> 8;
            default -> 0;
        };
    }

    private static int swordBonus(String id) {
        // BETA26 uses (int)(ItemSword.getAttackDamage() + 1) * 4.
        return switch (id) {
            case "minecraft:wooden_sword", "minecraft:golden_sword" -> 16;
            case "minecraft:stone_sword" -> 20;
            case "minecraft:iron_sword" -> 24;
            case "minecraft:diamond_sword" -> 28;
            default -> 0;
        };
    }

    private static int toolBonus(String id) {
        if ("minecraft:shears".equals(id)) return 12;
        String[] types = {"_pickaxe", "_axe", "_shovel", "_hoe"};
        for (String suffix : types) {
            if (!id.startsWith("minecraft:") || !id.endsWith(suffix)) continue;
            String material = id.substring("minecraft:".length(), id.length() - suffix.length());
            // ItemTool compares 1.12 harvest tier, whereas ItemHoe uses maximum
            // durability thresholds. They produce the same values here except
            // gold hoes, whose 32 durability falls into the WOOD <=59 branch.
            return switch (material) {
                case "wooden", "golden" -> 4;
                case "stone" -> 8;
                case "iron" -> 12;
                case "diamond" -> 16;
                default -> 0;
            };
        }
        return 0;
    }
}
