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
        state = state.afterActualPedestalRemoval(1);
        eq(1,state.consumedMask());
        eq(2,state.nextIngredientBit());
        eq(0,state.countdown());
        var snapshot = new LegacyCrystalFarmerIngredientProgress(
                state.consumedMask(),state.countdown());
        eq(state,snapshot); // round-trip NBT primitives
        for(int i=0;i<5;i++) snapshot = snapshot.afterValidatedCountdown();
        eq(LegacyCrystalFarmerIngredientProgress.Action.CONSUME,snapshot.nextAction());
        snapshot = snapshot.afterActualPedestalRemoval(2);
        eq(true,snapshot.readyToFinish());
        eq(3,snapshot.consumedMask());
        eq(LegacyCrystalFarmerIngredientProgress.Action.READY,snapshot.nextAction());
        var done = snapshot;
        rejected(()->done.afterActualPedestalRemoval(1));
        rejected(()->done.afterValidatedCountdown());
        rejected(()->new LegacyCrystalFarmerIngredientProgress(4,0));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(3,1));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(0,-1));
        rejected(()->new LegacyCrystalFarmerIngredientProgress(1,6));
        System.out.println("BETA26 Crystal Farmer ingredient countdown: OK ("+checks+" assertions)");
    }
}
