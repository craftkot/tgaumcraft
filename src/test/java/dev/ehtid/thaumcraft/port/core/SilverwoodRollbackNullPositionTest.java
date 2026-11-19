package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Corrupt rollback journals must be rejected before any world access. */
public final class SilverwoodRollbackNullPositionTest {
    public static void main(String[] args) {
        Map<Integer, String> trunkOriginals = new LinkedHashMap<>();
        Map<Integer, String> trunkWritten = new LinkedHashMap<>();
        trunkOriginals.put(1, "AIR");
        trunkWritten.put(1, "LOG_X");
        trunkWritten.put(null, "LOG_Y");
        int[] reads = {0};
        int[] writes = {0};
        boolean rejected = false;
        try {
            SilverwoodTrunkRollback.restore(trunkOriginals, trunkWritten,
                    pos -> { reads[0]++; return "LOG_X"; },
                    (pos, state) -> { writes[0]++; return true; });
        } catch (NullPointerException expected) {
            rejected = true;
        }
        if (!rejected || reads[0] != 0 || writes[0] != 0) {
            throw new AssertionError("Corrupt trunk position reached world access");
        }

        Map<Integer, String> canopyOriginals = new LinkedHashMap<>();
        canopyOriginals.put(2, "FLOWER");
        canopyOriginals.put(null, "AIR");
        rejected = false;
        try {
            SilverwoodCanopyRollback.restore(canopyOriginals, "LEAVES",
                    pos -> { reads[0]++; return "LEAVES"; },
                    (pos, state) -> { writes[0]++; return true; });
        } catch (NullPointerException expected) {
            rejected = true;
        }
        if (!rejected || reads[0] != 0 || writes[0] != 0) {
            throw new AssertionError("Corrupt canopy position reached world access");
        }
        // Null original states are equally corrupt and must fail before reads/writes.
        Map<Integer, String> trunkNullOriginal = new LinkedHashMap<>();
        Map<Integer, String> trunkNullWritten = new LinkedHashMap<>();
        trunkNullOriginal.put(3, "DIRT");
        trunkNullOriginal.put(4, null);
        trunkNullWritten.put(3, "LOG_X");
        trunkNullWritten.put(4, "LOG_Y");
        rejected = false;
        try {
            SilverwoodTrunkRollback.restore(trunkNullOriginal, trunkNullWritten,
                    pos -> { reads[0]++; return "LOG_X"; },
                    (pos, state) -> { writes[0]++; return true; });
        } catch (NullPointerException expected) {
            rejected = true;
        }
        if (!rejected || reads[0] != 0 || writes[0] != 0) {
            throw new AssertionError("Null trunk original reached world access");
        }

        Map<Integer, String> canopyNullOriginal = new LinkedHashMap<>();
        canopyNullOriginal.put(5, "FLOWER");
        canopyNullOriginal.put(6, null);
        rejected = false;
        try {
            SilverwoodCanopyRollback.restore(canopyNullOriginal, "LEAVES",
                    pos -> { reads[0]++; return "LEAVES"; },
                    (pos, state) -> { writes[0]++; return true; });
        } catch (NullPointerException expected) {
            rejected = true;
        }
        if (!rejected || reads[0] != 0 || writes[0] != 0) {
            throw new AssertionError("Null canopy original reached world access");
        }
        System.out.println("M12 Silverwood null-position rollback: OK (4 cases)");
    }
}
