package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** BETA26 scan boundaries, arbitrary Ext symmetry, and mirrored eligibility quirks. */
public final class InfusionSurroundingsParityTest {
    private static int checks;

    private static void eq(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", actual " + actual);
    }

    private static void near(float expected, float actual) {
        checks++;
        if (Math.abs(expected - actual) > 0.000001F)
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }

    private static LegacyInfusionMatrixLayout.Offset at(int x, int y, int z) {
        return new LegacyInfusionMatrixLayout.Offset(x, y, z);
    }

    private static LegacyInfusionSurroundings.Sample sample(String key, float amount) {
        return new LegacyInfusionSurroundings.Sample(key, amount, true, .3F);
    }

    private static LegacyInfusionStability.Balance balance(
            Map<LegacyInfusionMatrixLayout.Offset, LegacyInfusionSurroundings.Sample> world) {
        return LegacyInfusionSurroundings.balance(0, 64, 0, world::get, (one, mirror) -> false);
    }

    public static void main(String[] args) {
        var scan = LegacyInfusionSurroundings.scanOffsets();
        eq(3168, scan.size());
        eq(at(-8, 3, -8), scan.getFirst());
        eq(at(-8, -7, -8), scan.get(10));
        eq(at(-8, 3, -7), scan.get(11));
        eq(at(8, -7, 8), scan.getLast());
        eq(false, scan.stream().anyMatch(pos -> pos.x() == 0 && pos.z() == 0));
        eq(0L, LegacyInfusionSurroundings.legacyPositionKey(0, 0, 0));
        eq(1L << 38, LegacyInfusionSurroundings.legacyPositionKey(1, 0, 0));
        eq(1L << 26, LegacyInfusionSurroundings.legacyPositionKey(0, 1, 0));
        eq(1L, LegacyInfusionSurroundings.legacyPositionKey(0, 0, 1));

        Map<LegacyInfusionMatrixLayout.Offset, LegacyInfusionSurroundings.Sample> world = new HashMap<>();
        world.put(at(2, -2, 1), sample("candle", .1F));
        world.put(at(-2, -2, -1), sample("candle", .1F));
        world.put(at(3, -2, 1), sample("candle", .1F));
        world.put(at(-3, -2, -1), sample("candle", .1F));
        near(.175F, balance(world).replenishment());
        eq(List.of(), balance(world).problemPositions());

        // Ext penalties may compare arbitrary world state, rather than item occupancy.
        var penalty = LegacyInfusionSurroundings.balance(0, 64, 0, world::get,
                (one, mirror) -> Math.abs(one.x()) == 2);
        near(-.2F, penalty.replenishment());
        eq(1, penalty.problemPositions().size());

        // A rejected mirror is still read directly by the source pair comparison.
        world.clear();
        world.put(at(2, -2, 1), sample("extension", .4F));
        world.put(at(-2, -2, -1), new LegacyInfusionSurroundings.Sample("extension", .4F, false, 0));
        near(.4F, balance(world).replenishment());
        world.put(at(-2, -2, -1), sample("extension", .2F));
        near(-.4F, balance(world).replenishment());
        world.remove(at(-2, -2, -1));
        near(-.4F, balance(world).replenishment());

        // Even a zero-strength ordinary pedestal loses default .1 against air.
        world.clear();
        world.put(at(2, -2, 1), sample("pedestal_arcane", 0));
        near(-.1F, balance(world).replenishment());

        // Same y is mandatory; scan boundary and central-column exclusions apply before pairing.
        world.put(at(-2, -3, -1), sample("pedestal_arcane", 0));
        near(-.2F, balance(world).replenishment());
        world.clear();
        world.put(at(0, -2, 0), sample("stabilizer", 100));
        world.put(at(9, -2, 0), sample("stabilizer", 100));
        world.put(at(2, 4, 0), sample("stabilizer", 100));
        world.put(at(2, -8, 0), sample("stabilizer", 100));
        near(0, balance(world).replenishment());
        System.out.println("BETA26 infusion surroundings: OK (" + checks + " assertions)");
    }
}
