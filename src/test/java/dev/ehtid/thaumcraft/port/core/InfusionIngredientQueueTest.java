package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;
import java.util.Objects;

/** Source craftCycle:1152..1456 searches later ingredients without resetting itemCount. */
public final class InfusionIngredientQueueTest {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual))
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }
    private static void reject(Runnable action) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Forged state or illegal transition accepted");
    }
    private static LegacyInfusionIngredientQueue consuming(int count, int index) {
        var state = LegacyInfusionIngredientQueue.initial(count).select(index);
        for (int tick = 0; tick < 5; tick++) state = state.afterValidatedCountdown();
        eq(LegacyInfusionIngredientQueue.Action.CONSUME, state.nextAction());
        return state;
    }
    private static void skipMissingWithoutRestartingCountdown() {
        var initial = LegacyInfusionIngredientQueue.initial(4);
        eq(0, initial.activeIndex());
        eq(1, initial.nextIngredientBit());
        eq(LegacyInfusionIngredientQueue.Action.SOURCE_EFFECT, initial.nextAction());
        // Source effect for slot0 occurred; it disappears before the next cycle.
        var started = initial.afterValidatedCountdown();
        eq(5, started.countdown());
        var later = started.select(2);
        eq(5, later.countdown());
        eq(2, later.activeIndex());
        eq(LegacyInfusionIngredientQueue.Action.COUNTDOWN, later.nextAction());
        later = later.afterValidatedCountdown().select(3);
        eq(4, later.countdown());
        eq(0, later.consumedMask());
        for (int tick = 0; tick < 3; tick++) later = later.afterValidatedCountdown();
        eq(1, later.countdown());
        // Search can change its chosen item even on the actual consume cycle.
        later = later.select(1);
        var before = later;
        reject(() -> before.afterActualPedestalRemoval(1 << 3));
        later = later.afterActualPedestalRemoval(1 << 1);
        eq(2, later.consumedMask());
        eq(0, later.activeIndex());
        eq(0, later.countdown());
        eq(false, later.readyToFinish());
    }
    private static void missingUnpaidIsNotAReceipt() {
        var state = LegacyInfusionIngredientQueue.restore(4, 2, 3, 3);
        // Paid slot1 still has its receipt. Missing slot0 and slot2 remain unpaid.
        eq(state, state.recoverSavedReceipts(new boolean[]{false,true,false,false},
                new boolean[]{false,false,false,true}));
        eq(2, state.consumedMask());
        eq(3, state.countdown());
        eq(3, state.activeIndex());
        reject(() -> state.recoverSavedReceipts(new boolean[]{false,false,false,false},
                new boolean[]{false,false,false,true}));
        reject(() -> state.recoverSavedReceipts(new boolean[]{false,true,false,false},
                new boolean[]{false,true,false,true}));
        reject(() -> state.recoverSavedReceipts(new boolean[]{false,false,false,false},
                new boolean[]{false,true,false,true}));
    }
    private static void recoverExactlySelectedStaleRemovalOnce() {
        var stale = LegacyInfusionIngredientQueue.restore(5, 10, 1, 4);
        var receipts = new boolean[]{false,true,false,true,true};
        var present = new boolean[]{false,false,true,false,false};
        var recovered = stale.recoverSavedReceipts(receipts, present);
        eq(26, recovered.consumedMask());
        eq(0, recovered.countdown());
        eq(0, recovered.activeIndex());
        eq(recovered, recovered.recoverSavedReceipts(receipts, present));
        reject(() -> recovered.afterActualPedestalRemoval(16));
        reject(() -> recovered.select(4));
        // One genuinely consumed final item can restore READY, with no second debit.
        var finalStale = LegacyInfusionIngredientQueue.restore(3, 5, 1, 1);
        var done = finalStale.recoverSavedReceipts(new boolean[]{true,true,true}, new boolean[3]);
        eq(true, done.readyToFinish());
        eq(-1, done.activeIndex());
        eq(0, done.nextIngredientBit());
        eq(LegacyInfusionIngredientQueue.Action.READY, done.nextAction());
        eq(done, done.recoverSavedReceipts(new boolean[]{true,true,true}, new boolean[3]));
    }
    private static void rejectForgedStaleReceipts() {
        var stale = LegacyInfusionIngredientQueue.restore(4, 2, 1, 3);
        // A saved consume can account for one receipt, for its selected index only.
        reject(() -> stale.recoverSavedReceipts(new boolean[]{true,true,false,false}, new boolean[4]));
        reject(() -> stale.recoverSavedReceipts(new boolean[]{true,true,false,true}, new boolean[4]));
        reject(() -> stale.recoverSavedReceipts(new boolean[]{false,true,true,false}, new boolean[4]));
        reject(() -> stale.recoverSavedReceipts(new boolean[]{false,true,false,true},
                new boolean[]{false,false,false,true}));
        // A saved source effect or countdown is never proof that removal happened.
        for (int countdown : new int[]{0,2,3,4,5}) {
            var premature = LegacyInfusionIngredientQueue.restore(4, 2, countdown, 3);
            reject(() -> premature.recoverSavedReceipts(new boolean[]{false,true,false,true}, new boolean[4]));
        }
        reject(() -> stale.recoverSavedReceipts(new boolean[3], new boolean[4]));
        reject(() -> stale.recoverSavedReceipts(new boolean[4], new boolean[5]));
        reject(() -> stale.recoverSavedReceipts(null, new boolean[4]));
        reject(() -> stale.recoverSavedReceipts(new boolean[4], null));
    }
    private static void thirtyIndependentBitsAndOutOfOrderCompletion() {
        for (int count : new int[]{1,2,3,6,12,30}) {
            var state = LegacyInfusionIngredientQueue.initial(count);
            int expectedMask = 0;
            for (int index = count - 1; index >= 0; index--) {
                state = state.select(index);
                eq(1 << index, state.nextIngredientBit());
                var tooEarly = state;
                reject(() -> tooEarly.afterActualPedestalRemoval(tooEarly.nextIngredientBit()));
                for (int cycle = 0; cycle < 5; cycle++) state = state.afterValidatedCountdown();
                var consume = state;
                reject(consume::afterValidatedCountdown);
                state = state.afterActualPedestalRemoval(1 << index);
                expectedMask |= 1 << index;
                eq(expectedMask, state.consumedMask());
                eq(state, LegacyInfusionIngredientQueue.restore(count, state.consumedMask(),
                        state.countdown(), state.activeIndex()));
            }
            eq((1 << count) - 1, state.consumedMask());
            eq(true, state.readyToFinish());
            var done = state;
            reject(() -> done.afterActualPedestalRemoval(1));
            reject(() -> done.select(0));
            reject(done::afterValidatedCountdown);
            boolean[] receipts = new boolean[count];
            Arrays.fill(receipts, true);
            eq(done, done.recoverSavedReceipts(receipts, new boolean[count]));
        }
        var high = consuming(30, 29).afterActualPedestalRemoval(1 << 29);
        eq(1 << 29, high.consumedMask());
        eq(0, high.activeIndex());
    }
    private static void restoreLegacyAndValidateState() {
        eq(2, LegacyInfusionIngredientQueue.restore(5, 3, 4).activeIndex());
        eq(0, LegacyInfusionIngredientQueue.restore(5, 10, 4).activeIndex());
        eq(2, LegacyInfusionIngredientQueue.restore(5, 3, 4, (Integer)null).activeIndex());
        eq(-1, LegacyInfusionIngredientQueue.restore(3, 7, 0).activeIndex());
        for (int invalid : new int[]{-1,0,31,32}) reject(() -> LegacyInfusionIngredientQueue.initial(invalid));
        reject(() -> LegacyInfusionIngredientQueue.restore(Integer.MAX_VALUE, -1, 0));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, -1, 0));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 8, 0));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 0, -1));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 0, 6));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 7, 1));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 7, 0, 0));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 0, 0, -1));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 0, 0, 3));
        reject(() -> LegacyInfusionIngredientQueue.restore(3, 2, 0, 1));
        var initial = LegacyInfusionIngredientQueue.initial(3);
        reject(() -> initial.select(-1));
        reject(() -> initial.select(3));
        var consume = consuming(3, 2);
        reject(() -> consume.afterActualPedestalRemoval(0));
        reject(() -> consume.afterActualPedestalRemoval(3));
        reject(() -> consume.afterActualPedestalRemoval(1));
    }
    public static void main(String[] args) {
        skipMissingWithoutRestartingCountdown();
        missingUnpaidIsNotAReceipt();
        recoverExactlySelectedStaleRemovalOnce();
        rejectForgedStaleReceipts();
        thirtyIndependentBitsAndOutOfOrderCompletion();
        restoreLegacyAndValidateState();
        System.out.println("BETA26 infusion ingredient queue: OK (" + checks + " assertions)");
    }
}
