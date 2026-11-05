package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class InfusionRitualLedgerParityTest {
    private static int checks;
    private static void eq(Object a, Object b) {
        checks++;
        if (!Objects.equals(a,b)) throw new AssertionError("expected "+a+" got "+b);
    }
    private static void bad(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Invalid ritual checkpoint accepted");
    }

    public static void main(String[] args) {
        var aspects = new AspectRegistry();
        var requested = new AspectListData()
                .add(aspects.AIR, 10).add(aspects.CRYSTAL,10).add(aspects.TRAP,5);
        var ledger = LegacyInfusionRitualLedger.begin("thaumcraft:CrystalClusterAir",requested);
        eq(25,ledger.unitsLeft());
        eq(0,ledger.paid());
        eq(false,ledger.readyForIngredients());
        // The input list was only a snapshot; mutating it cannot grant units.
        requested.remove(aspects.AIR, 10);
        eq(25,ledger.unitsLeft());
        for (int i=0;i<10;i++) ledger=ledger.afterWorldDebit("aer");
        eq(15,ledger.unitsLeft());
        eq(10,ledger.paid());
        var checkpoint = LegacyInfusionRitualLedger.restore(ledger.recipeId(),
                ledger.requested(),ledger.remaining(),ledger.paid());
        eq(ledger.remaining(),checkpoint.remaining());
        eq(10,checkpoint.paid());
        var stoppedAtAerZero = checkpoint;
        bad(()->stoppedAtAerZero.afterWorldDebit("aer"));
        bad(()->stoppedAtAerZero.afterWorldDebit("unknown"));
        for (int i=0;i<10;i++) checkpoint=checkpoint.afterWorldDebit("vitreus");
        for (int i=0;i<5;i++) checkpoint=checkpoint.afterWorldDebit("vinculum");
        eq(true,checkpoint.readyForIngredients());
        eq(0,checkpoint.unitsLeft());
        eq(25,checkpoint.paid());
        var done=checkpoint;
        bad(()->done.afterWorldDebit("aer"));

        var preCheckpoint = ledger;
        var restoredOriginal = new LinkedHashMap<>(preCheckpoint.requested());
        var forged = new LinkedHashMap<>(preCheckpoint.remaining());
        forged.put("aer",11);
        bad(()->LegacyInfusionRitualLedger.restore(preCheckpoint.recipeId(),restoredOriginal,forged,10));
        bad(()->LegacyInfusionRitualLedger.restore(preCheckpoint.recipeId(),restoredOriginal,preCheckpoint.remaining(),11));
        forged.remove("vinculum");
        bad(()->LegacyInfusionRitualLedger.restore(preCheckpoint.recipeId(),restoredOriginal,forged,10));
        var mutableCopy = new LinkedHashMap<>(ledger.remaining());
        var immutable = LegacyInfusionRitualLedger.restore(ledger.recipeId(),
                ledger.requested(),mutableCopy,ledger.paid());
        mutableCopy.put("vitreus",10000);
        eq(10,immutable.remaining().get("vitreus"));
        bad(()->immutable.remaining().put("aer",8));
        bad(()->LegacyInfusionRitualLedger.restore("",restoredOriginal,preCheckpoint.remaining(),10));
        // Restart verification must check the ACTUAL BETA26 recipe price,
        // not just arithmetic consistency of an attacker/corrupt save.
        for (var spec : LegacyInfusionCrystalFarmerCatalog.all()) {
            var originalPrice = LegacyInfusionCrystalFarmerCatalog.essentia(spec, aspects);
            for (float multiplier : new float[] { 0.5F, 1.0F, 1.35F }) {
                var actualPrice = LegacyInfusionStartPlanner.calculateEssentia(
                        originalPrice, multiplier);
                var real = LegacyInfusionRitualLedger.begin(spec.legacyId(), actualPrice);
                var verified = LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        spec.legacyId(), real.requested(), real.remaining(), 0, multiplier);
                eq(real.requested(), verified.requested());
                eq(real.unitsLeft(), verified.unitsLeft());
                String firstAspect = verified.requested().keySet().iterator().next();
                var afterDebit = verified.afterWorldDebit(firstAspect);
                eq(1, LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        spec.legacyId(), afterDebit.requested(), afterDebit.remaining(),
                        1, multiplier).paid());

                var noCost = new LinkedHashMap<String,Integer>();
                real.requested().forEach((tag,count) -> noCost.put(tag, 0));
                bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        spec.legacyId(), noCost, noCost, 0, multiplier));
                var reduced = new LinkedHashMap<>(real.requested());
                reduced.put(firstAspect, reduced.get(firstAspect) - 1);
                bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        spec.legacyId(), reduced, reduced, 0, multiplier));
                var extra = new LinkedHashMap<>(real.requested());
                extra.put("ignis".equals(firstAspect) ? "aer" : "ignis", 100);
                bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        spec.legacyId(), extra, extra, 0, multiplier));
                bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                        spec.legacyId(), real.requested(), real.remaining(), 0,
                        Float.NaN));
            }
        }
        var catalogAir = LegacyInfusionCrystalFarmerCatalog.all().get(0);
        var genuine = LegacyInfusionRitualLedger.begin(catalogAir.legacyId(),
                LegacyInfusionCrystalFarmerCatalog.essentia(catalogAir, aspects));
        bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                "thaumcraft:nonexistent", genuine.requested(), genuine.remaining(), 0, 1));
        bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                genuine.recipeId(), genuine.requested(), genuine.remaining(), 0, 0.49F));
        bad(() -> LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer(
                genuine.recipeId(), genuine.requested(), genuine.remaining(), 0,
                Float.POSITIVE_INFINITY));
        System.out.println("BETA26 infusion ritual resource ledger: OK ("+checks+" assertions)");
    }
}
