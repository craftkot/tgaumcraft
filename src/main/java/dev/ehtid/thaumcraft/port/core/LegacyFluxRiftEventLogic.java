package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 EntityFluxRift instability-event trigger and RandomItemChooser weights.
 */
public final class LegacyFluxRiftEventLogic {
    private static final double TOTAL_WEIGHT = 101.0D;

    private LegacyFluxRiftEventLogic() {}

    /**
     * BETA26 attempts an event only below zero stability and when
     * {@code rand.nextInt(1000) < abs(stability) + riftSize}.
     */
    public static boolean shouldAttemptEvent(float stability, int riftSize, int randomRoll) {
        return stability < 0.0F
                && randomRoll >= 0
                && randomRoll < 1000
                && randomRoll < Math.abs(stability) + riftSize;
    }

    /**
     * Selects the original event ids using RandomItemChooser's cumulative
     * {@code countWeight >= Math.random() * totalWeight} comparison.
     *
     * <p>Event ids and weights: Wisp 0/50, Taint Seed 1/10,
     * Infectious Vis Exhaust 2/20, Flux focus 3/20, collapse 4/1.</p>
     */
    public static int selectWeightedEvent(double randomRoll) {
        if (!Double.isFinite(randomRoll) || randomRoll < 0.0D || randomRoll >= 1.0D) {
            throw new IllegalArgumentException("randomRoll must be finite and in [0, 1)");
        }

        double selectedWeight = randomRoll * TOTAL_WEIGHT;
        if (50.0D >= selectedWeight) return 0;
        if (60.0D >= selectedWeight) return 1;
        if (80.0D >= selectedWeight) return 2;
        if (100.0D >= selectedWeight) return 3;
        return 4;
    }
}
