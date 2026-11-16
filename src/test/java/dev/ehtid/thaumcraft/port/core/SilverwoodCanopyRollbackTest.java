package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SilverwoodCanopyRollbackTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("air", "AIR");
        before.put("flower", "FLOWER");
        before.put("foreign", "AIR");
        before.put("failed", "AIR");
        Map<String, String> world = new HashMap<>();
        world.put("air", "LEAVES");
        world.put("flower", "LEAVES");
        world.put("foreign", "STONE");
        world.put("failed", "LEAVES");
        int[] attempted = {0};
        int restored = SilverwoodCanopyRollback.restore(before, "LEAVES",
                world::get, (pos, state) -> {
                    attempted[0]++;
                    if (pos.equals("failed")) return false;
                    world.put(pos, state);
                    return true;
                });
        check(restored == 2, "count only successful restores");
        check(attempted[0] == 3, "do not write foreign changes");
        check("AIR".equals(world.get("air")), "restore air");
        check("FLOWER".equals(world.get("flower")), "restore replaceable plant");
        check("STONE".equals(world.get("foreign")), "preserve foreign block");
        check("LEAVES".equals(world.get("failed")), "failed restore remains");
        check(SilverwoodCanopyRollback.restore(Map.of(), "LEAVES",
                pos -> "LEAVES", (pos, state) -> true) == 0, "empty originals");
        // A second pass must not undo already-restored blocks or touch foreign writes.
        int[] repeatedWrites = {0};
        int secondPass = SilverwoodCanopyRollback.restore(before, "LEAVES",
                world::get, (pos, state) -> {
                    repeatedWrites[0]++;
                    return false;
                });
        check(secondPass == 0, "failed retry must not claim restored blocks");
        check(repeatedWrites[0] == 1, "retry only the leaf whose restore failed");

        // A position changed to a non-leaf state (including an unloaded/unknown
        // state represented by null) must never be passed to the write callback.
        int[] unknownWrites = {0};
        int unknownRestores = SilverwoodCanopyRollback.restore(Map.of("missing", "AIR"),
                "LEAVES", pos -> null, (pos, state) -> {
                    unknownWrites[0]++;
                    return true;
                });
        check(unknownRestores == 0, "unknown state cannot be restored");
        check(unknownWrites[0] == 0, "unknown state must not be overwritten");
        // A successful trunk write must not be rolled back as canopy, even when
        // the same position was previously recorded as a successful leaf write.
        Map<String, String> mixedOriginals = new LinkedHashMap<>();
        mixedOriginals.put("trunk", "AIR");
        mixedOriginals.put("water", "WATER");
        mixedOriginals.put("canopy", "AIR");
        Map<String, String> mixedWorld = new HashMap<>();
        mixedWorld.put("trunk", "LOG");
        mixedWorld.put("water", "LEAVES");
        mixedWorld.put("canopy", "LEAVES");
        int[] mixedWrites = {0};
        int mixedRestored = SilverwoodCanopyRollback.restore(mixedOriginals, "LEAVES",
                mixedWorld::get, (pos, state) -> {
                    mixedWrites[0]++;
                    mixedWorld.put(pos, state);
                    return true;
                });
        check(mixedRestored == 2, "restore only remaining canopy positions");
        check(mixedWrites[0] == 2, "never overwrite the successful trunk");
        check("LOG".equals(mixedWorld.get("trunk")), "preserve trunk after canopy cleanup");
        check("WATER".equals(mixedWorld.get("water")), "restore non-air original state");
        check("AIR".equals(mixedWorld.get("canopy")), "restore remaining canopy to air");
        System.out.println("SilverwoodCanopyRollbackTest: OK (" + checks + " assertions)");
    }
}
