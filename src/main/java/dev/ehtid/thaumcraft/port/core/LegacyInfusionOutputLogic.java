package dev.ehtid.thaumcraft.port.core;

import java.util.*;

/** Numeric and enchantment rules transcribed from BETA26, with no world or registry access. */
public final class LegacyInfusionOutputLogic {
    private LegacyInfusionOutputLogic() {}

    public record Enchantment(String name, int legacyOrdinal, int maxLevel, Set<String> toolClasses) {
        public Enchantment { toolClasses = Set.copyOf(toolClasses); }
    }
    private static final List<Enchantment> ENCHANTMENTS = List.of(
            new Enchantment("COLLECTOR",0,1,Set.of("axe","pickaxe","shovel","weapon")),
            new Enchantment("DESTRUCTIVE",1,1,Set.of("axe","pickaxe","shovel")),
            new Enchantment("BURROWING",2,1,Set.of("axe","pickaxe")),
            new Enchantment("SOUNDING",3,4,Set.of("pickaxe")),
            new Enchantment("REFINING",4,4,Set.of("pickaxe")),
            new Enchantment("ARCING",5,4,Set.of("weapon")),
            new Enchantment("ESSENCE",6,5,Set.of("weapon")),
            new Enchantment("VISBATTERY",7,3,Set.of("chargable")),
            new Enchantment("VISCHARGE",8,1,Set.of("chargable")),
            new Enchantment("SWIFT",9,4,Set.of("boots")),
            new Enchantment("AGILE",10,1,Set.of("legs")),
            new Enchantment("INFESTED",11,1,Set.of("chest")),
            new Enchantment("LAMPLIGHT",12,1,Set.of("axe","pickaxe","shovel")));
    public static Enchantment enchantment(String name) {
        return ENCHANTMENTS.stream().filter(value -> value.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown source infusion enchantment " + name));
    }
    public static boolean validEnchantmentOrdinal(int ordinal) { return ordinal >= 0 && ordinal < ENCHANTMENTS.size(); }

    /** craftingFinish applies damage fraction only to a pristine damageable stack output. */
    public static int transferredDamage(int centerDamage, int centerMax, boolean centerDamageable,
                                        int outputDamage, int outputMax, boolean outputDamageable) {
        if (centerDamageable && centerDamage > 0 && outputDamageable && outputDamage <= 0)
            return (int)(outputMax * ((float)centerDamage / centerMax));
        return outputDamage;
    }
    public static Map<String,Integer> runicAspects(int oldRunic) {
        int protect = 20 + (int)(20.0D * Math.pow(2.0D,oldRunic));
        if (protect <= 0) return Map.of();
        return Map.of("praemunio",protect,"vitreus",protect / 2,"potentia",protect / 2);
    }
    public static int runicInstability(int oldRunic) { return 5 + oldRunic / 2; }
    public static int runicAmberCount(int oldRunic) { return 1 + Math.max(0,oldRunic); }
    public static byte nextRunic(int oldRunic) { return (byte)(oldRunic + 1); }

    /** BETA26 casts each float product to int, before the altar cost multiplier. */
    public static Map<String,Integer> enchantmentAspects(Map<String,Integer> base, int oldLevel,
                                                        int otherEnchantments) {
        if (oldLevel < 0 || otherEnchantments < 0) throw new IllegalArgumentException("Negative enchantment state");
        float multiplier = oldLevel + 1 + otherEnchantments * 0.33F;
        var result = new LinkedHashMap<String,Integer>();
        base.forEach((aspect,amount) -> result.put(aspect,(int)(amount * multiplier)));
        return Collections.unmodifiableMap(result);
    }
    public static byte enchantmentWarp(int currentWarp, int validEnchantmentCount, int randomTen) {
        if (validEnchantmentCount < 0 || randomTen < 0 || randomTen >= 10)
            throw new IllegalArgumentException("Invalid source enchantment roll");
        return (byte)(currentWarp + (randomTen < validEnchantmentCount ? 1 : 0));
    }
}
