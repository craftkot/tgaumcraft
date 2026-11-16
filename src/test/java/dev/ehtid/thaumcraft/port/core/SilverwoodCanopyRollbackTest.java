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

    private static void checkThrowsNull(Runnable action, String message) {
        boolean threw = false;
        try {
            action.run();
        } catch (NullPointerException expected) {
            threw = true;
        }
        check(threw, message);
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
        // Invalid callback dependencies must fail before reading or writing the world.
        checkThrowsNull(() -> SilverwoodCanopyRollback.restore(null, "LEAVES",
                pos -> "LEAVES", (pos, state) -> true), "null originals");
        checkThrowsNull(() -> SilverwoodCanopyRollback.restore(Map.of(), null,
                pos -> "LEAVES", (pos, state) -> true), "null leaf state");
        checkThrowsNull(() -> SilverwoodCanopyRollback.restore(Map.of(), "LEAVES",
                null, (pos, state) -> true), "null state reader");
        checkThrowsNull(() -> SilverwoodCanopyRollback.restore(Map.of(), "LEAVES",
                pos -> "LEAVES", null), "null state writer");
        // A rejected leaf write must not affect any other recorded position.
        Map<String, String> rejectedWorld = new HashMap<>();
        rejectedWorld.put("one", "LEAVES");
        rejectedWorld.put("two", "STONE");
        int[] rejectedWrites = {0};
        int rejectedRestores = SilverwoodCanopyRollback.restore(
                new LinkedHashMap<>(Map.of("one", "AIR", "two", "AIR")), "LEAVES",
                rejectedWorld::get, (pos, state) -> {
                    rejectedWrites[0]++;
                    return false;
                });
        check(rejectedRestores == 0, "rejected write is not counted");
        check(rejectedWrites[0] == 1, "foreign position never reaches rejected writer");
        check("LEAVES".equals(rejectedWorld.get("one"))
                && "STONE".equals(rejectedWorld.get("two")), "rejected write leaves world intact");
        // Re-read each position at rollback time: a previous callback may change
        // a later position to a foreign block. Never use a stale snapshot.
        Map<String, String> changingWorld = new HashMap<>();
        changingWorld.put("first", "LEAVES");
        changingWorld.put("later", "LEAVES");
        Map<String, String> changingOriginals = new LinkedHashMap<>();
        changingOriginals.put("first", "AIR");
        changingOriginals.put("later", "AIR");
        int[] changingWrites = {0};
        int changingRestored = SilverwoodCanopyRollback.restore(
                changingOriginals, "LEAVES", changingWorld::get,
                (pos, state) -> {
                    changingWrites[0]++;
                    changingWorld.put(pos, state);
                    if (pos.equals("first")) changingWorld.put("later", "STONE");
                    return true;
                });
        check(changingRestored == 1, "only first leaf restored after intervening foreign edit");
        check(changingWrites[0] == 1, "later foreign block must not reach writer");
        check("AIR".equals(changingWorld.get("first")), "first leaf restored");
        check("STONE".equals(changingWorld.get("later")), "preserve foreign edit during rollback");
        // A rejected first restoration must not abort cleanup of later leaves.
        Map<String, String> partialWorld = new LinkedHashMap<>();
        partialWorld.put("rejected", "LEAVES");
        partialWorld.put("accepted", "LEAVES");
        Map<String, String> partialOriginals = new LinkedHashMap<>();
        partialOriginals.put("rejected", "WATER");
        partialOriginals.put("accepted", "AIR");
        int[] partialAttempts = {0};
        int partialRestored = SilverwoodCanopyRollback.restore(
                partialOriginals, "LEAVES", partialWorld::get, (pos, state) -> {
                    partialAttempts[0]++;
                    if (pos.equals("rejected")) return false;
                    partialWorld.put(pos, state);
                    return true;
                });
        check(partialRestored == 1, "continue after an earlier rejected restore");
        check(partialAttempts[0] == 2, "attempt later leaf after failed write");
        check("LEAVES".equals(partialWorld.get("rejected")), "failed first write remains unchanged");
        check("AIR".equals(partialWorld.get("accepted")), "later successful write restores air");
        System.out.println("SilverwoodCanopyRollbackTest: OK (" + checks + " assertions)");
    }
}
