package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact Forge 1.12.x BiomeDictionary registrations for vanilla biome constants.
 * WATER is injected as the BiomeDictionary supertype of OCEAN/RIVER, matching
 * addTypes/listSupertypes before Thaumcraft averages the resulting type set.
 */
public final class LegacyVanillaBiomeTypes {
    private LegacyVanillaBiomeTypes() {}

    private static final Map<String, List<String>> TYPES = create();

    private static Map<String, List<String>> create() {
        LinkedHashMap<String, List<String>> m = new LinkedHashMap<>();
        put(m,"OCEAN","OCEAN");
        put(m,"PLAINS","PLAINS");
        put(m,"DESERT","HOT","DRY","SANDY");
        put(m,"EXTREME_HILLS","MOUNTAIN","HILLS");
        put(m,"FOREST","FOREST");
        put(m,"TAIGA","COLD","CONIFEROUS","FOREST");
        put(m,"SWAMPLAND","WET","SWAMP");
        put(m,"RIVER","RIVER");
        put(m,"HELL","HOT","DRY","NETHER");
        put(m,"SKY","COLD","DRY","END");
        put(m,"FROZEN_OCEAN","COLD","OCEAN","SNOWY");
        put(m,"FROZEN_RIVER","COLD","RIVER","SNOWY");
        put(m,"ICE_PLAINS","COLD","SNOWY","WASTELAND");
        put(m,"ICE_MOUNTAINS","COLD","SNOWY","MOUNTAIN");
        put(m,"MUSHROOM_ISLAND","MUSHROOM","RARE");
        put(m,"MUSHROOM_ISLAND_SHORE","MUSHROOM","BEACH","RARE");
        put(m,"BEACH","BEACH");
        put(m,"DESERT_HILLS","HOT","DRY","SANDY","HILLS");
        put(m,"FOREST_HILLS","FOREST","HILLS");
        put(m,"TAIGA_HILLS","COLD","CONIFEROUS","FOREST","HILLS");
        put(m,"EXTREME_HILLS_EDGE","MOUNTAIN");
        put(m,"JUNGLE","HOT","WET","DENSE","JUNGLE");
        put(m,"JUNGLE_HILLS","HOT","WET","DENSE","JUNGLE","HILLS");
        put(m,"JUNGLE_EDGE","HOT","WET","JUNGLE","FOREST","RARE");
        put(m,"DEEP_OCEAN","OCEAN");
        put(m,"STONE_BEACH","BEACH");
        put(m,"COLD_BEACH","COLD","BEACH","SNOWY");
        put(m,"BIRCH_FOREST","FOREST");
        put(m,"BIRCH_FOREST_HILLS","FOREST","HILLS");
        put(m,"ROOFED_FOREST","SPOOKY","DENSE","FOREST");
        put(m,"COLD_TAIGA","COLD","CONIFEROUS","FOREST","SNOWY");
        put(m,"COLD_TAIGA_HILLS","COLD","CONIFEROUS","FOREST","SNOWY","HILLS");
        put(m,"REDWOOD_TAIGA","COLD","CONIFEROUS","FOREST");
        put(m,"REDWOOD_TAIGA_HILLS","COLD","CONIFEROUS","FOREST","HILLS");
        put(m,"EXTREME_HILLS_WITH_TREES","MOUNTAIN","FOREST","SPARSE");
        put(m,"SAVANNA","HOT","SAVANNA","PLAINS","SPARSE");
        put(m,"SAVANNA_PLATEAU","HOT","SAVANNA","PLAINS","SPARSE","RARE");
        put(m,"MESA","MESA","SANDY","DRY");
        put(m,"MESA_ROCK","MESA","SANDY","DRY","SPARSE");
        put(m,"MESA_CLEAR_ROCK","MESA","SANDY","DRY");
        put(m,"VOID","VOID");
        put(m,"MUTATED_PLAINS","PLAINS","RARE");
        put(m,"MUTATED_DESERT","HOT","DRY","SANDY","RARE");
        put(m,"MUTATED_EXTREME_HILLS","MOUNTAIN","SPARSE","RARE");
        put(m,"MUTATED_FOREST","FOREST","HILLS","RARE");
        put(m,"MUTATED_TAIGA","COLD","CONIFEROUS","FOREST","MOUNTAIN","RARE");
        put(m,"MUTATED_SWAMPLAND","WET","SWAMP","HILLS","RARE");
        put(m,"MUTATED_ICE_FLATS","COLD","SNOWY","HILLS","RARE");
        put(m,"MUTATED_JUNGLE","HOT","WET","DENSE","JUNGLE","MOUNTAIN","RARE");
        put(m,"MUTATED_JUNGLE_EDGE","HOT","SPARSE","JUNGLE","HILLS","RARE");
        put(m,"MUTATED_BIRCH_FOREST","FOREST","DENSE","HILLS","RARE");
        put(m,"MUTATED_BIRCH_FOREST_HILLS","FOREST","DENSE","MOUNTAIN","RARE");
        put(m,"MUTATED_ROOFED_FOREST","SPOOKY","DENSE","FOREST","MOUNTAIN","RARE");
        put(m,"MUTATED_TAIGA_COLD","COLD","CONIFEROUS","FOREST","SNOWY","MOUNTAIN","RARE");
        put(m,"MUTATED_REDWOOD_TAIGA","DENSE","FOREST","RARE");
        put(m,"MUTATED_REDWOOD_TAIGA_HILLS","DENSE","FOREST","HILLS","RARE");
        put(m,"MUTATED_EXTREME_HILLS_WITH_TREES","MOUNTAIN","SPARSE","RARE");
        put(m,"MUTATED_SAVANNA","HOT","DRY","SPARSE","SAVANNA","MOUNTAIN","RARE");
        put(m,"MUTATED_SAVANNA_ROCK","HOT","DRY","SPARSE","SAVANNA","HILLS","RARE");
        put(m,"MUTATED_MESA","HOT","DRY","SPARSE","MOUNTAIN","RARE");
        put(m,"MUTATED_MESA_ROCK","HOT","DRY","SPARSE","HILLS","RARE");
        put(m,"MUTATED_MESA_CLEAR_ROCK","HOT","DRY","SPARSE","MOUNTAIN","RARE");
        return Map.copyOf(m);
    }

    private static void put(Map<String,List<String>> map, String biome, String... rawTypes) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>(List.of(rawTypes));
        if ((types.contains("OCEAN") || types.contains("RIVER")) && !types.contains("WATER"))
            types.add("WATER");
        map.put(biome, List.copyOf(types));
    }

    public static List<String> types(String vanillaBiomeConstant) {
        return TYPES.get(vanillaBiomeConstant);
    }

    public static float auraModifier(String vanillaBiomeConstant) {
        List<String> types = types(vanillaBiomeConstant);
        return types == null ? 0.5F : LegacyBiomeAuraModifiers.average(types);
    }

    public static Map<String,List<String>> table() {
        return TYPES;
    }
}
