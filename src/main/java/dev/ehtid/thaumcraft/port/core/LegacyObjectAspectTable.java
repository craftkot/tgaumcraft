package dev.ehtid.thaumcraft.port.core;

/** Exact unambiguous BETA26 object-tag slice extracted from ConfigAspects.registerItemAspects bytecode. */
public final class LegacyObjectAspectTable {
    private LegacyObjectAspectTable() {}
    public static AspectListData get(AspectRegistry a, String id) {
        return switch (id) {
            case "minecraft:lapis_lazuli" -> list(a.SENSES,15);
            case "minecraft:lapis_ore" -> list(a.EARTH,5,a.SENSES,15);
            case "minecraft:diamond" -> list(a.CRYSTAL,15,a.DESIRE,15);
            case "minecraft:emerald" -> list(a.CRYSTAL,15,a.DESIRE,10);
            case "minecraft:quartz" -> list(a.CRYSTAL,5);
            case "minecraft:nether_quartz_ore" -> list(a.EARTH,5,a.CRYSTAL,10);
            case "minecraft:iron_ingot" -> list(a.METAL,15);
            case "minecraft:gold_ingot" -> list(a.METAL,10,a.DESIRE,10);
            case "minecraft:redstone" -> list(a.ENERGY,10);
            case "minecraft:glowstone_dust" -> list(a.SENSES,5,a.LIGHT,10);
            case "minecraft:diamond_ore" -> list(a.EARTH,5,a.DESIRE,15,a.CRYSTAL,15);
            case "minecraft:emerald_ore" -> list(a.EARTH,5,a.DESIRE,10,a.CRYSTAL,15);
            case "minecraft:iron_ore" -> list(a.EARTH,5,a.METAL,15);
            case "minecraft:gold_ore" -> list(a.EARTH,5,a.METAL,10,a.DESIRE,10);
            case "minecraft:redstone_ore" -> list(a.EARTH,5,a.ENERGY,15);
            case "minecraft:glowstone" -> list(a.SENSES,10,a.LIGHT,15);
            case "minecraft:coal" -> list(a.ENERGY,10,a.FIRE,10);
            case "minecraft:coal_ore" -> list(a.EARTH,5,a.ENERGY,10,a.FIRE,10);
            // BETA26 registers Salis Mundus directly as an ItemStack with
            // exactly 5 Praecantatio and 5 Potentia (not a computed recipe tag).
            case "thaumcraft:salis_mundus" -> list(a.MAGIC,5,a.ENERGY,5);
            // BETA26 ConfigAspects.registerItemAspects registrations verified
            // against the original JAR's bytecode. These map established
            // 1.12 ore-dictionary names to their unambiguous vanilla items.
            case "minecraft:stone", "minecraft:granite", "minecraft:diorite",
                    "minecraft:andesite" -> list(a.EARTH,5);
            case "minecraft:cobblestone" -> list(a.EARTH,5,a.ENTROPY,1);
            case "minecraft:dirt" -> list(a.EARTH,5);
            case "minecraft:sand", "minecraft:red_sand" -> list(a.EARTH,5,a.ENTROPY,5);
            case "minecraft:grass_block" -> list(a.EARTH,5,a.PLANT,2);
            case "minecraft:end_stone" -> list(a.EARTH,5,a.DARKNESS,5);
            case "minecraft:gravel" -> list(a.EARTH,5,a.ENTROPY,2);
            case "minecraft:netherrack" -> list(a.EARTH,5,a.FIRE,2);
            case "minecraft:nether_brick" -> list(a.FIRE,1,a.ORDER,1);
            case "minecraft:glass" -> list(a.CRYSTAL,5);
            case "minecraft:obsidian" -> list(a.EARTH,5,a.FIRE,5,a.DARKNESS,5);
            case "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
                    "minecraft:jungle_log", "minecraft:acacia_log",
                    "minecraft:dark_oak_log" -> list(a.PLANT,20);
            case "minecraft:oak_sapling", "minecraft:spruce_sapling",
                    "minecraft:birch_sapling", "minecraft:jungle_sapling",
                    "minecraft:acacia_sapling", "minecraft:dark_oak_sapling"
                    -> list(a.PLANT,15,a.LIFE,5);
            case "minecraft:oak_leaves", "minecraft:spruce_leaves",
                    "minecraft:birch_leaves", "minecraft:jungle_leaves",
                    "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
                    "minecraft:vine" -> list(a.PLANT,5);
            case "minecraft:nether_wart" -> list(a.PLANT,1,a.FLUX,2,a.ALCHEMY,3);
            case "minecraft:cactus" -> list(a.PLANT,5,a.WATER,5,a.AVERSION,1);
            case "minecraft:sugar_cane" -> list(a.PLANT,5,a.WATER,3,a.AIR,2);
            case "minecraft:wheat" -> list(a.PLANT,5,a.LIFE,5);
            case "minecraft:carrot" -> list(a.PLANT,5,a.LIFE,5,a.SENSES,5);
            case "minecraft:potato" -> list(a.PLANT,5,a.LIFE,5,a.EARTH,5);
            case "minecraft:string" -> list(a.BEAST,5,a.CRAFT,1);
            case "minecraft:slime_ball" -> list(a.WATER,5,a.LIFE,5,a.ALCHEMY,1);
            case "minecraft:leather" -> list(a.BEAST,5,a.PROTECT,5);
            case "minecraft:feather" -> list(a.FLIGHT,5,a.AIR,5);
            case "minecraft:bone" -> list(a.DEATH,5,a.LIFE,5);
            case "minecraft:egg" -> list(a.LIFE,5,a.BEAST,5);
            case "minecraft:gunpowder" -> list(a.FIRE,10,a.ENTROPY,10,a.ALCHEMY,5);
            case "minecraft:ender_pearl" -> list(a.ELDRITCH,10,a.MOTION,15);
            case "minecraft:nether_star" -> list(a.ELDRITCH,10,a.MAGIC,20,a.ORDER,20,a.AURA,10);
            default -> new AspectListData();
        };
    }
    private static AspectListData list(Object... p){ AspectListData o=new AspectListData(); for(int i=0;i<p.length;i+=2)o.add((AspectData)p[i],(Integer)p[i+1]); return o; }
}
