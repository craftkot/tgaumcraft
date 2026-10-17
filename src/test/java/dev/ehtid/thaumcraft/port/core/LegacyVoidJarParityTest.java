package dev.ehtid.thaumcraft.port.core;

public final class LegacyVoidJarParityTest {
    private static int checks;
    private static void eq(Object wanted, Object actual) {
        checks++;
        if (!java.util.Objects.equals(wanted, actual))
            throw new AssertionError("void-jar expected " + wanted + ", got " + actual);
    }
    public static void main(String[] args) {
        var empty = LegacyEssentiaJarLogic.State.empty();
        eq(32, LegacyVoidJarLogic.minimumSuction(empty));
        eq(32, LegacyVoidJarLogic.suction(empty));
        var labeled = new LegacyEssentiaJarLogic.State(null, "aer", 0);
        eq(48, LegacyVoidJarLogic.minimumSuction(labeled));
        eq(48, LegacyVoidJarLogic.suction(labeled));
        var fullLabeled = new LegacyEssentiaJarLogic.State("aer", "aer", 250);
        eq(48, LegacyVoidJarLogic.minimumSuction(fullLabeled));
        eq(32, LegacyVoidJarLogic.suction(fullLabeled));
        var first = LegacyVoidJarLogic.add(empty, "aer", 240);
        eq(240, first.consumed());
        eq(0, first.destroyed());
        eq(240, first.state().amount());
        eq("aer", first.state().aspect());
        eq(false, first.overflowed());
        var overflow = LegacyVoidJarLogic.add(first.state(), "aer", 20);
        eq(20, overflow.consumed());
        eq(10, overflow.destroyed());
        eq(250, overflow.state().amount());
        eq(true, overflow.overflowed());
        eq(32, LegacyVoidJarLogic.suction(overflow.state()));
        var onceFull = LegacyVoidJarLogic.add(overflow.state(), "aer", 10);
        eq(10, onceFull.consumed());
        eq(10, onceFull.destroyed());
        eq(250, onceFull.state().amount());
        eq(0, LegacyVoidJarLogic.add(overflow.state(), "ignis", 10).consumed());
        eq(0, LegacyVoidJarLogic.add(overflow.state(), "ignis", 10).destroyed());
        eq(0, LegacyVoidJarLogic.add(empty, "aer", 0).consumed());
        eq(0, LegacyVoidJarLogic.add(empty, "aer", -1).consumed());
        eq(0, LegacyVoidJarLogic.add(empty, null, 10).consumed());
        eq(0, LegacyVoidJarLogic.add(empty, "", 10).consumed());
        eq(empty, LegacyVoidJarLogic.add(empty, "aer", 0).state());
        var filtered = LegacyVoidJarLogic.add(labeled, "aer", 251);
        eq(251, filtered.consumed());
        eq(1, filtered.destroyed());
        eq("aer", filtered.state().filter());
        eq(250, filtered.state().amount());
        // Low-level BETA26 add ignores the label. Server adapters must check it.
        eq(1, LegacyVoidJarLogic.add(labeled, "ignis", 1).consumed());
        eq(false, LegacyEssentiaJarLogic.accepts(labeled, "ignis"));
        eq(true, LegacyEssentiaJarLogic.accepts(labeled, "aer"));
        // A huge offered amount must never overflow integer arithmetic.
        var huge = LegacyVoidJarLogic.add(empty, "ordo", Integer.MAX_VALUE);
        eq(Integer.MAX_VALUE, huge.consumed());
        eq(Integer.MAX_VALUE - 250, huge.destroyed());
        eq(250, huge.state().amount());
        System.out.println("BETA26 void jar parity: OK (" + checks + " assertions)");
    }
}
