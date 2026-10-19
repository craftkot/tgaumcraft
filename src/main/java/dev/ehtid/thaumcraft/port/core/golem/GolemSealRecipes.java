package dev.ehtid.thaumcraft.port.core.golem;

import dev.ehtid.thaumcraft.port.core.*;
import java.util.List;

/** ConfigRecipes BETA26: SealBlank arcane, SealCollect/SealStore crucible. */
public final class GolemSealRecipes {
    private GolemSealRecipes() {}
    public static final String BLANK_RESEARCH = "CONTROLSEALS";
    public static final int VIS = 20, BLANK_OUTPUT = 3, ESSENTIA = 10;
    public enum Ingredient { EMPTY, CLAY, TALLOW, RED_DYE, NITOR, OTHER }
    public enum Catalyst { BLANK, EMPTY, FILL, EMPTY_ADVANCED, PICKUP }
    public enum Conversion {
        PICKUP("SEALCOLLECT",Catalyst.BLANK,"desiderium"),
        PICKUP_ADVANCED("SEALCOLLECT&&MINDBIOTHAUMIC",Catalyst.PICKUP,"sensus","cognitio"),
        FILL("SEALSTORE",Catalyst.BLANK,"aversio"),
        FILL_ADVANCED("SEALSTORE&&MINDBIOTHAUMIC",Catalyst.FILL,"sensus","cognitio"),
        EMPTY("SEALEMPTY",Catalyst.BLANK,"vacuos"),
        EMPTY_ADVANCED("SEALEMPTY&&MINDBIOTHAUMIC",Catalyst.EMPTY,"sensus","cognitio"),
        PROVIDE("SEALPROVIDE",Catalyst.EMPTY_ADVANCED,"permutatio","desiderium"),
        STOCK("SEALSTOCK",Catalyst.FILL,"cognitio","desiderium");
        public final String research;
        public final Catalyst catalyst;
        public final List<String> aspects;
        Conversion(String research,Catalyst catalyst,String... aspects) {
            this.research=research; this.catalyst=catalyst; this.aspects=List.of(aspects);
        }
        public AspectData aspect(AspectRegistry registry) {
            return registry.get(aspects.getFirst());
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
        return conversion(Catalyst.BLANK,knowledge,stored,registry);
    }

    public static Conversion conversion(Catalyst catalyst,PlayerKnowledgeData knowledge, AspectListData stored, AspectRegistry registry) {
        if (knowledge == null) return null;
        for (Conversion recipe : Conversion.values())
            if (recipe.catalyst==catalyst && java.util.Arrays.stream(recipe.research.split("&&")).allMatch(knowledge::isResearchKnown)
                    && recipe.aspects.stream().allMatch(tag->amount(stored,tag)>=ESSENTIA))
                return recipe;
        return null; // Same-catalyst basic recipes cost10: insertion order wins ties.
    }

    public static long amount(AspectListData stored, String tag) {
        long total = 0;
        for (AspectData aspect : stored.getAspects()) if (aspect.tag().equals(tag)) total += stored.getAmount(aspect);
        return total;
    }

    public static AspectListData afterCraft(AspectListData stored, Conversion recipe, AspectRegistry registry) {
        if(recipe.aspects.stream().anyMatch(tag->amount(stored,tag)<ESSENTIA))
            throw new IllegalArgumentException("Missing seal essentia");
        var result = stored.copy();
        for(String tag:recipe.aspects) {
        int remaining = ESSENTIA;
        // Runtime scanners and tiles use separate registries; canonical identity is the legacy aspect tag.
        for (AspectData aspect : stored.getAspects()) {
            if (!aspect.tag().equals(tag)) continue;
            int debit = Math.min(remaining, stored.getAmount(aspect));
            result.remove(aspect, debit); remaining -= debit;
            if (remaining == 0) break;
        }
        }
        return result;
    }
}
