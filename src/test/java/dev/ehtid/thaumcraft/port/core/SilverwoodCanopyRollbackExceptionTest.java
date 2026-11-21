package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Callback-level rollback fault injection; not a native NeoForge GameTest. */
public final class SilverwoodCanopyRollbackExceptionTest {
    public static void main(String[] args) {
        Map<Integer, String> world = new LinkedHashMap<>();
        world.put(1, "LEAVES");
        world.put(2, "LEAVES");
        world.put(3, "LEAVES");
        Map<Integer, String> originals = new LinkedHashMap<>();
        originals.put(1, "AIR");
        originals.put(2, "FLOWER");
        originals.put(3, "AIR");
        RuntimeException first = new IllegalStateException("first");
        RuntimeException second = new IllegalStateException("second");
        int[] attempts = {0};
        RuntimeException caught = null;
        try {
            SilverwoodCanopyRollback.restore(originals, "LEAVES", world::get,
                    (pos, state) -> {
                        attempts[0]++;
                        if (pos == 1) throw first;
                        if (pos == 2) throw second;
                        world.put(pos, state);
                        return true;
                    });
        } catch (RuntimeException failure) {
            caught = failure;
        }
        if (caught != first) throw new AssertionError("first failure must remain primary");
        if (caught.getSuppressed().length != 1 || caught.getSuppressed()[0] != second)
            throw new AssertionError("later failure must be suppressed");
        if (attempts[0] != 3 || !"AIR".equals(world.get(3)))
            throw new AssertionError("cleanup must continue after failures");
        if (!"LEAVES".equals(world.get(1)) || !"LEAVES".equals(world.get(2)))
            throw new AssertionError("failed positions must remain unchanged");

        Map<Integer, String> reused = new LinkedHashMap<>();
        reused.put(1, "LEAVES");
        reused.put(2, "LEAVES");
        RuntimeException same = new IllegalStateException("reused");
        try {
            SilverwoodCanopyRollback.restore(Map.of(1, "AIR", 2, "AIR"),
                    "LEAVES", reused::get, (pos, state) -> { throw same; });
            throw new AssertionError("expected exception");
        } catch (IllegalStateException failure) {
            if (failure != same || failure.getSuppressed().length != 0)
                throw new AssertionError("self-suppression must be avoided");
        }
        System.out.println("M12 Silverwood canopy rollback exception: OK");
    }
}
