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
        System.out.println("SilverwoodCanopyRollbackTest: OK (" + checks + " assertions)");
    }
}
