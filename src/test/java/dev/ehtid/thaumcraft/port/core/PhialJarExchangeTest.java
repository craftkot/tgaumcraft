package dev.ehtid.thaumcraft.port.core;

/** Material-conservation and filter tests for live phial/jar exchanges. */
public final class PhialJarExchangeTest {
    private static int checks;
    private static void eq(Object a, Object b) {
        ++checks;
        if (!java.util.Objects.equals(a, b))
            throw new AssertionError("expected " + a + " but was " + b);
    }
    public static void main(String[] args) {
        var jar = new LegacyEssentiaJarLogic.State("aer", null, 25);
        var withdraw = LegacyPhialJarExchange.withdraw(jar, "aer", 10);
        eq(10, withdraw.transferred());
        eq(15, withdraw.state().amount());
        eq(25, jar.amount()); // no mutation of the source snapshot
        eq(0, LegacyPhialJarExchange.withdraw(jar, "aer", 9).transferred());
        eq(0, LegacyPhialJarExchange.withdraw(jar, "ignis", 10).transferred());
        eq(0, LegacyPhialJarExchange.withdraw(
                new LegacyEssentiaJarLogic.State("aer", null, 9), "aer", 10).transferred());

        var returnAll = LegacyPhialJarExchange.deposit(withdraw.state(), "aer", 10);
        eq(10, returnAll.transferred());
        eq(jar, returnAll.state());
        eq(0, LegacyPhialJarExchange.deposit(jar, "ignis", 10).transferred());
        eq(0, LegacyPhialJarExchange.deposit(jar, "aer", 0).transferred());
        eq(0, LegacyPhialJarExchange.deposit(jar, "aer", 11).transferred());
        var nearlyFull = new LegacyEssentiaJarLogic.State("aer", null, 248);
        eq(0, LegacyPhialJarExchange.deposit(nearlyFull, "aer", 10).transferred());
        eq(nearlyFull, LegacyPhialJarExchange.deposit(nearlyFull, "aer", 10).state());
        eq(2, LegacyPhialJarExchange.deposit(nearlyFull, "aer", 2).transferred());

        var labeled = new LegacyEssentiaJarLogic.State(null, "ignis", 0);
        eq(0, LegacyPhialJarExchange.deposit(labeled, "aer", 10).transferred());
        eq(10, LegacyPhialJarExchange.deposit(labeled, "ignis", 10).transferred());
        eq("ignis", LegacyPhialJarExchange.deposit(labeled, "ignis", 10)
                .state().filter());
        var last = LegacyPhialJarExchange.withdraw(
                new LegacyEssentiaJarLogic.State("ignis", "ignis", 10),
                "ignis", 10);
        eq(0, last.state().amount());
        eq(null, last.state().aspect());
        eq("ignis", last.state().filter());
        eq(0, LegacyPhialJarExchange.deposit(last.state(), "aer", 1).transferred());
        eq(0, LegacyPhialJarExchange.deposit(last.state(), null, 1).transferred());

        System.out.println("BETA26 phial/jar conservation: OK (" + checks + " checks)");
    }
}
