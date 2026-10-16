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
        bad(()->original.reserve(false,true,true));
        bad(()->original.reserve(true,false,true));
        bad(()->original.reserve(true,true,false));
        var reserved=original.reserve(true,true,true);
        eq("RESERVED",reserved.phase().name());
        bad(()->reserved.reserve(true,true,true));
        bad(()->reserved.finishAfterActualCenterReplacement(false));
        var recovered=LegacyCrystalFarmerFinishCheckpoint.restore("RESERVED");
        eq("STALLED", recovered.phase().name());
        eq(false,recovered.mayPublishOneItem());
        eq(false,recovered.canReserve());
        bad(()->recovered.finishAfterActualCenterReplacement(true));
        bad(()->recovered.reserve(true,true,true));
        eq(recovered,LegacyCrystalFarmerFinishCheckpoint.restore("STALLED"));
        // Recovery never implicitly publishes an item. The real world bridge
        // must fail closed if RESERVED was already present at load.
        var finished=reserved.finishAfterActualCenterReplacement(true);
        eq("FINISHED",finished.phase().name());
        bad(()->finished.finishAfterActualCenterReplacement(true));
        bad(()->finished.reserve(true,true,true));
        bad(()->LegacyCrystalFarmerFinishCheckpoint.restore("NOT_A_PHASE"));
        eq(true,finished.finished());
        eq(false,finished.canReserve());
        System.out.println("BETA26 Crystal Farmer finish reservation: OK ("+checks+" assertions)");
    }
}
