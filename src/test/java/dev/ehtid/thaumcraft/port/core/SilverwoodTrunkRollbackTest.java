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
        System.out.println("SilverwoodTrunkRollbackTest: OK (6 assertions)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
