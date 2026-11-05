package dev.ehtid.thaumcraft.port.core;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/** Exact BETA26 ConfigRecipes arcane glyph, stabilizer and shapeless inlay definitions. */
public final class LegacyInfusionStabilityRecipes {
    private LegacyInfusionStabilityRecipes() {}

    public enum Kind {
        SPEED("INFUSIONBOOST", 500, 0b010001, "matrix_speed", 1, "SNS", "NGN", "SNS"),
        COST("INFUSIONBOOST", 500, 0b100101, "matrix_cost", 1, "SAS", "AGA", "SAS"),
        STABILIZER("INFUSIONSTABLE", 250, 0b101100, "stabilizer", 1, "SRS", "BVB", "IMI"),
        INLAY("INFUSIONSTABLE", 25, 0b000100, "inlay", 2),
        MIRRORED_GLASS("BASEARTIFICE", 50, 0b010100, "mirrored_glass", 1);

        public final String research;
        public final int vis;
        public final int crystalMask;
        public final String outputPath;
        public final int outputCount;
        private final String[] pattern;

        Kind(String research, int vis, int crystalMask, String outputPath, int outputCount, String... pattern) {
            this.research = research;
            this.vis = vis;
            this.crystalMask = crystalMask;
            this.outputPath = outputPath;
            this.outputCount = outputCount;
            this.pattern = pattern;
        }
    }

    /** Input stacks may have any positive count; a craft consumes one per occupied slot. */
    public static boolean matches(Kind kind, BiPredicate<Integer, Character> ingredient, IntPredicate empty) {
        if (kind == null || ingredient == null || empty == null) return false;
        if (kind == Kind.INLAY || kind == Kind.MIRRORED_GLASS) {
            // Each source ShapelessArcaneRecipe has exactly two distinct inputs.
            for (int red = 0; red < 9; red++) for (int gold = 0; gold < 9; gold++) {
                if (red == gold || !ingredient.test(red, 'R') || !ingredient.test(gold, 'G')) continue;
                boolean otherEmpty = true;
                for (int slot = 0; slot < 9; slot++) if (slot != red && slot != gold && !empty.test(slot)) {
                    otherEmpty = false;
                    break;
                }
                if (otherEmpty) return true;
            }
            return false;
        }
        for (boolean mirror : new boolean[]{false, true}) {
            boolean matching = true;
            for (int slot = 0; slot < 9; slot++) {
                char token = kind.pattern[slot / 3].charAt(mirror ? 2 - slot % 3 : slot % 3);
                if (!ingredient.test(slot, token)) { matching = false; break; }
            }
            if (matching) return true;
        }
        return false;
    }

    public static int discountedVis(Kind kind, float discount) {
        if (kind == null || !Float.isFinite(discount)) throw new IllegalArgumentException("kind/discount");
        return Math.max(0, (int) (kind.vis * (1.0F - discount)));
    }
}
