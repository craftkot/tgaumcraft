package dev.ehtid.thaumcraft.port.core;

import java.util.Random;

/** Verifies the exact RNG API call order, not NeoForge world generation. */
public final class LegacyTreeAttemptRngSequenceTest {
    private static int assertions;
    private static void check(boolean value, String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }

    private static final class Sequence extends Random {
        private final StringBuilder calls = new StringBuilder();
        private final boolean missTreeRoll;
        Sequence() { this(false); }
        Sequence(boolean missTreeRoll) { this.missTreeRoll = missTreeRoll; }
        @Override public int nextInt(int bound) {
            calls.append("I").append(bound).append(';');
            return switch (bound) {
                case 80 -> missTreeRoll ? 0 : 3;
                case 25 -> missTreeRoll ? 0 : 7;
                case 9 -> 4;
                case 8 -> 0;
                default -> throw new AssertionError("Unexpected RNG bound: " + bound);
            };
        }
        @Override public float nextFloat() {
            calls.append("F;");
            return 0.75F;
        }
        String calls() { return calls.toString(); }
    }

    public static void main(String[] args) {
        Sequence silverwood = new Sequence();
        check(LegacyTreeAttempt.silverwoodRoll(silverwood), "silverwood roll");
        check(LegacyTreeAttempt.centeredCoordinate(-2, silverwood) == -24,
                "negative chunk centered coordinate");
        check(!LegacyTreeAttempt.silverwoodBiome(1F, false, false, 0, silverwood),
                "silverwood biome rejection");
        check("I80;I9;F;".equals(silverwood.calls()), "silverwood call order");

        Sequence greatwood = new Sequence();
        check(LegacyTreeAttempt.greatwoodRoll(greatwood), "greatwood roll");
        check(LegacyTreeAttempt.centeredCoordinate(2, greatwood) == 40,
                "positive chunk centered coordinate");
        check(LegacyTreeAttempt.greatwoodBiome(1F, greatwood), "greatwood biome acceptance");
        check(LegacyTreeAttempt.spiderGreatwood(greatwood), "spider variant");
        check("I25;I9;F;I8;".equals(greatwood.calls()), "greatwood call order");

        // A rejected Greatwood biome must not consume the spider-variant roll.
        Sequence rejectedGreatwood = new Sequence();
        check(!LegacyTreeAttempt.greatwoodBiome(0.75F, rejectedGreatwood),
                "greatwood strict threshold rejection");
        check("F;".equals(rejectedGreatwood.calls()),
                "rejected greatwood biome does not draw spider variant");

        // Magical fallback still consumes the initial biome float exactly once.
        Sequence magicalSilverwood = new Sequence();
        check(LegacyTreeAttempt.silverwoodBiome(0F, true, false, 0, magicalSilverwood),
                "magical silverwood fallback");
        check("F;".equals(magicalSilverwood.calls()),
                "magical silverwood fallback consumes exactly one float");

        // A rejected tree roll must not draw coordinates or biome support.
        Sequence missedSilverwood = new Sequence(true);
        check(!LegacyTreeAttempt.silverwoodRoll(missedSilverwood), "silverwood roll rejection");
        check("I80;".equals(missedSilverwood.calls()), "silverwood rejection RNG count");

        Sequence missedGreatwood = new Sequence(true);
        check(!LegacyTreeAttempt.greatwoodRoll(missedGreatwood), "greatwood roll rejection");
        check("I25;".equals(missedGreatwood.calls()), "greatwood rejection RNG count");

        Sequence overflow = new Sequence();
        boolean rejected = false;
        try {
            LegacyTreeAttempt.centeredCoordinate(Integer.MAX_VALUE, overflow);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "overflow rejected");
        check("I9;".equals(overflow.calls()), "overflow still consumes offset roll");
        // Negative overflow must fail after exactly one coordinate draw too.
        Sequence underflow = new Sequence();
        boolean underflowRejected = false;
        try {
            LegacyTreeAttempt.centeredCoordinate(Integer.MIN_VALUE, underflow);
        } catch (IllegalArgumentException expected) {
            underflowRejected = true;
        }
        check(underflowRejected, "negative chunk overflow rejected");
        check("I9;".equals(underflow.calls()), "negative overflow consumes one offset roll");

        System.out.println("M12 LegacyTreeAttempt RNG sequence: OK (" + assertions + " assertions)");
    }
}
