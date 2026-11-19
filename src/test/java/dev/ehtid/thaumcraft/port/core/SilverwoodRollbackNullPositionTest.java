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
        System.out.println("M12 Silverwood null-position rollback: OK (2 cases)");
    }
}
