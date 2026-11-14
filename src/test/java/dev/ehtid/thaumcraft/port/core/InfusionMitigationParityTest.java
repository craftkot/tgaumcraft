package dev.ehtid.thaumcraft.port.core;

/** Standalone source parity tests; native fixtures exercise actual world charge/debits. */
public final class InfusionMitigationParityTest {
    private static int checks;

    private static void eq(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }

    public static void main(String[] args) {
        eq(new LegacyInfusionMitigation.Recharge(0, 0.0F), LegacyInfusionMitigation.recharge(0, 19));
        eq(new LegacyInfusionMitigation.Recharge(1, 0.25F), LegacyInfusionMitigation.recharge(0, 20));
        eq(new LegacyInfusionMitigation.Recharge(15, 0.0F), LegacyInfusionMitigation.recharge(15, 20));
        int energy = 0;
        float pollution = 0;
        for (int tick = 1; tick <= 400; tick++) {
            var next = LegacyInfusionMitigation.recharge(energy, tick);
            energy = next.energy();
            pollution += next.pollution();
        }
        eq(15, energy);
        eq(3.75F, pollution);
        eq(15, LegacyInfusionMitigation.afterMitigation(15, 16));
        eq(5, LegacyInfusionMitigation.afterMitigation(15, 10));
        eq(0, LegacyInfusionMitigation.afterMitigation(5, 5));
        eq(5, LegacyInfusionMitigation.afterMitigation(5, 10));
        eq(15, LegacyInfusionMitigation.nextCharge(0, 15, 0));
        eq(14, LegacyInfusionMitigation.nextCharge(0, 0, 15));
        eq(10, LegacyInfusionMitigation.nextCharge(0, 10, 6));
        eq(14, LegacyInfusionMitigation.nextCharge(15, 0, 0));

        // Two disconnected conductors cannot keep each other's old charge alive.
        int first = 15, second = 14;
        for (int pass = 0; pass < 20; pass++) {
            first = LegacyInfusionMitigation.nextCharge(first, 0, second);
            second = LegacyInfusionMitigation.nextCharge(second, 0, first);
        }
        eq(0, first);
        eq(0, second);

        // Exhaustively compare the reduced formula to the original bytecode branches.
        for (int current = 0; current <= 15; current++)
            for (int source = 0; source <= 15; source++)
                for (int adjacent = 0; adjacent <= 15; adjacent++) {
                    int original = current;
                    if (source > 0 && source > original - 1) original = source;
                    if (adjacent > original) original = adjacent - 1;
                    else if (original > 0) original--;
                    else original = 0;
                    if (source > original - 1) original = source;
                    eq(original, LegacyInfusionMitigation.nextCharge(current, source, adjacent));
                }
        System.out.println("BETA26 infusion mitigation: OK (" + checks + " assertions)");
    }
}
