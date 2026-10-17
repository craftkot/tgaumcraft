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

    /** Commit after a real pedestal emptied successfully; never before. */
    public LegacyCrystalFarmerIngredientProgress afterActualPedestalRemoval(int itemBit) {
        if (itemBit != nextIngredientBit() || nextAction() != Action.CONSUME)
            throw new IllegalStateException("Replay, wrong item or unfinished countdown");
        return new LegacyCrystalFarmerIngredientProgress(consumedMask | itemBit, 0);
    }
}
