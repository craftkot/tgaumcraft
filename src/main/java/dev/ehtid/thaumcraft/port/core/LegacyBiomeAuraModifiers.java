package dev.ehtid.thaumcraft.port.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Final BETA26 BiomeHandler biome-type aura modifiers after registerBiomes().
 * Type.DRY is deliberately 0.125: its later ENTROPY registration overwrites
 * the earlier FIRE registration (0.25) in BiomeHandler.biomeInfo.
 */
public final class LegacyBiomeAuraModifiers {
    private LegacyBiomeAuraModifiers() {}

    private static final Map<String, Float> MODIFIERS = create();

    private static Map<String, Float> create() {
        LinkedHashMap<String, Float> m = new LinkedHashMap<>();
        m.put("WATER", .33F);
        m.put("OCEAN", .33F);
        m.put("RIVER", .4F);
        m.put("WET", .4F);
        m.put("LUSH", .5F);
        m.put("HOT", .33F);
        m.put("DRY", .25F);
        m.put("NETHER", .125F);
        m.put("MESA", .33F);
        m.put("SPOOKY", .5F);
        m.put("DENSE", .4F);
        m.put("SNOWY", .25F);
        m.put("COLD", .25F);
        m.put("MUSHROOM", .75F);
        m.put("MAGICAL", .75F);
        m.put("CONIFEROUS", .33F);
        m.put("FOREST", .5F);
        m.put("SANDY", .25F);
        m.put("BEACH", .3F);
        m.put("JUNGLE", .6F);
        m.put("SAVANNA", .25F);
        m.put("MOUNTAIN", .3F);
        m.put("HILLS", .33F);
        m.put("PLAINS", .3F);
        m.put("END", .125F);
        // BETA26 registers DRY a second time near the end; HashMap.put overwrites it.
        m.put("DRY", .125F);
        m.put("SPARSE", .2F);
        m.put("SWAMP", .5F);
        m.put("WASTELAND", .125F);
        m.put("DEAD", .1F);
        return Map.copyOf(m);
    }

    public static Float modifier(String legacyType) {
        return MODIFIERS.get(legacyType);
    }

    /**
     * Mirrors BiomeHandler.getBiomeAuraModifier: average all registered types;
     * any lookup exception/missing type falls back to 0.5 for the whole biome.
     */
    public static float average(Collection<String> legacyTypes) {
        try {
            float total = 0.0F;
            int count = 0;
            for (String type : legacyTypes) {
                Float modifier = MODIFIERS.get(type);
                if (modifier == null) throw new IllegalArgumentException("Unknown legacy biome type " + type);
                total += modifier;
                count++;
            }
            return total / (float) count;
        } catch (Exception ignored) {
            return 0.5F;
        }
    }

    public static Map<String, Float> table() {
        return MODIFIERS;
    }
}
