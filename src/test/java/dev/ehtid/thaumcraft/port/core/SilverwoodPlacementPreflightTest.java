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
        check(SilverwoodPlacementPreflight.mayPlace(4,64,8,7,0,0,-64,320,soil,air), false);
        check(SilverwoodPlacementPreflight.mayPlace(8,64,12,7,0,0,-64,320,soil,air), false);
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
        System.out.println("M12 SilverwoodPlacementPreflight: OK (" + assertions + " assertions)");
    }
}
