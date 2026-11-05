package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 craftCycle:1152..1456 searches every remaining ingredient and pedestal.
 * A missing ingredient can be skipped; the single itemCount belongs to the
 * ritual and survives selecting another available ingredient. World adapters
 * validate the selected physical stack before advancing or recording removal.
 */
public record LegacyInfusionIngredientQueue(int ingredientCount, int consumedMask,
                                           int countdown, int activeIndex) {
    public LegacyInfusionIngredientQueue {
        if (ingredientCount < 1 || ingredientCount > 30 || countdown < 0 || countdown > 5)
            throw new IllegalArgumentException("Invalid infusion ingredient count or countdown");
        int fullMask = (1 << ingredientCount) - 1;
        if ((consumedMask & ~fullMask) != 0)
            throw new IllegalArgumentException("Ingredient mask exceeds recipe");
        if (consumedMask == fullMask) {
            if (countdown != 0 || activeIndex != -1)
                throw new IllegalArgumentException("Completed ingredient queue still active");
        } else if (activeIndex < 0 || activeIndex >= ingredientCount
                || (consumedMask & (1 << activeIndex)) != 0) {
            throw new IllegalArgumentException("Selected ingredient is missing or already consumed");
        }
    }

    /** Legacy checkpoints did not persist a selection; retain their first unpaid index. */
    public LegacyInfusionIngredientQueue(int count, int mask, int countdown) {
        this(count, mask, countdown, firstUnconsumed(count, mask));
    }

    public enum Action { READY, SOURCE_EFFECT, COUNTDOWN, CONSUME }

    public static LegacyInfusionIngredientQueue initial(int count) {
        return new LegacyInfusionIngredientQueue(count, 0, 0);
    }

    public static LegacyInfusionIngredientQueue restore(int count, int mask, int countdown) {
        return new LegacyInfusionIngredientQueue(count, mask, countdown);
    }

    public static LegacyInfusionIngredientQueue restore(int count, int mask, int countdown, int activeIndex) {
        return new LegacyInfusionIngredientQueue(count, mask, countdown, activeIndex);
    }

    public static LegacyInfusionIngredientQueue restore(int count, int mask, int countdown, Integer activeIndex) {
        return activeIndex == null ? restore(count, mask, countdown)
                : restore(count, mask, countdown, activeIndex.intValue());
    }

    public boolean readyToFinish() { return consumedMask == (1 << ingredientCount) - 1; }

    public int nextIngredientBit() { return readyToFinish() ? 0 : 1 << activeIndex; }

    public Action nextAction() {
        return readyToFinish() ? Action.READY : countdown == 0 ? Action.SOURCE_EFFECT
                : countdown == 1 ? Action.CONSUME : Action.COUNTDOWN;
    }

    /** Select a still-unpaid ingredient without restarting the shared source countdown. */
    public LegacyInfusionIngredientQueue select(int index) {
        if (index < 0 || index >= ingredientCount)
            throw new IllegalArgumentException("Ingredient index outside recipe");
        if ((consumedMask & (1 << index)) != 0)
            throw new IllegalStateException("Ingredient already consumed");
        return index == activeIndex ? this : new LegacyInfusionIngredientQueue(
                ingredientCount, consumedMask, countdown, index);
    }

    public LegacyInfusionIngredientQueue afterValidatedCountdown() {
        if (nextAction() != Action.SOURCE_EFFECT && nextAction() != Action.COUNTDOWN)
            throw new IllegalStateException("Not counting down a validated ingredient");
        return new LegacyInfusionIngredientQueue(ingredientCount, consumedMask,
                countdown == 0 ? 5 : countdown - 1, activeIndex);
    }

    /** Commit one selected bit only after the pedestal has durably recorded its real removal. */
    public LegacyInfusionIngredientQueue afterActualPedestalRemoval(int bit) {
        if (nextAction() != Action.CONSUME || bit != nextIngredientBit())
            throw new IllegalStateException("Wrong ingredient, replay or incomplete countdown");
        int updated = consumedMask | bit;
        return new LegacyInfusionIngredientQueue(ingredientCount, updated, 0,
                firstUnconsumed(ingredientCount, updated));
    }

    /**
     * Verify every previous payment before acknowledging one stale selected
     * consume. A bare missing unpaid stack is allowed, and never counts as paid.
     * Receipt booleans must already prove the ritual UUID, ingredient bit and
     * physical saved result; this class does not authenticate world receipts.
     */
    public LegacyInfusionIngredientQueue recoverSavedReceipts(boolean[] receipts, boolean[] originalsPresent) {
        if (receipts == null || originalsPresent == null
                || receipts.length != ingredientCount || originalsPresent.length != ingredientCount)
            throw new IllegalArgumentException("Wrong physical pedestal count");
        int receiptMask = 0;
        for (int index = 0; index < ingredientCount; index++) {
            int bit = 1 << index;
            if (receipts[index] && originalsPresent[index])
                throw new IllegalStateException("Receipt contradicts original item still present");
            if ((consumedMask & bit) != 0 && !receipts[index])
                throw new IllegalStateException("Previously consumed ingredient has no saved receipt");
            if (receipts[index]) receiptMask |= bit;
        }
        int newlyReceived = receiptMask & ~consumedMask;
        if (newlyReceived == 0) return this;
        if (nextAction() != Action.CONSUME || newlyReceived != nextIngredientBit())
            throw new IllegalStateException("Receipt was not the single pending selected removal");
        return afterActualPedestalRemoval(newlyReceived);
    }

    private static int firstUnconsumed(int count, int mask) {
        if (count < 1 || count > 30)
            throw new IllegalArgumentException("Invalid infusion ingredient count");
        for (int index = 0; index < count; index++)
            if ((mask & (1 << index)) == 0) return index;
        return -1;
    }
}
