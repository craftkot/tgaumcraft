package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic setBlock rejection simulation for the M12 rollback boundary.
 * This is a loader-neutral contract test, NOT a native WorldGenLevel GameTest.
 */
public final class SilverwoodRollbackFailureInjectionTest {
    private static int checks;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        Map<Integer, String> world = new LinkedHashMap<>();
        world.put(1, "FLOWER");
        world.put(2, "AIR");
        world.put(3, "AIR");
        Map<Integer, String> leavesBefore = new LinkedHashMap<>();
        Map<Integer, String> logsBefore = new LinkedHashMap<>();
        Map<Integer, String> writtenLogs = new LinkedHashMap<>();

        // Canopy writes precede trunk writes. Record the first original state.
        for (int pos : new int[] {1, 2, 3}) {
            String before = world.get(pos);
            leavesBefore.putIfAbsent(pos, before);
            world.put(pos, "LEAVES");
        }
        // Trunk replaces our own canopy at 2, but the world rejects the next
        // trunk write at 3. The failure path must restore both phases.
        logsBefore.put(2, leavesBefore.get(2));
        world.put(2, "LOG_Y");
        writtenLogs.put(2, "LOG_Y");
        boolean accepted = false; // simulated WorldGenLevel.setBlock(3, LOG_X, 2)
        check(!accepted, "injected trunk rejection");

        int restoredLogs = SilverwoodTrunkRollback.restore(logsBefore, writtenLogs,
                world::get, (pos, state) -> {
                    world.put(pos, state);
                    return true;
                });
        int restoredLeaves = SilverwoodCanopyRollback.restore(leavesBefore, "LEAVES",
                world::get, (pos, state) -> {
                    world.put(pos, state);
                    return true;
                });
        check(restoredLogs == 1, "restore accepted trunk write");
        check(restoredLeaves == 2, "restore remaining canopy writes");
        check("FLOWER".equals(world.get(1)), "restore replaceable flower");
        check("AIR".equals(world.get(2)), "restore pre-canopy state under trunk");
        check("AIR".equals(world.get(3)), "restore leaf at rejected trunk");
        check(SilverwoodTrunkRollback.restore(logsBefore, writtenLogs,
                world::get, (pos, state) -> true) == 0, "idempotent trunk cleanup");
        check(SilverwoodCanopyRollback.restore(leavesBefore, "LEAVES",
                world::get, (pos, state) -> true) == 0, "idempotent canopy cleanup");
        System.out.println("M12 Silverwood rollback failure injection: OK (" + checks + " assertions)");
    }
}
