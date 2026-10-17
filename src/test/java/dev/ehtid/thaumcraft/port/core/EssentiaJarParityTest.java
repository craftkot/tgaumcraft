package dev.ehtid.thaumcraft.port.core;

public final class EssentiaJarParityTest {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected "+expected+", actual "+actual);
    }
    private static void yes(boolean value) {
        checks++;
        if (!value) throw new AssertionError("expected true");
    }
    private static void no(boolean value) {
        checks++;
        if (value) throw new AssertionError("expected false");
    }
    public static void main(String[] args) {
        var empty = LegacyEssentiaJarLogic.State.empty();
        eq(250, LegacyEssentiaJarLogic.CAPACITY);
        eq(32, LegacyEssentiaJarLogic.suction(empty));
        eq(32, LegacyEssentiaJarLogic.minimumSuction(empty));
        eq(nullText(), empty.aspect());
        yes(LegacyEssentiaJarLogic.accepts(empty,"aer"));

        var fill = LegacyEssentiaJarLogic.add(empty,"aer",300);
        eq(250, fill.transferred());
        eq(250, fill.state().amount());
        eq("aer", fill.state().aspect());
        eq(0, LegacyEssentiaJarLogic.suction(fill.state()));
        eq(0, LegacyEssentiaJarLogic.add(fill.state(),"aer",1).transferred());
        eq(0, LegacyEssentiaJarLogic.add(fill.state(),"ignis",1).transferred());

        var take = LegacyEssentiaJarLogic.take(fill.state(),"aer",249);
        eq(249, take.transferred());
        eq(1, take.state().amount());
        eq("aer", take.state().aspect());
        eq(0, LegacyEssentiaJarLogic.take(take.state(),"aer",2).transferred());
        eq(0, LegacyEssentiaJarLogic.take(take.state(),"ignis",1).transferred());
        var last = LegacyEssentiaJarLogic.take(take.state(),"aer",1);
        eq(1, last.transferred());
        eq(0, last.state().amount());
        eq(nullText(), last.state().aspect());

        var filtered = new LegacyEssentiaJarLogic.State(null,"ignis",0);
        eq(64, LegacyEssentiaJarLogic.suction(filtered));
        eq("ignis", LegacyEssentiaJarLogic.suctionAspect(filtered));
        no(LegacyEssentiaJarLogic.accepts(filtered,"aer"));
        yes(LegacyEssentiaJarLogic.accepts(filtered,"ignis"));
        // The original low-level addToContainer ignores the label; the
        // transport adapter must call doesContainerAccept first.
        eq(1, LegacyEssentiaJarLogic.add(filtered,"aer",1).transferred());

        var half = LegacyEssentiaJarLogic.add(empty,"terra",123).state();
        eq(123,half.amount());
        eq(7,LegacyEssentiaJarLogic.add(half,"terra",7).transferred());
        eq(123, half.amount()); // immutable: a failed external debit cannot mutate jar
        eq(0,LegacyEssentiaJarLogic.add(half,"ignis",1).transferred());
        eq(0,LegacyEssentiaJarLogic.add(empty,"aer",-100).transferred());
        eq(0,LegacyEssentiaJarLogic.take(half,"terra",-1).transferred());
        eq(0, LegacyEssentiaJarLogic.comparatorOutput(empty));
        eq(1, LegacyEssentiaJarLogic.comparatorOutput(
                new LegacyEssentiaJarLogic.State("aer", null, 1)));
        eq(1, LegacyEssentiaJarLogic.comparatorOutput(
                new LegacyEssentiaJarLogic.State("aer", null, 17)));
        eq(2, LegacyEssentiaJarLogic.comparatorOutput(
                new LegacyEssentiaJarLogic.State("aer", null, 18)));
        eq(8, LegacyEssentiaJarLogic.comparatorOutput(
                new LegacyEssentiaJarLogic.State("aer", "ignis", 125)));
        eq(14, LegacyEssentiaJarLogic.comparatorOutput(
                new LegacyEssentiaJarLogic.State("aer", null, 249)));
        eq(15, LegacyEssentiaJarLogic.comparatorOutput(fill.state()));
        no(LegacyEssentiaJarLogic.connectsOnTop(0));
        yes(LegacyEssentiaJarLogic.connectsOnTop(1));
        no(LegacyEssentiaJarLogic.connectsOnTop(2));
        System.out.println("BETA26 jar parity: OK ("+checks+" assertions)");
    }
    private static String nullText() { return null; }
}
