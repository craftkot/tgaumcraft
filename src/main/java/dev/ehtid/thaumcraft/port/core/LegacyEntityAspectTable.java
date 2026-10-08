package dev.ehtid.thaumcraft.port.core;

import java.util.Map;

/**
 * First exact BETA26 entity-aspect slice, extracted from ConfigAspects.registerEntityAspects bytecode.
 * Keys are legacy EntityList names; values intentionally preserve legacy amounts.
 */
public final class LegacyEntityAspectTable {
    private LegacyEntityAspectTable() {}

    public static AspectListData get(AspectRegistry a, String legacyEntityName) {
        return switch (legacyEntityName) {
            case "Zombie" -> list(a.UNDEAD,20,a.MAN,10,a.EARTH,5);
            case "Husk" -> list(a.UNDEAD,20,a.MAN,10,a.FIRE,5);
            case "Giant" -> list(a.UNDEAD,25,a.MAN,15,a.EARTH,10);
            case "Skeleton" -> list(a.UNDEAD,20,a.MAN,5,a.EARTH,5);
            case "WitherSkeleton" -> list(a.UNDEAD,25,a.MAN,5,a.ENTROPY,10);
            case "Creeper" -> list(a.PLANT,15,a.FIRE,15);
            case "Horse", "Donkey", "Mule" -> list(a.BEAST,15,a.EARTH,5,a.AIR,5);
            case "SkeletonHorse" -> list(a.BEAST,5,a.UNDEAD,10,a.EARTH,5,a.AIR,5);
            case "ZombieHorse" -> list(a.BEAST,10,a.UNDEAD,5,a.EARTH,5,a.AIR,5);
            case "Pig" -> list(a.BEAST,10,a.EARTH,10,a.DESIRE,5);
            case "Sheep" -> list(a.BEAST,10,a.EARTH,10);
            case "Cow" -> list(a.BEAST,15,a.EARTH,15);
            case "MushroomCow" -> list(a.BEAST,15,a.PLANT,15,a.EARTH,15);
            case "SnowMan" -> list(a.COLD,10,a.MAN,5,a.MECHANISM,5,a.MAGIC,5);
            case "Ozelot" -> list(a.BEAST,10,a.ENTROPY,10);
            case "Chicken" -> list(a.BEAST,5,a.FLIGHT,5,a.AIR,5);
            case "Squid" -> list(a.BEAST,5,a.WATER,10);
            case "Wolf" -> list(a.BEAST,15,a.EARTH,10,a.AVERSION,5);
            case "Bat" -> list(a.BEAST,5,a.FLIGHT,5,a.DARKNESS,5);
            case "Spider" -> list(a.BEAST,10,a.ENTROPY,10,a.TRAP,10);
            case "Slime" -> list(a.LIFE,10,a.WATER,10,a.ALCHEMY,5);
            case "MagmaCube" -> list(a.LIFE,10,a.FIRE,10,a.ALCHEMY,5);
            case "Blaze" -> list(a.FIRE,20,a.FLIGHT,10,a.MAGIC,5);
            case "Ghast" -> list(a.BEAST,10,a.FLIGHT,15,a.FIRE,10);
            case "Enderman" -> list(a.MAN,10,a.VOID,20,a.MOTION,10);
            case "Endermite" -> list(a.BEAST,5,a.VOID,10,a.ENTROPY,5);
            case "Silverfish" -> list(a.BEAST,5,a.EARTH,5,a.ENTROPY,5);
            case "Guardian" -> list(a.BEAST,15,a.WATER,15,a.AVERSION,10);
            case "ElderGuardian" -> list(a.BEAST,20,a.WATER,20,a.AVERSION,15);
            case "Witch" -> list(a.MAN,10,a.MAGIC,15,a.ALCHEMY,10);
            case "Villager" -> list(a.MAN,15,a.DESIRE,10);
            case "IronGolem" -> list(a.METAL,20,a.MAN,5,a.MECHANISM,10,a.PROTECT,10);
            case "Shulker" -> list(a.BEAST,10,a.PROTECT,15,a.VOID,10);
            default -> new AspectListData();
        };
    }

    /** Modern vanilla registry id -> legacy 1.12 EntityList name for the exact table above. */
    public static String legacyName(String modernId) {
        return Map.ofEntries(
                Map.entry("minecraft:zombie","Zombie"), Map.entry("minecraft:husk","Husk"),
                Map.entry("minecraft:giant","Giant"), Map.entry("minecraft:skeleton","Skeleton"),
                Map.entry("minecraft:wither_skeleton","WitherSkeleton"), Map.entry("minecraft:creeper","Creeper"),
                Map.entry("minecraft:horse","Horse"), Map.entry("minecraft:donkey","Donkey"),
                Map.entry("minecraft:mule","Mule"), Map.entry("minecraft:skeleton_horse","SkeletonHorse"),
                Map.entry("minecraft:zombie_horse","ZombieHorse"), Map.entry("minecraft:pig","Pig"),
                Map.entry("minecraft:sheep","Sheep"), Map.entry("minecraft:cow","Cow"),
                Map.entry("minecraft:mooshroom","MushroomCow"), Map.entry("minecraft:snow_golem","SnowMan"),
                Map.entry("minecraft:ocelot","Ozelot"), Map.entry("minecraft:chicken","Chicken"),
                Map.entry("minecraft:squid","Squid"), Map.entry("minecraft:wolf","Wolf"),
                Map.entry("minecraft:bat","Bat"), Map.entry("minecraft:spider","Spider"),
                Map.entry("minecraft:slime","Slime"), Map.entry("minecraft:magma_cube","MagmaCube"),
                Map.entry("minecraft:blaze","Blaze"), Map.entry("minecraft:ghast","Ghast"),
                Map.entry("minecraft:enderman","Enderman"), Map.entry("minecraft:endermite","Endermite"),
                Map.entry("minecraft:silverfish","Silverfish"), Map.entry("minecraft:guardian","Guardian"),
                Map.entry("minecraft:elder_guardian","ElderGuardian"), Map.entry("minecraft:witch","Witch"),
                Map.entry("minecraft:villager","Villager"), Map.entry("minecraft:iron_golem","IronGolem"),
                Map.entry("minecraft:shulker","Shulker")
        ).get(modernId);
    }

    private static AspectListData list(Object... pairs) {
        AspectListData out=new AspectListData();
        for(int i=0;i<pairs.length;i+=2) out.add((AspectData)pairs[i],(Integer)pairs[i+1]);
        return out;
    }
}
