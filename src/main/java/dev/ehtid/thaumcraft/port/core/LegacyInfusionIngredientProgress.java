package dev.ehtid.thaumcraft.port.core;

/** Ordered source five-cycle ingestion with one physical receipt per ingredient. */
public record LegacyInfusionIngredientProgress(int ingredientCount, int consumedMask, int countdown) {
    public LegacyInfusionIngredientProgress {
        if (ingredientCount < 1 || ingredientCount > 30 || consumedMask < 0
                || consumedMask > (1 << ingredientCount) - 1
                || (consumedMask & (consumedMask + 1)) != 0
                || countdown < 0 || countdown > 5
                || (Integer.bitCount(consumedMask) == ingredientCount && countdown != 0))
            throw new IllegalArgumentException("Invalid ordered infusion progress");
    }
    public enum Action { READY, SOURCE_EFFECT, COUNTDOWN, CONSUME }
    public static LegacyInfusionIngredientProgress initial(int count) { return new LegacyInfusionIngredientProgress(count,0,0); }
    public boolean readyToFinish() { return Integer.bitCount(consumedMask) == ingredientCount; }
    public int nextIngredientBit() { return readyToFinish() ? 0 : 1 << Integer.bitCount(consumedMask); }
    public Action nextAction() { return readyToFinish() ? Action.READY : countdown == 0 ? Action.SOURCE_EFFECT : countdown == 1 ? Action.CONSUME : Action.COUNTDOWN; }
    public LegacyInfusionIngredientProgress afterValidatedCountdown() {
        if (nextAction() != Action.SOURCE_EFFECT && nextAction() != Action.COUNTDOWN)
            throw new IllegalStateException("Not counting down");
        return new LegacyInfusionIngredientProgress(ingredientCount,consumedMask,countdown == 0 ? 5 : countdown - 1);
    }
    public LegacyInfusionIngredientProgress afterActualPedestalRemoval(int bit) {
        if (bit != nextIngredientBit() || nextAction() != Action.CONSUME)
            throw new IllegalStateException("Wrong ingredient, replay or incomplete countdown");
        return new LegacyInfusionIngredientProgress(ingredientCount,consumedMask | bit,0);
    }
    /** Verify every original slot before acknowledging ANY stale progress. No partial recovery. */
    public LegacyInfusionIngredientProgress recoverSavedReceipts(boolean[] receipts, boolean[] originalsPresent) {
        if (receipts.length != ingredientCount || originalsPresent.length != ingredientCount)
            throw new IllegalArgumentException("Wrong physical pedestal count");
        int mask = 0;
        boolean reachedUnpaid = false;
        for (int i = 0; i < ingredientCount; i++) {
            if (receipts[i] == originalsPresent[i] || (reachedUnpaid && receipts[i]))
                throw new IllegalStateException("Missing, contradictory or unordered payment");
            if (receipts[i]) mask |= 1 << i;
            else reachedUnpaid = true;
        }
        if ((mask & consumedMask) != consumedMask)
            throw new IllegalStateException("Previously paid ingredient restored without receipt");
        return mask == consumedMask ? this : new LegacyInfusionIngredientProgress(ingredientCount,mask,0);
    }
}
