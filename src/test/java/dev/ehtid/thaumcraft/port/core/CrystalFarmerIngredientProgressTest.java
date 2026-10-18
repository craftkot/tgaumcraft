package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

public final class CrystalFarmerIngredientProgressTest {
    private static int checks;
    private static void eq(Object a, Object b) {
        checks++;
        if (!Objects.equals(a, b)) throw new AssertionError(a + " != " + b);
    }
    private static void rejected(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException e) { return; }
        throw new AssertionError("Invalid/replayed item stage accepted");
    }
    public static void main(String[] args) {
        var state = LegacyCrystalFarmerIngredientProgress.initial();
        eq(1, state.nextIngredientBit());
        eq(true, state.matchesLivePedestals(true, true));
        eq(false, state.matchesLivePedestals(false, true));
        eq(false, state.matchesLivePedestals(true, false));
        eq(LegacyCrystalFarmerIngredientProgress.Action.SOURCE_EFFECT,state.nextAction());
        var beforeStart = state;
        rejected(()->beforeStart.afterActualPedestalRemoval(1));
        state = state.afterValidatedCountdown();
        eq(5,state.countdown());
        for(int i=4;i>=1;i--) {
            state = state.afterValidatedCountdown();
            eq(i,state.countdown());
        }
        var pending = state;
        rejected(()->pending.afterValidatedCountdown());
        rejected(()->pending.afterActualPedestalRemoval(2));
        rejected(()->pending.afterSavedPedestalReceipt(1, false, true));
        rejected(()->pending.afterSavedPedestalReceipt(1, true, false));
        rejected(()->pending.afterSavedPedestalReceipt(2, true, true));
        var fromSavedReceipt = pending.afterSavedPedestalReceipt(1, true, true);
        eq(1, fromSavedReceipt.consumedMask());
        eq(0, fromSavedReceipt.countdown());
        rejected(()->fromSavedReceipt.afterSavedPedestalReceipt(1, true, true));
        // A stale matrix may have saved the 0..5 countdown before the
        // real pedestal saved its empty slot + UUID receipt. Any earlier
        // countdown is recoverable, never just the final 1 -> 0 tick.
        for (int staleCountdown = 0; staleCountdown <= 5; staleCountdown++) {
            var stale = new LegacyCrystalFarmerIngredientProgress(0, staleCountdown);
            var acknowledged = stale.afterSavedPedestalReceipt(1, true, true);
            eq(1, acknowledged.consumedMask());
            eq(0, acknowledged.countdown());
            rejected(()->acknowledged.afterSavedPedestalReceipt(1, true, true));
            var staleSecond = new LegacyCrystalFarmerIngredientProgress(1, staleCountdown);
            var secondAck = staleSecond.afterSavedPedestalReceipt(2, true, true);
            eq(3, secondAck.consumedMask());
            eq(true, secondAck.readyToFinish());
            rejected(()->secondAck.afterSavedPedestalReceipt(2, true, true));
        }
        state = state.afterActualPedestalRemoval(1);
        eq(state,fromSavedReceipt);
        eq(1,state.consumedMask());
        eq(true,state.matchesLivePedestals(false, true));
        eq(false,state.matchesLivePedestals(true, true)); // matrix newer than pedestal
        eq(false,state.matchesLivePedestals(false, false)); // Salis vanished without debit
        eq(2,state.nextIngredientBit());
        eq(0,state.countdown());
        var snapshot = new LegacyCrystalFarmerIngredientProgress(
                state.consumedMask(),state.countdown());
        eq(state,snapshot); // round-trip NBT primitives
        for(int i=0;i<5;i++) snapshot = snapshot.afterValidatedCountdown();
        eq(LegacyCrystalFarmerIngredientProgress.Action.CONSUME,snapshot.nextAction());
        var pendingSalis = snapshot;
        rejected(()->pendingSalis.afterSavedPedestalReceipt(1, true, true));
        rejected(()->pendingSalis.afterSavedPedestalReceipt(2, false, true));
        rejected(()->pendingSalis.afterSavedPedestalReceipt(2, true, false));
        eq(3, pendingSalis.afterSavedPedestalReceipt(2, true, true).consumedMask());
        snapshot = snapshot.afterActualPedestalRemoval(2);
        eq(true,snapshot.readyToFinish());
        eq(3,snapshot.consumedMask());
        eq(true,snapshot.matchesLivePedestals(false, false));
        eq(false,snapshot.matchesLivePedestals(true, false)); // crash restored seeds
        eq(false,snapshot.matchesLivePedestals(false, true)); // crash restored Salis
        eq(false,snapshot.matchesLivePedestals(true, true));
        eq(LegacyCrystalFarmerIngredientProgress.Action.READY,snapshot.nextAction());
        var done = snapshot;
        rejected(()->done.afterActualPedestalRemoval(1));
        rejected(()->done.afterValidatedCountdown());
        rejected(()->new LegacyCrystalFarmerIngredientProgress(2,0));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(2,5));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(4,0));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(3,1));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(0,-1));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(1,6));
        // Recovery requires an exact receipt in the SAME saved pedestal,
        // not merely an empty slot or a matching item type.
        var a = java.util.UUID.fromString("93c2dbf0-41e3-49b8-a224-0a315dbc0b42");
        var b = java.util.UUID.fromString("a0ab9d33-a1f0-4344-b3bb-baf772cab284");
        var seedReceipt = new LegacyCrystalFarmerPedestalReceipt(a, 1);
        eq(true, seedReceipt.provesSavedRemoval(a, 1, true));
        eq(false, seedReceipt.provesSavedRemoval(a, 1, false));
        eq(false, seedReceipt.provesSavedRemoval(b, 1, true));
        eq(false, seedReceipt.provesSavedRemoval(null, 1, true));
        eq(false, seedReceipt.provesSavedRemoval(a, 2, true));
        var salisReceipt = new LegacyCrystalFarmerPedestalReceipt(a, 2);
        eq(true, salisReceipt.provesSavedRemoval(a, 2, true));
        eq(false, salisReceipt.provesSavedRemoval(a, 1, true));
        rejected(()->new LegacyCrystalFarmerPedestalReceipt(null, 1));
        rejected(()->new LegacyCrystalFarmerPedestalReceipt(a, 0));
        rejected(()->new LegacyCrystalFarmerPedestalReceipt(a, 3));
        System.out.println("BETA26 Crystal Farmer ingredient countdown: OK ("+checks+" assertions)");
    }
}
