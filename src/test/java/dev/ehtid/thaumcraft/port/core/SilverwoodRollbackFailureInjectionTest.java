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
        // A foreign edit after placement must survive best-effort cleanup.
        Map<Integer, String> foreignWorld = new LinkedHashMap<>();
        foreignWorld.put(10, "LOG_Y");
        foreignWorld.put(11, "LEAVES");
        Map<Integer, String> foreignOriginals = Map.of(10, "AIR");
        Map<Integer, String> foreignWritten = Map.of(10, "LOG_Y");
        Map<Integer, String> foreignLeaves = Map.of(11, "FLOWER");
        foreignWorld.put(10, "STONE"); // another writer replaced the log
        foreignWorld.put(11, "DIRT");  // another writer replaced the leaves
        check(SilverwoodTrunkRollback.restore(foreignOriginals, foreignWritten,
                foreignWorld::get, (pos, state) -> {
                    foreignWorld.put(pos, state);
                    return true;
                }) == 0, "do not erase foreign trunk replacement");
        check(SilverwoodCanopyRollback.restore(foreignLeaves, "LEAVES",
                foreignWorld::get, (pos, state) -> {
                    foreignWorld.put(pos, state);
                    return true;
                }) == 0, "do not erase foreign canopy replacement");
        check("STONE".equals(foreignWorld.get(10)), "preserve foreign trunk state");
        check("DIRT".equals(foreignWorld.get(11)), "preserve foreign canopy state");

        // Even a matching state cannot be counted as restored when setBlock rejects.
        Map<Integer, String> rejectedWorld = new LinkedHashMap<>();
        rejectedWorld.put(20, "LOG_X");
        rejectedWorld.put(21, "LEAVES");
        check(SilverwoodTrunkRollback.restore(Map.of(20, "AIR"), Map.of(20, "LOG_X"),
                rejectedWorld::get, (pos, state) -> false) == 0,
                "rejected trunk rollback is not counted");
        check(SilverwoodCanopyRollback.restore(Map.of(21, "FLOWER"), "LEAVES",
                rejectedWorld::get, (pos, state) -> false) == 0,
                "rejected canopy rollback is not counted");
        check("LOG_X".equals(rejectedWorld.get(20)), "rejected trunk remains unchanged");
        check("LEAVES".equals(rejectedWorld.get(21)), "rejected canopy remains unchanged");
        // Corrupt trunk journals must fail before touching even the first valid block.
        Map<Integer, String> corruptWorld = new LinkedHashMap<>();
        corruptWorld.put(30, "LOG_X");
        corruptWorld.put(31, "LOG_Y");
        Map<Integer, String> corruptWritten = new LinkedHashMap<>();
        corruptWritten.put(30, "LOG_X");
        corruptWritten.put(31, "LOG_Y");
        int[] corruptWrites = {0};
        boolean corruptRejected = false;
        try {
            SilverwoodTrunkRollback.restore(Map.of(30, "AIR"), corruptWritten,
                    corruptWorld::get, (pos, state) -> {
                        corruptWrites[0]++;
                        corruptWorld.put(pos, state);
                        return true;
                    });
        } catch (IllegalArgumentException expected) {
            corruptRejected = true;
        }
        check(corruptRejected, "missing trunk original must reject journal");
        check(corruptWrites[0] == 0, "invalid journal must not write any blocks");
        check("LOG_X".equals(corruptWorld.get(30)), "valid prefix remains untouched");
        check("LOG_Y".equals(corruptWorld.get(31)), "invalid suffix remains untouched");
        System.out.println("M12 Silverwood rollback failure injection: OK (" + checks + " assertions)");
    }
}
