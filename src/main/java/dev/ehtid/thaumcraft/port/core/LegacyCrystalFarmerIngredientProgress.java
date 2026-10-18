package dev.ehtid.thaumcraft.port.core;

/**
 * Two original BETA26 Crystal Farmer pedestal inputs:
 * wheat seeds followed by Salis Mundus. The generic BETA26
 * LegacyInfusionIngredientCycle applies one shared five-cycle countdown
 * before removal. This immutable progress record is the source-era
 * Crystal Farmer recipe's restart-safe specialization.
 *
 * This class NEVER removes an item itself. The world adapter must validate
 * the real pedestal ItemStack and commit a one-item replacement BEFORE
 * calling afterActualPedestalRemoval, then persist the returned state.
 */
public record LegacyCrystalFarmerIngredientProgress(int consumedMask, int countdown) {
    public static final int SEEDS_BIT = 1;
    public static final int SALIS_BIT = 2;
    public static final int ALL = SEEDS_BIT | SALIS_BIT;
    public static final int ORIGINAL_COUNTDOWN = 5;

    public LegacyCrystalFarmerIngredientProgress {
        if ((consumedMask & ~ALL) != 0 || consumedMask < 0
                // Source order is seeds first, Salis Mundus second.
                // A saved mask containing only Salis would skip a real cost.
                || consumedMask == SALIS_BIT
                || countdown < 0 || countdown > ORIGINAL_COUNTDOWN
                || (consumedMask == ALL && countdown != 0))
            throw new IllegalArgumentException("Invalid original ingredient progress");
    }

    public static LegacyCrystalFarmerIngredientProgress initial() {
        return new LegacyCrystalFarmerIngredientProgress(0, 0);
    }

    public enum Action { READY, SOURCE_EFFECT, COUNTDOWN, CONSUME }

    /** Source order follows the BETA26 recipe's listed ingredients. */
    public int nextIngredientBit() {
        if ((consumedMask & SEEDS_BIT) == 0) return SEEDS_BIT;
        if ((consumedMask & SALIS_BIT) == 0) return SALIS_BIT;
        return 0;
    }

    /**
     * A world save is not atomic across the matrix and side pedestals.
     * If the matrix has persisted an ingredient debit but the pedestal
     * has restored its old item, allowing the ritual to finish would mint
     * an output while leaving that paid ingredient behind.
     *
     * Fail closed on either discrepancy: consumed items must be absent,
     * outstanding items must still be present. Legitimate insertion of an
     * extra matching item during an active ritual also pauses progression
     * until the extra is removed; never treats it as a free ingredient.
     */
    public boolean matchesLivePedestals(boolean seedsPresent, boolean salisPresent) {
        return (((consumedMask & SEEDS_BIT) == 0) == seedsPresent)
                && (((consumedMask & SALIS_BIT) == 0) == salisPresent);
    }

    /**
     * The matrix's paid mask is never sufficient evidence on its own:
     * a separately saved pedestal may be empty for an unrelated reason,
     * or have an OLD receipt from another ritual. Every consumed input
     * must have its own current ritual UUID receipt in the live pedestal.
     */
    public boolean matchesSavedReceipts(boolean seedReceiptMatches,
                                        boolean salisReceiptMatches) {
        return (((consumedMask & SEEDS_BIT) != 0) == seedReceiptMatches)
                && (((consumedMask & SALIS_BIT) != 0) == salisReceiptMatches);
    }

    public boolean readyToFinish() { return consumedMask == ALL; }

    public Action nextAction() {
        if (readyToFinish()) return Action.READY;
        if (countdown == 0) return Action.SOURCE_EFFECT;
        if (countdown == 1) return Action.CONSUME;
        return Action.COUNTDOWN;
    }

    /** Call only when the expected live pedestal item still exists. */
    public LegacyCrystalFarmerIngredientProgress afterValidatedCountdown() {
        return switch (nextAction()) {
            case SOURCE_EFFECT -> new LegacyCrystalFarmerIngredientProgress(consumedMask,
                    ORIGINAL_COUNTDOWN);
            case COUNTDOWN -> new LegacyCrystalFarmerIngredientProgress(consumedMask,
                    countdown - 1);
            default -> throw new IllegalStateException("Cannot tick consumed or missing item");
        };
    }

    /**
     * A pedestal can save its consumed input plus a ritual-specific receipt
     * while the matrix still has ANY earlier countdown (0..5), since the
     * different chunks persist independently. The matching receipt and
     * physically empty original pedestal are authoritative evidence that
     * consumption already happened; do not debit it twice or replay a bit.
     *
     * This advances ONLY the next outstanding ingredient and only when the
     * server has independently verified the original pedestal/ritual ID.
     */
    public LegacyCrystalFarmerIngredientProgress afterSavedPedestalReceipt(
            int itemBit, boolean exactRitualReceipt, boolean originalSlotEmpty) {
        if (!exactRitualReceipt || !originalSlotEmpty || itemBit != nextIngredientBit()
                || readyToFinish())
            throw new IllegalStateException("Unverified persisted pedestal removal");
        return new LegacyCrystalFarmerIngredientProgress(consumedMask | itemBit, 0);
    }

    /** Commit after a real pedestal emptied successfully; never before. */
    public LegacyCrystalFarmerIngredientProgress afterActualPedestalRemoval(int itemBit) {
        if (itemBit != nextIngredientBit() || nextAction() != Action.CONSUME)
            throw new IllegalStateException("Replay, wrong item or unfinished countdown");
        return new LegacyCrystalFarmerIngredientProgress(consumedMask | itemBit, 0);
    }
}
