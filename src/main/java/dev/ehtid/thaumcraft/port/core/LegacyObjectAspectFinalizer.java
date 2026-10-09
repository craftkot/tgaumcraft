package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Thaumcraft 6.1.BETA26 AspectHelper.cullTags(AspectList) followed by
 * ThaumcraftCraftingManager.capAspects(AspectList, 500).
 *
 * The bytecode removes the aspect with the LOWEST weighted amount while there
 * are more than seven distinct entries. Primal aspects receive x0.9 weight;
 * a compound's immediate non-primal components increase its weight x1.1,
 * with another x1.05 for each second-level non-primal component. Comparisons
 * use strict float less-than, so ties remove the earliest inserted aspect.
 *
 * All score calculations and insertion ordering are intentionally preserved.
 */
public final class LegacyObjectAspectFinalizer {
    private LegacyObjectAspectFinalizer() {}

    public static AspectListData finish(AspectListData input) {
        return finish(input, 7, 500);
    }

    public static AspectListData finish(AspectListData input, int maxTypes, int maxAmount) {
        Objects.requireNonNull(input, "input");
        if (maxTypes < 0) throw new IllegalArgumentException("maxTypes");
        AspectListData culled = input.copy();
        while (culled.size() > maxTypes) {
            AspectData least = null;
            // Original BETA26 initializes this to 32767f. If 8+ aspects
            // each have a weighted count >=32767, NONE is selected and the
            // old while-loop never advances. The port can safely start at
            // +infinity: ordinary <32767 data produces IDENTICAL selections,
            // while unusually large/modded/corrupt aspect lists terminate.
            float lowest = Float.POSITIVE_INFINITY;
            for (AspectData aspect : culled.getAspects()) {
                if (aspect == null) continue;
                float score = culled.getAmount(aspect);
                if (aspect.isPrimal()) {
                    score *= 0.9F;
                } else {
                    List<AspectData> firstLevel = aspect.components();
                    // Exact nonrecursive, two-level factor tree from BETA26.
                    for (int i = 0; i < 2; i++) {
                        AspectData component = firstLevel.get(i);
                        if (!component.isPrimal()) {
                            score *= 1.1F;
                            List<AspectData> secondLevel = component.components();
                            if (!secondLevel.get(0).isPrimal()) score *= 1.05F;
                            if (!secondLevel.get(1).isPrimal()) score *= 1.05F;
                        }
                    }
                }
                if (score < lowest) {
                    lowest = score;
                    least = aspect;
                }
            }
            if (least == null) {
                // Do not kill the server if a malformed external aspect
                // definition supplies NaN scores for every candidate.
                // Consistent fallback: drop the first registered entry.
                least = culled.getAspects()[0];
            }
            culled.remove(least);
        }

        AspectListData result = new AspectListData();
        for (AspectData aspect : culled.getAspects()) {
            result.merge(aspect, Math.min(maxAmount, culled.getAmount(aspect)));
        }
        return result;
    }
}
