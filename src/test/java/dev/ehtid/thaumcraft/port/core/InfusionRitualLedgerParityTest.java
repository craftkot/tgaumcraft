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
        bad(()->checkpoint.afterWorldDebit("unknown"));
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
        bad(()->LegacyInfusionRitualLedger.restore(ledger.recipeId(),restoredOriginal,forged,10));
        var mutableCopy = new LinkedHashMap<>(ledger.remaining());
        var immutable = LegacyInfusionRitualLedger.restore(ledger.recipeId(),
                ledger.requested(),mutableCopy,ledger.paid());
        mutableCopy.put("vitreus",10000);
        eq(10,immutable.remaining().get("vitreus"));
        bad(()->immutable.remaining().put("aer",8));
        bad(()->LegacyInfusionRitualLedger.restore("",restoredOriginal,preCheckpoint.remaining(),10));
        System.out.println("BETA26 infusion ritual resource ledger: OK ("+checks+" assertions)");
    }
}
