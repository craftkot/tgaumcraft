package dev.ehtid.thaumcraft.port.core;

/** Exact arithmetic core of BETA26 CasterManager.getTotalVisDiscount. */
public final class VisDiscountLogic {
    private VisDiscountLogic() {}

    /**
     * @param gearPercent sum of every IVisDiscountGear percentage from baubles and four armor slots
     * @param visExhaustAmplifier null when PotionVisExhaust is absent
     * @param infectiousAmplifier null when PotionInfectiousVisExhaust is absent
     */
    public static float totalDiscount(int gearPercent, Integer visExhaustAmplifier,
                                      Integer infectiousAmplifier) {
        int total = gearPercent;
        if (visExhaustAmplifier != null || infectiousAmplifier != null) {
            // BETA26 initializes both local amplifier variables to zero, so an
            // absent sibling effect still participates as amplifier 0 in max().
            int normal = visExhaustAmplifier == null ? 0 : visExhaustAmplifier;
            int infectious = infectiousAmplifier == null ? 0 : infectiousAmplifier;
            total -= (Math.max(normal, infectious) + 1) * 10;
        }
        // Deliberately unclamped: negative discounts and values > 1 are legacy behavior.
        return total / 100.0F;
    }
}
