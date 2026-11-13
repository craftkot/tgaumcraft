package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/**
 * BETA26 ThaumcraftWorldGenerator.generateOres crystal-cluster arithmetic.
 * The 1.21 world integration keeps the original 8 attempts, 64-size budget,
 * 2/3 cell chance, 1..3 crystal sizes and biome-type aspect selection.
 */
public final class LegacyCrystalWorldGenLogic {
    public static final int DEFAULT_DENSITY_PERCENT = 100;

    private LegacyCrystalWorldGenLogic() {}

    public static int attemptCount(int densityPercent) {
        float density = densityPercent / 100.0F;
        return Math.round(8.0F * density);
    }

    public static int maximumCrystalAmount(int densityPercent) {
        float density = densityPercent / 100.0F;
        return Math.round(64.0F * density);
    }

    /** Converts the source-era zero-based Y range to modern worlds with negative min build height. */
    public static int yRange(int minBuildHeight, int surfaceY) {
        return Math.max(5, surfaceY - minBuildHeight - 5);
    }

    public static int clusterSize(int zeroBasedRoll) {
        if (zeroBasedRoll < 0 || zeroBasedRoll > 2) {
            throw new IllegalArgumentException("Crystal cluster size roll must be in [0,2]");
        }
        return zeroBasedRoll + 1;
    }

    /** BETA26 accepts non-liquid air/replaceable cells touching a solid ROCK face. */
    public static boolean canPlace(boolean liquid, boolean airOrReplaceable, boolean touchesRock) {
        return !liquid && airOrReplaceable && touchesRock;
    }

    /**
     * Mirrors BETA26's random metadata draw followed by its one-in-three
     * biome-tag override. DRY's second registerBiomeInfo call overwrites FIRE
     * with ENTROPY, so the final DRY metadata is 5.
     *
     * The supplied function is Random.nextInt(bound); it is injected so tests
     * can prove the exact bound/call order without replacing production rules.
     */
    public static int chooseShardMetadata(String biomeId, IntUnaryOperator nextInt) {
        Objects.requireNonNull(nextInt, "nextInt");
        int metadata = bounded(nextInt, 6);
        if (bounded(nextInt, 3) == 0) {
            String legacyBiome = LegacyModernBiomeCompatibility.originalConstant(biomeId);
            List<String> types = legacyBiome == null ? null : LegacyVanillaBiomeTypes.types(legacyBiome);
            Integer biomeMetadata = null;
            if (types != null && !types.isEmpty()) {
                String selectedType = types.get(bounded(nextInt, types.size()));
                biomeMetadata = aspectMetadata(selectedType);
            }
            // BiomeHandler.getRandomBiomeTag catches unknown/unregistered types
            // and returns null; generateOres then draws a fresh random metadata.
            if (biomeMetadata == null) metadata = bounded(nextInt, 6);
            else metadata = biomeMetadata;
        }
        return metadata;
    }

    /** Returns the BETA26 ShardType metadata for a registered BiomeDictionary type. */
    public static Integer aspectMetadata(String biomeType) {
        if (biomeType == null) return null;
        return switch (biomeType) {
            case "WATER", "OCEAN", "RIVER", "WET", "LUSH" -> 2; // WATER
            case "HOT", "NETHER", "MESA", "SPOOKY" -> 1;       // FIRE
            case "DENSE", "SNOWY", "COLD", "MUSHROOM", "MAGICAL" -> 4; // ORDER
            case "CONIFEROUS", "FOREST", "SANDY", "BEACH", "JUNGLE" -> 3; // EARTH
            case "SAVANNA", "MOUNTAIN", "HILLS", "PLAINS", "END" -> 0; // AIR
            case "DRY", "SPARSE", "SWAMP", "WASTELAND", "DEAD" -> 5; // ENTROPY
            default -> null;
        };
    }

    private static int bounded(IntUnaryOperator nextInt, int bound) {
        int value = nextInt.applyAsInt(bound);
        if (value < 0 || value >= bound) {
            throw new IllegalArgumentException("nextInt(" + bound + ") returned " + value);
        }
        return value;
    }
}
