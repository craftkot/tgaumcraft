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
            case "Horse", "Donkey" -> list(a.BEAST,15,a.EARTH,5,a.AIR,5);
            default -> new AspectListData();
        };
    }

    /** Modern vanilla registry id -> legacy 1.12 EntityList name for the exact table above. */
    public static String legacyName(String modernId) {
        return Map.of(
                "minecraft:zombie","Zombie",
                "minecraft:husk","Husk",
                "minecraft:giant","Giant",
                "minecraft:skeleton","Skeleton",
                "minecraft:wither_skeleton","WitherSkeleton",
                "minecraft:creeper","Creeper",
                "minecraft:horse","Horse",
                "minecraft:donkey","Donkey"
        ).get(modernId);
    }

    private static AspectListData list(Object... pairs) {
        AspectListData out=new AspectListData();
        for(int i=0;i<pairs.length;i+=2) out.add((AspectData)pairs[i],(Integer)pairs[i+1]);
        return out;
    }
}
