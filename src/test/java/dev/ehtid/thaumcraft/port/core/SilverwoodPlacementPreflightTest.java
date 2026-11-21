package dev.ehtid.thaumcraft.port.core;

import java.util.concurrent.atomic.AtomicInteger;

public final class SilverwoodPlacementPreflightTest {
    private static int assertions;
    private static void check(boolean actual, boolean expected) {
        assertions++;
        if (actual != expected) throw new AssertionError("Assertion " + assertions + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        var soil = (SilverwoodPlacementPreflight.BlockPredicate) (x, y, z) -> y == 63;
        var air = (SilverwoodPlacementPreflight.BlockPredicate) (x, y, z) -> true;
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,7,0,0,-64,320,soil,air), true);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,10,0,0,-64,320,soil,air), true);
        check(SilverwoodPlacementPreflight.mayPlace(8,65,8,7,0,0,-64,320,soil,air), false);
        check(SilverwoodPlacementPreflight.mayPlace(4,64,8,7,0,0,-64,320,soil,air), true);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,12,7,0,0,-64,320,soil,air), false);\n        check(SilverwoodPlacementPreflight.mayPlace(11,64,11,7,0,0,-64,320,soil,air), true);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,11,0,0,-64,320,soil,air), false);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,7,0,0,-64,320,null,air), false);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,7,0,0,-64,320,soil,null), false);
        check(SilverwoodPlacementPreflight.mayPlace(-8,64,-8,7,-1,-1,-64,320,soil,air), true);
        for (int blocked = 0; blocked < 10; blocked++) {
            final int obstacleY = 64 + blocked;
            check(SilverwoodPlacementPreflight.mayPlace(8,64,8,10,0,0,-64,320,
                    soil,(x,y,z) -> y != obstacleY), false);
        }
        AtomicInteger reads = new AtomicInteger();
        check(SilverwoodPlacementPreflight.mayPlace(4,64,8,7,0,0,-64,320,
                (x,y,z) -> {reads.incrementAndGet();return true;},air), false);
        check(reads.get() == 0, true);
        // Invalid geometry must fail before any world predicate is invoked.
        AtomicInteger invalidReads = new AtomicInteger();
        SilverwoodPlacementPreflight.BlockPredicate counted = (x,y,z) -> {
            invalidReads.incrementAndGet();
            return true;
        };
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,6,0,0,-64,320,counted,counted), false);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,7,1,0,-64,320,counted,counted), false);
        check(SilverwoodPlacementPreflight.mayPlace(8,315,8,10,0,0,-64,320,counted,counted), false);
        check(invalidReads.get() == 0, true);

        // Soil rejection must not trigger a single trunk read.
        AtomicInteger trunkReads = new AtomicInteger();
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,7,0,0,-64,320,
                (x,y,z) -> false,
                (x,y,z) -> { trunkReads.incrementAndGet(); return true; }), false);
        check(trunkReads.get() == 0, true);
        // Successful placement reads exactly one soil block and every trunk block.
        AtomicInteger soilReads = new AtomicInteger();
        AtomicInteger fullTrunkReads = new AtomicInteger();
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,7,0,0,-64,320,
                (x,y,z) -> { soilReads.incrementAndGet(); return true; },
                (x,y,z) -> { fullTrunkReads.incrementAndGet(); return true; }), true);
        check(soilReads.get() == 1, true);
        check(fullTrunkReads.get() == 7, true);

        // First blocked trunk position must short-circuit later world reads.
        AtomicInteger blockedReads = new AtomicInteger();
        check(SilverwoodPlacementPreflight.mayPlace(8,64,8,10,0,0,-64,320,
                soil, (x,y,z) -> { blockedReads.incrementAndGet(); return y != 67; }), false);
        check(blockedReads.get() == 4, true);
        // Negative chunk coordinates must reach the exact soil and trunk positions.
        AtomicInteger negativeSoilReads = new AtomicInteger();
        AtomicInteger negativeTrunkReads = new AtomicInteger();
        check(SilverwoodPlacementPreflight.mayPlace(-8,64,-8,7,-1,-1,-64,320,
                (x,y,z) -> { negativeSoilReads.incrementAndGet(); return x == -8 && y == 63 && z == -8; },
                (x,y,z) -> {
                    int index = negativeTrunkReads.getAndIncrement();
                    return x == -8 && y == 64 + index && z == -8;
                }), true);
        check(negativeSoilReads.get() == 1, true);
        check(negativeTrunkReads.get() == 7, true);

        // At the upper build limit, the highest trunk block is still in bounds.
        AtomicInteger topReads = new AtomicInteger();
        check(SilverwoodPlacementPreflight.mayPlace(8,303,8,10,0,0,-64,320,
                (x,y,z) -> y == 302,
                (x,y,z) -> { topReads.incrementAndGet(); return y <= 312; }), true);
        check(topReads.get() == 10, true);
        System.out.println("M12 SilverwoodPlacementPreflight: OK (" + assertions + " assertions)");
    }
}
