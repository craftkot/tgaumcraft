package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GreatwoodTrunkPlacementTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class World implements GreatwoodTrunkPlacement.WorldAdapter<String> {
        final Map<GreatwoodTrunkPlan.Cell, String> blocks = new HashMap<>();
        final List<GreatwoodTrunkPlan.Cell> writes = new ArrayList<>();
        int rejectAt = -1;
        int throwAt = -1;
        boolean denyReplace;
        boolean rejectRestore;
        int trunkWrites;
        RuntimeException sharedFailure;

        public String read(GreatwoodTrunkPlan.Cell cell) {
            return blocks.getOrDefault(cell, "air");
        }
        public boolean canReplace(GreatwoodTrunkPlan.Cell cell, String previous) {
            return !denyReplace;
        }
        public boolean write(GreatwoodTrunkPlan.Cell cell, String state) {
            writes.add(cell);
            if ("log".equals(state)) {
                trunkWrites++;
                blocks.put(cell, state); // Model an adapter that mutates before failure.
                if (trunkWrites == throwAt) {
                    if (sharedFailure != null) throw sharedFailure;
                    throw new IllegalStateException("injected");
                }
                return trunkWrites != rejectAt;
            }
            if (rejectRestore) return false;
            if (sharedFailure != null) throw sharedFailure;
            blocks.put(cell, state);
            return true;
        }
    }

    public static void main(String[] args) {
        List<GreatwoodTrunkPlan.Cell> plan =
                GreatwoodTrunkPlan.create(4, 70, 9, 2, -64, 320);
        World success = new World();
        check(GreatwoodTrunkPlacement.place(plan, "log", success), "successful placement");
        check(success.blocks.size() == 8, "eight blocks written");
        check(success.blocks.values().stream().allMatch("log"::equals), "all logs");

        World denied = new World();
        denied.denyReplace = true;
        check(!GreatwoodTrunkPlacement.place(plan, "log", denied), "preflight denied");
        check(denied.writes.isEmpty(), "no writes on preflight rejection");

        World rejected = new World();
        rejected.rejectAt = 3;
        check(!GreatwoodTrunkPlacement.place(plan, "log", rejected), "rejected write");
        check(rejected.blocks.values().stream().allMatch("air"::equals), "failed cell restored");
        check(rejected.writes.size() == 6, "three writes and three restores");
        check(rejected.writes.get(3).equals(plan.get(2))
                && rejected.writes.get(5).equals(plan.get(0)), "reverse rollback");

        World throwing = new World();
        throwing.throwAt = 2;
        try {
            GreatwoodTrunkPlacement.place(plan, "log", throwing);
            throw new AssertionError("exception not propagated");
        } catch (IllegalStateException expected) {
            check("injected".equals(expected.getMessage()), "original exception preserved");
        }
        check(throwing.blocks.values().stream().allMatch("air"::equals), "exception rollback");

        World unrestorable = new World();
        unrestorable.rejectAt = 1;
        unrestorable.rejectRestore = true;
        try {
            GreatwoodTrunkPlacement.place(plan, "log", unrestorable);
            throw new AssertionError("rollback failure hidden");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("rollback rejected"), "restore failure visible");
        }

        // The same RuntimeException object can be thrown by both callbacks.
        // Never mask the original failure with IllegalArgumentException from addSuppressed(self).
        World sameException = new World();
        sameException.throwAt = 2;
        sameException.sharedFailure = new IllegalStateException("shared failure");
        try {
            GreatwoodTrunkPlacement.place(plan, "log", sameException);
            throw new AssertionError("shared exception not propagated");
        } catch (IllegalStateException expected) {
            check(expected == sameException.sharedFailure, "original shared exception identity");
            check(expected.getSuppressed().length == 0, "no self-suppression");
            check(sameException.writes.size() == 4, "rollback attempted after shared failure");
        }

        // A false write followed by repeated identical restore exceptions must
        // preserve the first rollback failure instead of self-suppressing it.
        World repeatedRestore = new World();
        repeatedRestore.rejectAt = 2;
        repeatedRestore.sharedFailure = new IllegalStateException("repeated restore failure");
        try {
            GreatwoodTrunkPlacement.place(plan, "log", repeatedRestore);
            throw new AssertionError("repeated restore failure hidden");
        } catch (IllegalStateException expected) {
            check(expected == repeatedRestore.sharedFailure, "first restore failure preserved");
            check(expected.getSuppressed().length == 0, "repeated restore has no self-suppression");
            check(repeatedRestore.writes.size() == 4, "all attempted restores executed");
        }

        // When placement throws and restoration rejects, preserve the placement
        // exception and attach rollback failure instead of replacing its cause.
        World throwAndRejectRestore = new World();
        throwAndRejectRestore.throwAt = 2;
        throwAndRejectRestore.rejectRestore = true;
        try {
            GreatwoodTrunkPlacement.place(plan, "log", throwAndRejectRestore);
            throw new AssertionError("write/rollback failure hidden");
        } catch (IllegalStateException expected) {
            check("injected".equals(expected.getMessage()), "original write failure survives rollback rejection");
            check(expected.getSuppressed().length == 1, "rollback failure attached once");
            check(expected.getSuppressed()[0].getMessage().contains("rollback rejected"),
                    "suppressed failure describes rejected restoration");
            check(expected.getSuppressed()[0].getSuppressed().length == 1,
                    "both rejected restores reported");
            check(throwAndRejectRestore.writes.size() == 4, "both restore attempts completed");
        }

        check(!GreatwoodTrunkPlacement.place(List.of(), "log", new World()), "empty plan rejected");
        check(!GreatwoodTrunkPlacement.place(List.of(plan.get(0), plan.get(0)),
                "log", new World()), "duplicate plan rejected");
        System.out.println("M12 GreatwoodTrunkPlacement: OK (" + checks + " assertions)");
    }
}
