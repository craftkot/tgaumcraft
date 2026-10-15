package dev.ehtid.thaumcraft.port.core;

/**
 * Compatibility projection from modern vanilla biome IDs to BETA26's 1.12
 * BiomeDictionary types. It is intentionally NOT presented as source-exact
 * for newly added 1.21 biomes; unknown/modded biomes retain the original
 * fallback modifier 0.5 rather than pretending to be a known BETA26 biome.
 */
public final class LegacyModernBiomeCompatibility {
    private LegacyModernBiomeCompatibility() {}

    public static String originalConstant(String biomeId) {
        if (biomeId == null || !biomeId.startsWith("minecraft:")) return null;
        return switch (biomeId.substring("minecraft:".length())) {
            case "plains" -> "PLAINS";
            case "sunflower_plains" -> "MUTATED_PLAINS";
            case "desert" -> "DESERT";
            case "forest" -> "FOREST";
            case "flower_forest" -> "MUTATED_FOREST";
            case "birch_forest" -> "BIRCH_FOREST";
            case "old_growth_birch_forest" -> "MUTATED_BIRCH_FOREST";
            case "dark_forest" -> "ROOFED_FOREST";
            case "taiga" -> "TAIGA";
            case "snowy_taiga" -> "COLD_TAIGA";
            case "old_growth_pine_taiga" -> "REDWOOD_TAIGA";
            case "old_growth_spruce_taiga" -> "MUTATED_REDWOOD_TAIGA";
            case "swamp" -> "SWAMPLAND";
            case "mangrove_swamp" -> "SWAMPLAND";
            case "river" -> "RIVER";
            case "frozen_river" -> "FROZEN_RIVER";
            case "ocean", "lukewarm_ocean", "warm_ocean", "cold_ocean" -> "OCEAN";
            case "frozen_ocean" -> "FROZEN_OCEAN";
            case "deep_ocean", "deep_lukewarm_ocean", "deep_cold_ocean",
                 "deep_frozen_ocean" -> "DEEP_OCEAN";
            case "mushroom_fields" -> "MUSHROOM_ISLAND";
            case "beach" -> "BEACH";
            case "snowy_beach" -> "COLD_BEACH";
            case "stony_shore" -> "STONE_BEACH";
            case "jungle" -> "JUNGLE";
            case "sparse_jungle" -> "JUNGLE_EDGE";
            case "bamboo_jungle" -> "JUNGLE_HILLS";
            case "savanna" -> "SAVANNA";
            case "savanna_plateau" -> "SAVANNA_PLATEAU";
            case "windswept_savanna" -> "MUTATED_SAVANNA";
            case "badlands" -> "MESA";
            case "eroded_badlands" -> "MESA_ROCK";
            case "wooded_badlands" -> "MESA_CLEAR_ROCK";
            case "snowy_plains" -> "ICE_PLAINS";
            case "ice_spikes" -> "MUTATED_ICE_FLATS";
            case "windswept_hills", "stony_peaks", "jagged_peaks" -> "EXTREME_HILLS";
            case "windswept_gravelly_hills" -> "MUTATED_EXTREME_HILLS";
            case "windswept_forest" -> "EXTREME_HILLS_WITH_TREES";
            case "snowy_slopes", "frozen_peaks" -> "ICE_MOUNTAINS";
            case "grove" -> "COLD_TAIGA";
            case "meadow" -> "PLAINS";
            case "nether_wastes", "soul_sand_valley", "crimson_forest",
                 "warped_forest", "basalt_deltas" -> "HELL";
            case "the_end", "end_highlands", "end_midlands", "small_end_islands",
                 "end_barrens" -> "SKY";
            case "the_void" -> "VOID";
            default -> null;
        };
    }

    public static float auraModifier(String biomeId) {
        String original = originalConstant(biomeId);
        return original == null ? 0.5F : LegacyVanillaBiomeTypes.auraModifier(original);
    }
}
