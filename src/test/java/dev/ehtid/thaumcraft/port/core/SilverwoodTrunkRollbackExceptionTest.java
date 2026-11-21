package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Loader-neutral callback fault injection; not a NeoForge GameTest. */
public final class SilverwoodTrunkRollbackExceptionTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Map<Integer, String> written = new LinkedHashMap<>();
        written.put(1, "LOG_Y");
        written.put(2, "LOG_X");
        written.put(3, "LOG_Z");
        written.put(4, "LOG_Y");
        Map<Integer, String> original = Map.of(1, "AIR", 2, "DIRT", 3, "FLOWER", 4, "AIR");
        Map<Integer, String> world = new LinkedHashMap<>(written);
        RuntimeException readFailure = new IllegalStateException("read");
        RuntimeException writeFailure = new IllegalStateException("write");
        int[] attempted = {0};
        RuntimeException observed = null;
        try {
            SilverwoodTrunkRollback.restore(original, written,
                pos -> {
                    if (pos == 1) throw readFailure;
                    return world.get(pos);
                },
                (pos, state) -> {
                    attempted[0]++;
                    if (pos == 2) throw writeFailure;
                    world.put(pos, state);
                    return true;
                });
        } catch (RuntimeException failure) {
            observed = failure;
        }
        check(observed == readFailure, "first exception remains primary");
        check(observed.getSuppressed().length == 1, "second exception retained");
        check(observed.getSuppressed()[0] == writeFailure, "secondary exception identity");
        check(attempted[0] == 3, "later trunk positions still attempted");
        check(world.get(1).equals("LOG_Y"), "failed read does not write");
        check(world.get(2).equals("LOG_X"), "failed write leaves log");
        check(world.get(3).equals("FLOWER"), "later log restored");
        check(world.get(4).equals("AIR"), "last log restored");

        RuntimeException reused = new IllegalStateException("reused");
        int[] reads = {0};
        RuntimeException repeatedObserved = null;
        try {
            SilverwoodTrunkRollback.restore(original, written,
                pos -> { reads[0]++; throw reused; },
                (pos, state) -> { throw new AssertionError("must not write"); });
        } catch (RuntimeException failure) {
            repeatedObserved = failure;
        }
        check(repeatedObserved == reused, "reused exception remains primary");
        check(reused.getSuppressed().length == 0, "never self-suppress");
        check(reads[0] == 4, "every trunk position attempted");
        System.out.println("SilverwoodTrunkRollbackExceptionTest: OK (" + checks + " assertions)");
    }
}
