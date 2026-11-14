package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

public final class CrystalFarmerFinishCheckpointTest {
    private static int checks;
    private static void eq(Object a,Object b) {
        checks++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);
    }
    private static void bad(Runnable action) {
        checks++;
        try {action.run();}catch(IllegalArgumentException | IllegalStateException expected){return;}
        throw new AssertionError("Invalid/replayed original infusion result");
    }
    public static void main(String[] args) {
        var original=LegacyCrystalFarmerFinishCheckpoint.initial();
        eq("WAITING",original.phase().name());
        eq(true,original.consistentWithPaidProgress(false,false));
        eq(true,original.consistentWithPaidProgress(true,true));
        bad(()->original.reserve(false,true,true));
        bad(()->original.reserve(true,false,true));
        bad(()->original.reserve(true,true,false));
        var reserved=original.reserve(true,true,true);
        eq("RESERVED",reserved.phase().name());
        eq(false,reserved.consistentWithPaidProgress(false,true));
        eq(false,reserved.consistentWithPaidProgress(true,false));
        eq(true,reserved.consistentWithPaidProgress(true,true));
        bad(()->reserved.reserve(true,true,true));
        bad(()->reserved.finishAfterActualCenterReplacement(false));
        var recovered=LegacyCrystalFarmerFinishCheckpoint.restore("RESERVED");
        eq("STALLED", recovered.phase().name());
        eq(false,recovered.consistentWithPaidProgress(false,true));
        eq(false,recovered.consistentWithPaidProgress(true,false));
        eq(true,recovered.consistentWithPaidProgress(true,true));
        eq(false,recovered.mayPublishOneItem());
        eq(false,recovered.canReserve());
        bad(()->recovered.finishAfterActualCenterReplacement(true));
        bad(()->recovered.reserve(true,true,true));
        eq(recovered,LegacyCrystalFarmerFinishCheckpoint.restore("STALLED"));
        // Crash after the center's successful BlockEntity save, but before
        // matrix saved crafting=false. Checkpoint may ACK only an existing
        // output after every real ingredient and essentia debit is verified.
        eq(recovered,recovered.acknowledgeAlreadyPublished(false,true,true));
        eq(recovered,recovered.acknowledgeAlreadyPublished(true,false,true));
        eq(recovered,recovered.acknowledgeAlreadyPublished(true,true,false));
        var acknowledged=recovered.acknowledgeAlreadyPublished(true,true,true);
        eq(true,acknowledged.finished());
        eq(false,acknowledged.canReserve());
        bad(()->acknowledged.reserve(true,true,true));
        bad(()->acknowledged.finishAfterActualCenterReplacement(true));
        eq(original,original.acknowledgeAlreadyPublished(true,true,true));
        eq(reserved,reserved.acknowledgeAlreadyPublished(true,true,true));
        // Recovery never implicitly publishes an item. The real world bridge
        // must fail closed if RESERVED was already present at load.
        var finished=reserved.finishAfterActualCenterReplacement(true);
        eq("FINISHED",finished.phase().name());
        bad(()->finished.finishAfterActualCenterReplacement(true));
        bad(()->finished.reserve(true,true,true));
        bad(()->LegacyCrystalFarmerFinishCheckpoint.restore("NOT_A_PHASE"));
        eq(true,finished.finished());
        eq(false,finished.canReserve());
        // Cross-stage restart simulation in pure Java for ALL seven actual
        // BETA26 recipes and three legal multiplier values. This checks the
        // exact amount paid, the source 5-cycle order, and a non-replayable
        // crash after reservation. It is NOT a Minecraft world-save test.
        var aspects = new AspectRegistry();
        for (var recipe : LegacyInfusionCrystalFarmerCatalog.all()) {
            for (float multiplier : new float[]{0.5F, 1.0F, 1.35F}) {
                var price = LegacyInfusionStartPlanner.calculateEssentia(
                        LegacyInfusionCrystalFarmerCatalog.essentia(recipe, aspects), multiplier);
                var ledger = LegacyInfusionRitualLedger.begin(recipe.legacyId(), price);
                eq(false, ledger.readyForIngredients());
                for (var amount : ledger.requested().entrySet()) {
                    for (int unit = 0; unit < amount.getValue(); unit++)
                        ledger = ledger.afterWorldDebit(amount.getKey());
                }
                eq(true, ledger.readyForIngredients());
                eq(0, ledger.unitsLeft());
                var paid = LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        recipe.legacyId(), ledger.requested(), ledger.remaining(),
                        ledger.paid(), multiplier);
                eq(true, paid.readyForIngredients());
                var items = LegacyCrystalFarmerIngredientProgress.initial();
                for (int bit : new int[]{
                        LegacyCrystalFarmerIngredientProgress.SEEDS_BIT,
                        LegacyCrystalFarmerIngredientProgress.SALIS_BIT}) {
                    for (int step = 0; step < 5; step++)
                        items = items.afterValidatedCountdown();
                    eq(LegacyCrystalFarmerIngredientProgress.Action.CONSUME, items.nextAction());
                    items = items.afterActualPedestalRemoval(bit);
                }
                eq(true, items.readyToFinish());
                eq(true, items.matchesLivePedestals(false, false));
                eq(false, items.matchesLivePedestals(true, false));
                eq(false, items.matchesLivePedestals(false, true));
                var reservation = LegacyCrystalFarmerFinishCheckpoint.initial()
                        .reserve(paid.readyForIngredients(), items.readyToFinish(), true);
                var afterCrash = LegacyCrystalFarmerFinishCheckpoint.restore(
                        reservation.phase().name());
                eq(LegacyCrystalFarmerFinishCheckpoint.Phase.STALLED, afterCrash.phase());
                eq(afterCrash, afterCrash.acknowledgeAlreadyPublished(true, true, false));
                eq(true, afterCrash.acknowledgeAlreadyPublished(true, true, true).finished());
                bad(() -> afterCrash.finishAfterActualCenterReplacement(true));
            }
        }
        // A visually identical output planted on the center pedestal must
        // NEVER turn a STALLED reservation into accepted delivery.
        // The receipt's UUID and original output item ID are retained in
        // the SAME physical center pedestal NBT as the actual output.
        var ritualId = java.util.UUID.fromString("50d6639c-f9d8-4f1a-8e40-1c6160243f01");
        var foreignId = java.util.UUID.fromString("9a56c783-64a5-4239-a65a-8bf18542f005");
        for (var recipe : LegacyInfusionCrystalFarmerCatalog.all()) {
            String output = "thaumcraft:crystal_" + recipe.aspectTag();
            var receipt = new LegacyCrystalFarmerOutputReceipt(ritualId, output);
            eq(true, receipt.provesSavedPublication(ritualId, output, true));
            eq(false, receipt.provesSavedPublication(foreignId, output, true));
            eq(false, receipt.provesSavedPublication(null, output, true));
            eq(false, receipt.provesSavedPublication(ritualId,
                    "thaumcraft:crystal_wrong", true));
            eq(false, receipt.provesSavedPublication(ritualId, output, false));
            eq(false, receipt.provesSavedPublication(ritualId, null, true));
            var crashed = LegacyCrystalFarmerFinishCheckpoint.restore("RESERVED");
            eq(crashed, crashed.acknowledgeAlreadyPublished(true, true,
                    receipt.provesSavedPublication(foreignId, output, true)));
            eq(crashed, crashed.acknowledgeAlreadyPublished(true, true,
                    receipt.provesSavedPublication(ritualId, output, false)));
            eq(true, crashed.acknowledgeAlreadyPublished(true, true,
                    receipt.provesSavedPublication(ritualId, output, true)).finished());
        }
        bad(() -> new LegacyCrystalFarmerOutputReceipt(null, "thaumcraft:crystal_aer"));
        bad(() -> new LegacyCrystalFarmerOutputReceipt(ritualId, ""));
        bad(() -> new LegacyCrystalFarmerOutputReceipt(ritualId, null));
        System.out.println("BETA26 Crystal Farmer finish reservation: OK ("+checks+" assertions)");
    }
}
