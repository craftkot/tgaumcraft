package dev.ehtid.thaumcraft.port.core;

import java.util.Map;

/** Exact vanilla BETA26 entity aspect registrations extracted from ConfigAspects bytecode. */
public final class LegacyEntityAspectTable {
    private LegacyEntityAspectTable() {}

    public static AspectListData get(AspectRegistry a, String n) {
        return switch (n) {
            case "Zombie" -> list(a.UNDEAD,20,a.MAN,10,a.EARTH,5);
            case "Husk" -> list(a.UNDEAD,20,a.MAN,10,a.FIRE,5);
            case "Giant" -> list(a.UNDEAD,25,a.MAN,15,a.EARTH,10);
            case "Skeleton" -> list(a.UNDEAD,20,a.MAN,5,a.EARTH,5);
            case "WitherSkeleton" -> list(a.UNDEAD,25,a.MAN,5,a.ENTROPY,10);
            case "Creeper" -> list(a.PLANT,15,a.FIRE,15);
            case "Horse","Donkey","Mule" -> list(a.BEAST,15,a.EARTH,5,a.AIR,5);
            case "SkeletonHorse" -> list(a.BEAST,5,a.UNDEAD,10,a.EARTH,5,a.AIR,5);
            case "ZombieHorse" -> list(a.BEAST,10,a.UNDEAD,5,a.EARTH,5,a.AIR,5);
            case "Pig" -> list(a.BEAST,10,a.EARTH,10,a.DESIRE,5);
            case "XPOrb" -> list(a.MIND,10);
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
            case "Ghast" -> list(a.UNDEAD,15,a.FIRE,15);
            case "PigZombie" -> list(a.UNDEAD,15,a.FIRE,15,a.BEAST,10);
            case "Enderman" -> list(a.ELDRITCH,10,a.MOTION,15,a.DESIRE,5);
            case "CaveSpider" -> list(a.BEAST,5,a.DEATH,10,a.TRAP,10);
            case "Silverfish" -> list(a.BEAST,5,a.EARTH,10);
            case "Blaze" -> list(a.ELDRITCH,5,a.FIRE,15,a.FLIGHT,5);
            case "LavaSlime" -> list(a.WATER,5,a.FIRE,10,a.ALCHEMY,5);
            case "EnderDragon" -> list(a.ELDRITCH,50,a.BEAST,30,a.ENTROPY,50,a.FLIGHT,10);
            case "WitherBoss" -> list(a.UNDEAD,50,a.ENTROPY,25,a.FIRE,25);
            case "Witch" -> list(a.MAN,15,a.MAGIC,5,a.ALCHEMY,10);
            case "Villager" -> list(a.MAN,15);
            case "VillagerGolem" -> list(a.METAL,15,a.MAN,5,a.MECHANISM,5,a.MAGIC,5);
            case "EnderCrystal" -> list(a.ELDRITCH,15,a.AURA,15,a.LIFE,15);
            case "ItemFrame" -> list(a.SENSES,5,a.CRAFT,5);
            case "Painting" -> list(a.SENSES,10,a.CRAFT,5);
            case "Guardian" -> list(a.BEAST,10,a.ELDRITCH,10,a.WATER,10);
            case "Rabbit" -> list(a.BEAST,5,a.EARTH,5,a.MOTION,5);
            case "Endermite" -> list(a.BEAST,5,a.ELDRITCH,5,a.MOTION,5);
            case "PolarBear" -> list(a.BEAST,15,a.COLD,10);
            case "Shulker" -> list(a.ELDRITCH,10,a.TRAP,5,a.FLIGHT,5,a.PROTECT,5);
            case "EvocationIllager" -> list(a.ELDRITCH,5,a.MAGIC,5,a.MAN,10);
            case "VindicationIllager" -> list(a.AVERSION,5,a.MAGIC,5,a.MAN,10);
            case "IllusionIllager" -> list(a.SENSES,5,a.MAGIC,5,a.MAN,10);
            case "Llama" -> list(a.BEAST,15,a.WATER,5);
            case "Parrot" -> list(a.BEAST,5,a.FLIGHT,5,a.SENSES,5);
            case "Stray" -> list(a.UNDEAD,20,a.MAN,5,a.TRAP,5);
            case "Vex" -> list(a.ELDRITCH,5,a.FLIGHT,5,a.MAGIC,5,a.MAN,5);
            default -> new AspectListData();
        };
    }

    public static String legacyName(String id) {
        return MODERN_TO_LEGACY.get(id);
    }

    private static final Map<String,String> MODERN_TO_LEGACY = Map.ofEntries(
        e("minecraft:zombie","Zombie"),e("minecraft:husk","Husk"),e("minecraft:giant","Giant"),
        e("minecraft:skeleton","Skeleton"),e("minecraft:wither_skeleton","WitherSkeleton"),e("minecraft:creeper","Creeper"),
        e("minecraft:horse","Horse"),e("minecraft:donkey","Donkey"),e("minecraft:mule","Mule"),
        e("minecraft:skeleton_horse","SkeletonHorse"),e("minecraft:zombie_horse","ZombieHorse"),e("minecraft:pig","Pig"),
        e("minecraft:experience_orb","XPOrb"),e("minecraft:sheep","Sheep"),e("minecraft:cow","Cow"),e("minecraft:mooshroom","MushroomCow"),
        e("minecraft:snow_golem","SnowMan"),e("minecraft:ocelot","Ozelot"),e("minecraft:chicken","Chicken"),e("minecraft:squid","Squid"),
        e("minecraft:wolf","Wolf"),e("minecraft:bat","Bat"),e("minecraft:spider","Spider"),e("minecraft:slime","Slime"),
        e("minecraft:ghast","Ghast"),e("minecraft:zombified_piglin","PigZombie"),e("minecraft:enderman","Enderman"),e("minecraft:cave_spider","CaveSpider"),
        e("minecraft:silverfish","Silverfish"),e("minecraft:blaze","Blaze"),e("minecraft:magma_cube","LavaSlime"),e("minecraft:ender_dragon","EnderDragon"),
        e("minecraft:wither","WitherBoss"),e("minecraft:witch","Witch"),e("minecraft:villager","Villager"),e("minecraft:iron_golem","VillagerGolem"),
        e("minecraft:end_crystal","EnderCrystal"),e("minecraft:item_frame","ItemFrame"),e("minecraft:painting","Painting"),e("minecraft:guardian","Guardian"),
        e("minecraft:rabbit","Rabbit"),e("minecraft:endermite","Endermite"),e("minecraft:polar_bear","PolarBear"),e("minecraft:shulker","Shulker"),
        e("minecraft:evoker","EvocationIllager"),e("minecraft:vindicator","VindicationIllager"),e("minecraft:illusioner","IllusionIllager"),
        e("minecraft:llama","Llama"),e("minecraft:parrot","Parrot"),e("minecraft:stray","Stray"),e("minecraft:vex","Vex")
    );
    private static Map.Entry<String,String> e(String a,String b){ return Map.entry(a,b); }
    private static AspectListData list(Object... p){ AspectListData o=new AspectListData(); for(int i=0;i<p.length;i+=2)o.add((AspectData)p[i],(Integer)p[i+1]); return o; }
}
