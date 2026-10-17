package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Original BETA26 TileInfusionMatrix.getSurroundings includes Blocks.SKULL:
 * six skull metadata variants were one block and mirrored with each other.
 * In 1.21 separate skull/head blocks must map to the same legacy key.
 */
public final class InfusionSkullStabilityParityTest {
    private static int checks;
    private static void near(float expected, float actual) {
        checks++;
        if (Math.abs(expected - actual) > 0.00001F)
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }
    private static LegacyInfusionMatrixLayout.Offset at(int x, int y, int z) {
        return new LegacyInfusionMatrixLayout.Offset(x, y, z);
    }
    private static LegacyInfusionStability.Stabilizer skull() {
        return new LegacyInfusionStability.Stabilizer("minecraft:skull", 0.1F, false, 0);
    }
    public static void main(String[] args) {
        Map<LegacyInfusionMatrixLayout.Offset, LegacyInfusionStability.Stabilizer> pair =
                new HashMap<>();
        pair.put(at(2, -2, 1), skull());
        pair.put(at(-2, -2, -1), skull());
        var balance = LegacyInfusionStability.pedestalBalance(pair);
        near(0.1F, balance.replenishment());
        eq(0, balance.problemPositions().size());

        // A SECOND symmetric pair of any skull variants gets the BETA26 0.75
        // diminishing return, since 1.12 treated all skull metadata as one block.
        pair.put(at(3, -1, 2), skull());
        pair.put(at(-3, -1, -2), skull());
        near(0.175F, LegacyInfusionStability.pedestalBalance(pair).replenishment());

        pair.remove(at(-2, -2, -1));
        var lost = LegacyInfusionStability.pedestalBalance(pair);
        near(-0.025F, lost.replenishment());
        eq(1, lost.problemPositions().size());

        pair.put(at(-2, -2, -1), new LegacyInfusionStability.Stabilizer(
                "thaumcraft:candle_white", 0.1F, false, 0));
        var mismatch = LegacyInfusionStability.pedestalBalance(pair);
        near(-0.025F, mismatch.replenishment());
        eq(1, mismatch.problemPositions().size());

        pair.put(at(-2, -3, -1), skull());
        pair.remove(at(-2, -2, -1));
        near(-0.125F, LegacyInfusionStability.pedestalBalance(pair).replenishment());

        near(0.1F, LegacyInfusionStability.DEFAULT_UNMATCHED_STRENGTH);
        System.out.println("BETA26 skull infusion stabilizers: OK (" + checks + " assertions)");
    }
}
