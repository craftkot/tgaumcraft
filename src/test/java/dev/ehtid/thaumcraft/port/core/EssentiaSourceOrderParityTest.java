package dev.ehtid.thaumcraft.port.core;

import java.util.HashSet;
import java.util.List;

public final class EssentiaSourceOrderParityTest {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected=" + expected + " actual=" + actual);
    }
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("expected true");
    }

    public static void main(String[] args) {
        var one = LegacyEssentiaSourceOrder.nullFacing(1);
        eq(17, one.size()); // 3 * 3 * 2 minus the original matrix position
        eq(17, new HashSet<>(one).size()); // no duplicate source candidates
        var expectedFirst = List.of(
                new LegacyEssentiaSourceOrder.Offset(-1, 0, 0),
                new LegacyEssentiaSourceOrder.Offset(0, 0, -1),
                new LegacyEssentiaSourceOrder.Offset(0, -1, 0),
                new LegacyEssentiaSourceOrder.Offset(0, 0, 1),
                new LegacyEssentiaSourceOrder.Offset(1, 0, 0));
        eq(expectedFirst, one.subList(0, 5));
        int previous = 0;
        for (var pos : one) {
            check(pos.distanceSquared() >= previous);
            previous = pos.distanceSquared();
            check(pos.y() >= -1 && pos.y() < 1);
            check(!(pos.x() == 0 && pos.y() == 0 && pos.z() == 0));
        }
        var twelve = LegacyEssentiaSourceOrder.nullFacing(12);
        eq(14999, twelve.size());
        eq(14999, new HashSet<>(twelve).size());
        check(twelve.contains(new LegacyEssentiaSourceOrder.Offset(12, 11, 12)));
        check(!twelve.contains(new LegacyEssentiaSourceOrder.Offset(12, 12, 12)));
        check(!twelve.contains(new LegacyEssentiaSourceOrder.Offset(0, 0, 0)));
        for (int i = 1; i < twelve.size(); i++) {
            check(twelve.get(i-1).distanceSquared() <= twelve.get(i).distanceSquared());
        }
        eq(twelve, LegacyEssentiaSourceOrder.nullFacing(12)); // stable cached order
        try {
            twelve.clear();
            throw new AssertionError("mutable source cache");
        } catch (UnsupportedOperationException expected) { checks++; }
        for (int range : new int[] {0, -1, 33}) {
            try {
                LegacyEssentiaSourceOrder.nullFacing(range);
                throw new AssertionError("bad range allowed: " + range);
            } catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("BETA26 essentia source search parity: OK (" + checks + " assertions)");
    }
}
