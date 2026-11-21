package dev.ehtid.thaumcraft.port.core;

/** Exact BETA26 AuraThread Flux-rift trigger threshold and probability. */
public final class LegacyFluxRiftTriggerLogic {
    private LegacyFluxRiftTriggerLogic() {}

    /**
     * BETA26 queues a rift candidate only above 75% of the phase-adjusted base,
     * with a strict random roll below currentFlux / 500 / 10.
     */
    public static boolean shouldTrigger(float phaseAdjustedBase, float currentFlux, float randomRoll) {
        return currentFlux > phaseAdjustedBase * 0.75F
                && randomRoll < currentFlux / 500.0F / 10.0F;
    }
}
