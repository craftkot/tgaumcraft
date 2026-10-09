package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Exact vanilla 1.12 BETA26 ThaumcraftCraftingManager.getBonusTags enchantment
 * dispatch, interpreted from its bytecode (offsets 709..1493).
 *
 * The original iterates the enchantment NBT list, multiplies each level by 3
 * with a Java SHORT narrowing conversion, merges a mapped aspect using MAX
 * (rather than addition) and accumulates total Praecantatio: scaled level
 * plus 2/4/6 for UNCOMMON/RARE/VERY_RARE enchants. Even an enchantment without
 * a specific aspect (e.g. Sweeping or a curse) contributes to Praecantatio.
 */
public final class LegacyEnchantmentAspectBonuses {
    private LegacyEnchantmentAspectBonuses() {}

    /** Vanilla 1.12 Enchantment.Rarity, with its original enchantment weight. */
    public enum Rarity {
        COMMON(10, 0), UNCOMMON(5, 2), RARE(2, 4), VERY_RARE(1, 6);

        private final int weight;
        private final int magicBonus;

        Rarity(int weight, int magicBonus) {
            this.weight = weight;
            this.magicBonus = magicBonus;
        }

        public int weight() { return weight; }
        public int magicBonus() { return magicBonus; }

        /** Used only for enchantments not in the vanilla 1.12 registry. */
        public static Rarity fromWeight(int weight) {
            return switch (weight) {
                case 10 -> COMMON;
                case 5 -> UNCOMMON;
                case 2 -> RARE;
                case 1 -> VERY_RARE;
                default -> COMMON;
            };
        }
    }

    public record Enchantment(String id, int level, Rarity rarity) {
        public Enchantment {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rarity, "rarity");
        }
    }

    /**
     * The four tiers in Minecraft 1.12 Enchantment.registerEnchantments;
     * deliberately independent of modern 1.21 data-pack rarity weights.
     * Returns null for post-1.12 and custom enchantments.
     */
    public static Rarity oldVanillaRarity(String id) {
        if (id == null) return null;
        return switch (id) {
            case "minecraft:protection", "minecraft:sharpness",
                    "minecraft:efficiency", "minecraft:power" -> Rarity.COMMON;

            case "minecraft:fire_protection", "minecraft:feather_falling",
                    "minecraft:projectile_protection", "minecraft:smite",
                    "minecraft:bane_of_arthropods", "minecraft:knockback",
                    "minecraft:unbreaking" -> Rarity.UNCOMMON;

            case "minecraft:blast_protection", "minecraft:respiration",
                    "minecraft:aqua_affinity", "minecraft:depth_strider",
                    "minecraft:frost_walker", "minecraft:fire_aspect",
                    "minecraft:looting", "minecraft:sweeping_edge",
                    "minecraft:fortune", "minecraft:punch", "minecraft:flame",
                    "minecraft:luck_of_the_sea", "minecraft:lure",
                    "minecraft:mending" -> Rarity.RARE;

            case "minecraft:thorns", "minecraft:binding_curse",
                    "minecraft:silk_touch", "minecraft:infinity",
                    "minecraft:vanishing_curse" -> Rarity.VERY_RARE;

            default -> null;
        };
    }

    public static AspectListData apply(AspectRegistry a, AspectListData base,
                                       List<Enchantment> enchantments) {
        Objects.requireNonNull(a, "aspects");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(enchantments, "enchantments");
        AspectListData out = base.copy();
        int magic = 0;

        for (Enchantment e : enchantments) {
            if (e == null) continue;
            // Original: (short)(getShort("lvl") * 3). The short's narrowing
            // occurs before the per-aspect amount and total are used.
            int scaled = (short) (e.level() * 3);
            int half = scaled / 2; // Java division truncates toward zero.
            switch (e.id()) {
                case "minecraft:aqua_affinity" -> out.merge(a.WATER, scaled);
                case "minecraft:bane_of_arthropods" -> {
                    out.merge(a.BEAST, half).merge(a.AVERSION, half);
                }
                case "minecraft:blast_protection" -> {
                    out.merge(a.PROTECT, half).merge(a.ENTROPY, half);
                }
                case "minecraft:efficiency" -> out.merge(a.TOOL, scaled);
                case "minecraft:feather_falling" -> out.merge(a.FLIGHT, scaled);
                case "minecraft:fire_aspect" -> {
                    out.merge(a.FIRE, half).merge(a.AVERSION, half);
                }
                case "minecraft:fire_protection" -> {
                    out.merge(a.PROTECT, half).merge(a.FIRE, half);
                }
                case "minecraft:flame" -> out.merge(a.FIRE, scaled);
                case "minecraft:fortune", "minecraft:looting",
                        "minecraft:luck_of_the_sea" -> out.merge(a.DESIRE, scaled);
                case "minecraft:infinity", "minecraft:mending" -> out.merge(a.CRAFT, scaled);
                case "minecraft:knockback", "minecraft:punch",
                        "minecraft:respiration" -> out.merge(a.AIR, scaled);
                case "minecraft:power", "minecraft:sharpness",
                        "minecraft:thorns" -> out.merge(a.AVERSION, scaled);
                case "minecraft:protection", "minecraft:projectile_protection"
                        -> out.merge(a.PROTECT, scaled);
                case "minecraft:silk_touch" -> out.merge(a.EXCHANGE, scaled);
                case "minecraft:smite" -> {
                    out.merge(a.UNDEAD, half).merge(a.AVERSION, half);
                }
                case "minecraft:unbreaking" -> out.merge(a.EARTH, scaled);
                case "minecraft:depth_strider" -> out.merge(a.WATER, scaled);
                case "minecraft:lure" -> out.merge(a.BEAST, scaled);
                case "minecraft:frost_walker" -> out.merge(a.COLD, scaled);
                // No specific aspect in BETA26 for Sweeping or either curse,
                // though rarity and level still contribute to MAGIC.
                default -> { }
            }

            Rarity vanilla = oldVanillaRarity(e.id());
            Rarity rarity = vanilla == null ? e.rarity() : vanilla;
            magic += scaled + rarity.magicBonus();
        }
        if (magic > 0) out.merge(a.MAGIC, magic);
        return out;
    }
}
