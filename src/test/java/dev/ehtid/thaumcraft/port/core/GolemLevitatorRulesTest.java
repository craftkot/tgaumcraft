package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.GolemLevitatorRules;
import dev.ehtid.thaumcraft.port.core.golem.GolemLevitatorRules.Direction;

/** Independent TileLevitator BETA26 range scan, credit and force golden fixtures. */
public final class GolemLevitatorRulesTest {
    private static int assertions;
    public static void main(String[] args) {
        for (int index = 0; index < 4; index++) {
            int length = new int[]{4, 8, 16, 32}[index];
            equal(length, GolemLevitatorRules.range(index)); equal(length * 2, GolemLevitatorRules.cost(index));
            int actual = 0, counter = 0;
            for (int distance = 1; distance <= length; distance++) {
                equal(distance, GolemLevitatorRules.nextProbe(index, counter));
                var scan = GolemLevitatorRules.scan(index, actual, counter, false);
                equal(distance, scan.actualRange()); equal(distance, scan.counter());
                actual = scan.actualRange(); counter = scan.counter();
            }
            equal(1, GolemLevitatorRules.nextProbe(index, counter));
            var blocked = GolemLevitatorRules.scan(index, actual, 2, true);
            equal(3, blocked.actualRange()); equal(0, blocked.counter());
            equal(0, GolemLevitatorRules.scan(index, 0, 0, true).actualRange());
            equal(index == 3 ? 32 : 1, GolemLevitatorRules.scan(index, 32, 0, false).actualRange()); // Shorter setting resets stale range.
            equal(1 - length * 2, GolemLevitatorRules.afterEntity(1, index)); // Preserve source negative remainder.
        }
        equal(1200, GolemLevitatorRules.refill(0, 1)); equal(1209, GolemLevitatorRules.refill(9, 1));
        equal(10, GolemLevitatorRules.refill(10, 1)); equal(300, GolemLevitatorRules.refill(0, .25F));
        equal(1185, GolemLevitatorRules.refill(-15, 1));
        for (Direction direction : Direction.values()) {
            var motion = GolemLevitatorRules.motion(0, 0, 0, direction, false, true);
            close(.1F * direction.x, motion.x()); close(.1F * direction.y, motion.y()); close(.1F * direction.z, motion.z());
            var capped = GolemLevitatorRules.motion(1, -1, 1, direction, false, true);
            close(.35F, capped.x()); close(-.35F, capped.y()); close(.35F, capped.z());
        }
        var sneak = GolemLevitatorRules.motion(1, -.2, 1, Direction.UP, true, false);
        close(1, sneak.x()); close(-.2 * (double) .9F, sneak.y()); close(1, sneak.z()); // Source skips clamping for sneak-UP.
        var risingSneak = GolemLevitatorRules.motion(0, .2, 0, Direction.UP, true, false);
        close(.2, risingSneak.y());
        var horizontal = GolemLevitatorRules.motion(0, -.2, 0, Direction.EAST, false, false);
        close(.1F, horizontal.x()); close(-.2 * (double) .9F + (double) .08F, horizontal.y());
        equal(true, GolemLevitatorRules.eligible(true, false, false));
        equal(true, GolemLevitatorRules.eligible(false, true, false));
        equal(true, GolemLevitatorRules.eligible(false, false, true));
        equal(false, GolemLevitatorRules.eligible(false, false, false));
        System.out.println("BETA26 levitator rules: OK (" + assertions + " assertions)");
    }
    private static void equal(int expected, int actual) { assertions++; if (expected != actual) throw new AssertionError(expected + " != " + actual); }
    private static void equal(boolean expected, boolean actual) { assertions++; if (expected != actual) throw new AssertionError(expected + " != " + actual); }
    private static void close(double expected, double actual) { assertions++; if (Math.abs(expected - actual) > 1e-12) throw new AssertionError(expected + " != " + actual); }
}
