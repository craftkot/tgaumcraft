package dev.ehtid.thaumcraft.port.core;

import java.util.UUID;

/**
 * Restart receipt for ONE actual infusion side-pedestal removal.
 *
 * The live PedestalBlockEntity writes this immutable identity and its newly
 * empty inventory slot into the SAME block entity save. The matrix can then
 * acknowledge a missing progress-mask update, but must not remove any item
 * again. This does not make saves across different block entities atomic.
 */
public record LegacyInfusionPedestalReceipt(UUID ritualId, int ingredientBit) {
    public LegacyInfusionPedestalReceipt {
        if (ritualId == null)
            throw new IllegalArgumentException("Missing ritual id");
        if (ingredientBit <= 0 || ingredientBit > (1 << 29) || Integer.bitCount(ingredientBit) != 1)
            throw new IllegalArgumentException("Unknown side ingredient");
    }

    /** Reject a different ritual, a different ingredient, or a nonempty slot. */
    public boolean provesSavedRemoval(UUID activeRitual, int expectedBit,
                                      boolean realPedestalEmpty) {
        return realPedestalEmpty && ritualId.equals(activeRitual)
                && ingredientBit == expectedBit;
    }
}
