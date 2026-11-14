package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Exact physical evidence that ONE essentia was debited from an original
 * server-side jar. For an emptied jar the original jar logic clears its
 * aspect to null; its filter remains unchanged.
 *
 * This is an in-memory, one-tick verification only, NOT a durable cross-chunk
 * receipt or a guarantee of atomic world saves.
 */
public final class LegacyInfusionJarDebitEvidence {
    private LegacyInfusionJarDebitEvidence() {}

    public static boolean exactlyOne(String requestedAspect,
                                     LegacyEssentiaJarLogic.State before,
                                     LegacyEssentiaJarLogic.State after,
                                     boolean jarReportedSuccess) {
        if (!jarReportedSuccess || requestedAspect == null
                || requestedAspect.isBlank() || before == null || after == null)
            return false;
        if (!Objects.equals(before.aspect(), requestedAspect)
                || before.amount() <= 0 || after.amount() != before.amount() - 1
                || !Objects.equals(before.filter(), after.filter()))
            return false;
        return Objects.equals(after.aspect(),
                after.amount() == 0 ? null : requestedAspect);
    }
}
