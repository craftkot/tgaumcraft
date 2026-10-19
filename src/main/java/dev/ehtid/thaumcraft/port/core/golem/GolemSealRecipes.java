package dev.ehtid.thaumcraft.port.core.golem;

import dev.ehtid.thaumcraft.port.core.*;
import java.util.List;

/** ConfigRecipes BETA26: SealBlank arcane, SealCollect/SealStore crucible. */
public final class GolemSealRecipes {
    private GolemSealRecipes() {}
    public static final String BLANK_RESEARCH = "CONTROLSEALS";
    public static final int VIS = 20, BLANK_OUTPUT = 3, ESSENTIA = 10;
    public enum Ingredient { EMPTY, CLAY, TALLOW, RED_DYE, NITOR, OTHER }
    public enum Conversion {
        PICKUP("SEALCOLLECT"), FILL("SEALSTORE"), EMPTY("SEALEMPTY");
        public final String research;
        Conversion(String research) { this.research = research; }
        public AspectData aspect(AspectRegistry registry) {
            return switch (this) { case PICKUP -> registry.DESIRE; case FILL -> registry.AVERSION; case EMPTY -> registry.VOID; };
        }
    }

    public static boolean matchesBlank(List<Ingredient> grid) {
        if (grid == null || grid.size() != 9) return false;
        int mask = 0;
        for (Ingredient ingredient : grid) {
            if (ingredient == null || ingredient == Ingredient.OTHER) return false;
            if (ingredient == Ingredient.EMPTY) continue;
            int bit = 1 << ingredient.ordinal();
            if ((mask & bit) != 0) return false;
            mask |= bit;
        }
        return mask == 30;
    }

    public static Conversion conversion(PlayerKnowledgeData knowledge, AspectListData stored, AspectRegistry registry) {
        if (knowledge == null) return null;
        for (Conversion recipe : Conversion.values())
            if (knowledge.isResearchKnown(recipe.research) && amount(stored, recipe.aspect(registry).tag()) >= ESSENTIA)
                return recipe;
        return null; // Both source costs are 10: insertion order wins ties.
    }

    public static long amount(AspectListData stored, String tag) {
        long total = 0;
        for (AspectData aspect : stored.getAspects()) if (aspect.tag().equals(tag)) total += stored.getAmount(aspect);
        return total;
    }

    public static AspectListData afterCraft(AspectListData stored, Conversion recipe, AspectRegistry registry) {
        String tag = recipe.aspect(registry).tag();
        if (amount(stored, tag) < ESSENTIA) throw new IllegalArgumentException("Missing seal essentia");
        var result = stored.copy();
        int remaining = ESSENTIA;
        // Runtime scanners and tiles use separate registries; canonical identity is the legacy aspect tag.
        for (AspectData aspect : stored.getAspects()) {
            if (!aspect.tag().equals(tag)) continue;
            int debit = Math.min(remaining, stored.getAmount(aspect));
            result.remove(aspect, debit); remaining -= debit;
            if (remaining == 0) break;
        }
        return result;
    }
}
