package dev.ehtid.thaumcraft.port.core;

import java.util.UUID;

/**
 * Provenance of the physically published Crystal Farmer output.
 * A real center pedestal writes this receipt in the SAME BlockEntity
 * snapshot as the output ItemStack, never separately in the matrix.
 *
 * This proves a previously published result only; it does NOT authorize
 * a new output, refund costs, or make separate chunks save atomically.
 */
public record LegacyCrystalFarmerOutputReceipt(UUID ritualId, String itemId) {
    public LegacyCrystalFarmerOutputReceipt {
        if (ritualId == null || itemId == null || itemId.isBlank())
            throw new IllegalArgumentException("Invalid output receipt");
    }

    public boolean provesSavedPublication(UUID expectedRitual, String expectedItemId,
                                          boolean actualOneItemOutputPresent) {
        return actualOneItemOutputPresent
                && ritualId.equals(expectedRitual)
                && itemId.equals(expectedItemId);
    }
}
