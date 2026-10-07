package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Loader-independent TC6 research category descriptor.
 *
 * formula/applyFormula mirrors ResearchCategory.applyFormula and is used by generic scanning to turn scanned
 * aspects into category-specific Observation knowledge.
 */
public record ResearchCategoryData(
        String key,
        String titleKey,
        String icon,
        String background,
        String background2,
        String researchKey,
        AspectListData formula) {

    public ResearchCategoryData {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(icon, "icon");
    }

    /** Compatibility constructor retained for loader/UI-only descriptors. */
    public ResearchCategoryData(String key, String titleKey, String icon, String background, String background2) {
        this(key, titleKey, icon, background, background2, null, null);
    }

    public int applyFormula(AspectListData scannedAspects) {
        return applyFormula(scannedAspects, 1.0D);
    }

    /** Exact arithmetic of BETA26 ResearchCategory.applyFormula(AspectList,double). */
    public int applyFormula(AspectListData scannedAspects, double multiplier) {
        if (formula == null) return 0;
        double total = 0.0D;
        for (AspectData aspect : formula.getAspects()) {
            total += multiplier * multiplier
                    * scannedAspects.getAmount(aspect)
                    * (formula.getAmount(aspect) / 10.0D);
        }
        if (total > 0.0D) total = Math.sqrt(total);
        return (int) Math.ceil(total);
    }
}
