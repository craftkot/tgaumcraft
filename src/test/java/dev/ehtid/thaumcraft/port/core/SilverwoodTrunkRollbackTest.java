package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SilverwoodTrunkRollbackTest {
    public static void main(String[] args) {
        Map<Integer, String> originals = new LinkedHashMap<>();
        originals.put(1, "AIR");
        originals.put(2, "WATER");
        originals.put(3, "LEAVES");
        Map<Integer, String> written = new LinkedHashMap<>();
        written.put(1, "LOG_Y");
        written.put(2, "LOG_X");
        written.put(3, "LOG_Z");
        Map<Integer, String> world = new HashMap<>(written);
        world.put(2, "STONE");
        int restored = SilverwoodTrunkRollback.restore(originals, written, world::get,
                (pos, state) -> { world.put(pos, state); return true; });
        check(restored == 2, "Only matching own logs restored");
        check(world.get(1).equals("AIR"), "Air restored");
        check(world.get(2).equals("STONE"), "Foreign block preserved");
        check(world.get(3).equals("LEAVES"), "Leaf restored");
        Map<Integer, String> invalid = Map.of(4, "LOG_Y");
        boolean rejected = false;
        try {
            SilverwoodTrunkRollback.restore(originals, invalid, world::get,
                    (pos, state) -> true);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Untracked originals rejected");
        check(SilverwoodTrunkRollback.restore(originals, written, world::get,
                (pos, state) -> { world.put(pos, state); return true; }) == 0,
                "Repeated rollback does not overwrite restored positions");
        // A late missing original must fail before restoring an earlier log.
        Map<Integer, String> partialOriginals = Map.of(1, "AIR");
        Map<Integer, String> twoWrites = new LinkedHashMap<>();
        twoWrites.put(1, "LOG_Y");
        twoWrites.put(4, "LOG_X");
        Map<Integer, String> partialWorld = new HashMap<>(twoWrites);
        final int[] writes = {0};
        boolean lateRejected = false;
        try {
            SilverwoodTrunkRollback.restore(partialOriginals, twoWrites, partialWorld::get,
                    (pos, state) -> { writes[0]++; partialWorld.put(pos, state); return true; });
        } catch (IllegalArgumentException expected) {
            lateRejected = true;
        }
        check(lateRejected, "Missing later original rejected");
        check(writes[0] == 0, "No world writes before complete validation");
        check(partialWorld.get(1).equals("LOG_Y"), "Earlier log remains untouched");
        // A restoration callback can change a later log position. Re-read it
        // immediately before restoring so a foreign block is never overwritten.
        Map<Integer, String> interleaved = new LinkedHashMap<>();
        interleaved.put(1, "LOG_Y");
        interleaved.put(2, "LOG_X");
        Map<Integer, String> interleavedOriginals = Map.of(1, "AIR", 2, "WATER");
        Map<Integer, String> interleavedWorld = new HashMap<>(interleaved);
        final int[] interleavedWrites = {0};
        int interleavedRestored = SilverwoodTrunkRollback.restore(
                interleavedOriginals, interleaved, interleavedWorld::get,
                (pos, state) -> {
                    interleavedWrites[0]++;
                    interleavedWorld.put(pos, state);
                    if (pos == 1) interleavedWorld.put(2, "STONE");
                    return true;
                });
        check(interleavedRestored == 1, "Only first log restored after callback mutation");
        check(interleavedWrites[0] == 1, "Foreign position receives no restoration write");
        check(interleavedWorld.get(1).equals("AIR"), "First log restored to original air");
        check(interleavedWorld.get(2).equals("STONE"), "Later foreign block preserved");
        // A rejected restore must not abort the remaining trunk cleanup.
        Map<Integer, String> rejectedWorld = new LinkedHashMap<>();
        rejectedWorld.put(1, "LOG_Y");
        rejectedWorld.put(2, "LOG_X");
        final int[] rejectedCalls = {0};
        int acceptedRestores = SilverwoodTrunkRollback.restore(
                Map.of(1, "AIR", 2, "WATER"), rejectedWorld,
                rejectedWorld::get, (pos, state) -> {
                    rejectedCalls[0]++;
                    if (pos == 1) return false;
                    rejectedWorld.put(pos, state);
                    return true;
                });
        check(acceptedRestores == 1, "Only accepted restoration counts");
        check(rejectedCalls[0] == 2, "Rejected first restoration does not abort later writes");
        check(rejectedWorld.get(1).equals("LOG_Y"), "Rejected log remains untouched");
        check(rejectedWorld.get(2).equals("WATER"), "Later log is restored despite first rejection");
        System.out.println("SilverwoodTrunkRollbackTest: OK (17 assertions)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
