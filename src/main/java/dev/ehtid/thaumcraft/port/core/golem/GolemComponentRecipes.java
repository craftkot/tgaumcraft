package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;

/** BETA26 ConfigRecipes: paid arcane golem mechanisms, clockwork minds and modules. */
public final class GolemComponentRecipes {
    private GolemComponentRecipes() {}
    public enum Ingredient {
        EMPTY, BRASS_PLATE, IRON_PLATE, WOODEN_ROD, GLASS_PANE, SIMPLE_MECHANISM, COMPARATOR,
        GLASS_BOTTLE, FERMENTED_SPIDER_EYE, BLAZE_POWDER, THAUMIUM_PLATE, PISTON, WOODEN_PLANK, NITOR
    }
    public static int bit(Ingredient ingredient) { return 1 << ingredient.ordinal(); }

    public enum Recipe {
        SIMPLE_MECHANISM("BASEARTIFICE", 10, 0b000110, List.of(
                Ingredient.EMPTY, Ingredient.BRASS_PLATE, Ingredient.EMPTY,
                Ingredient.IRON_PLATE, Ingredient.WOODEN_ROD, Ingredient.IRON_PLATE,
                Ingredient.EMPTY, Ingredient.BRASS_PLATE, Ingredient.EMPTY)),
        COMPLEX_MECHANISM("BASEARTIFICE", 50, 0b000110, List.of(
                Ingredient.EMPTY, Ingredient.SIMPLE_MECHANISM, Ingredient.EMPTY,
                Ingredient.THAUMIUM_PLATE, Ingredient.PISTON, Ingredient.THAUMIUM_PLATE,
                Ingredient.EMPTY, Ingredient.SIMPLE_MECHANISM, Ingredient.EMPTY)),
        CLOCKWORK_MIND("MINDCLOCKWORK@2", 25, 0b010010, List.of(
                Ingredient.EMPTY, Ingredient.GLASS_PANE, Ingredient.EMPTY,
                Ingredient.GLASS_PANE, Ingredient.SIMPLE_MECHANISM, Ingredient.GLASS_PANE,
                Ingredient.BRASS_PLATE, Ingredient.COMPARATOR, Ingredient.BRASS_PLATE)),
        VISION_MODULE("GOLEMVISION", 50, 0b000100, List.of(
                Ingredient.GLASS_BOTTLE, Ingredient.EMPTY, Ingredient.GLASS_BOTTLE,
                Ingredient.FERMENTED_SPIDER_EYE, Ingredient.EMPTY, Ingredient.FERMENTED_SPIDER_EYE,
                Ingredient.BRASS_PLATE, Ingredient.SIMPLE_MECHANISM, Ingredient.BRASS_PLATE)),
        LEVITATOR("LEVITATOR", 35, 0b000001, List.of(
                Ingredient.WOODEN_PLANK, Ingredient.THAUMIUM_PLATE, Ingredient.WOODEN_PLANK,
                Ingredient.IRON_PLATE, Ingredient.NITOR, Ingredient.IRON_PLATE,
                Ingredient.WOODEN_PLANK, Ingredient.SIMPLE_MECHANISM, Ingredient.WOODEN_PLANK)),
        AGGRESSION_MODULE("SEALGUARD", 50, 0b000010, List.of(
                Ingredient.EMPTY, Ingredient.GLASS_PANE, Ingredient.EMPTY,
                Ingredient.GLASS_PANE, Ingredient.BLAZE_POWDER, Ingredient.GLASS_PANE,
                Ingredient.BRASS_PLATE, Ingredient.SIMPLE_MECHANISM, Ingredient.BRASS_PLATE));

        public final String research;
        public final int vis, crystalMask;
        private final List<Ingredient> pattern;
        Recipe(String research, int vis, int crystalMask, List<Ingredient> pattern) {
            this.research = research; this.vis = vis; this.crystalMask = crystalMask; this.pattern = pattern;
        }
        public List<Ingredient> pattern() { return pattern; }
        /** Full 3x3, horizontally symmetric source patterns; no translated 2x3 shortcut. */
        public boolean matches(int[] ingredientMasks) {
            if (ingredientMasks == null || ingredientMasks.length != 9) return false;
            for (int slot = 0; slot < 9; slot++)
                if ((ingredientMasks[slot] & bit(pattern.get(slot))) == 0) return false;
            return true;
        }
        public int discountedVis(float discount) {
            if (!Float.isFinite(discount) || discount < 0 || discount > 1)
                throw new IllegalArgumentException("Invalid arcane discount");
            return (int) (vis * (1F - discount));
        }
    }
}
