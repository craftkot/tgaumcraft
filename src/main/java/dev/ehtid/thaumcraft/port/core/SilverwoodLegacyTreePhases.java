package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Random;

/**
 * Coordinates Silverwood write phases without resetting or splitting legacy RNG.
 * The archived generator consumes canopy rolls before the log/root rolls.
 *
 * This is an integration seam, not a NeoForge Feature or a canopy implementation.
 * The canopy callback must implement source-verified leaf writes separately.
 */
public final class SilverwoodLegacyTreePhases {
    private SilverwoodLegacyTreePhases() {}

    @FunctionalInterface
    public interface CanopyPhase {
        void place(int x, int y, int z, int height, Random random);
    }

    /**
     * No writes or RNG consumption occur for invalid arguments.
     * World/soil/replacement checks must be completed by the caller first.
     */
    public static void place(int x, int y, int z, int height, Random random,
                             CanopyPhase canopy, SilverwoodLegacyLogs.LogWriter logs) {
        if (!SilverwoodLegacyFootprint.validHeight(height)) {
            throw new IllegalArgumentException("Invalid Silverwood height");
        }
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(canopy, "canopy");
        Objects.requireNonNull(logs, "logs");
        canopy.place(x, y, z, height, random);
        SilverwoodLegacyLogs.place(x, y, z, height, random, logs);
    }
}
