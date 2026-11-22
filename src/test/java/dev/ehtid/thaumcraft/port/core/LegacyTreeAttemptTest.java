package dev.ehtid.thaumcraft.port.core;

import java.util.Random;

/** Deterministic source-era attempt and RNG-order checks, not a game test. */
public final class LegacyTreeAttemptTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    private static final class Scripted extends Random {
        private final int[] ints;
        private final float[] floats;
        private int i, f;
        Scripted(int[] ints, float[] floats) { this.ints = ints; this.floats = floats; }
        @Override public int nextInt(int bound) {
            if (i >= ints.length) throw new AssertionError("unexpected nextInt(" + bound + ")");
            int v = ints[i++];
            if (v < 0 || v >= bound) throw new AssertionError("invalid scripted bound");
            return v;
        }
        @Override public float nextFloat() {
            if (f >= floats.length) throw new AssertionError("unexpected nextFloat");
            return floats[f++];
        }
    }
    public static void main(String[] args) {
        for (int n = 0; n < 80; n++)
            check(LegacyTreeAttempt.silverwoodRoll(new Scripted(new int[]{n}, new float[0]))
                    == (n == 3), "silverwood chance " + n);
        for (int n = 0; n < 25; n++)
            check(LegacyTreeAttempt.greatwoodRoll(new Scripted(new int[]{n}, new float[0]))
                    == (n == 7), "greatwood chance " + n);
        for (int n = 0; n < 9; n++) {
            for (int chunk : new int[]{-3, -1, 0, 1, 3}) {
                check(LegacyTreeAttempt.centeredCoordinate(chunk,
                        new Scripted(new int[]{n}, new float[0]))
                        == chunk * 16 + 4 + n, "centered offset");
            }
        }
        check(LegacyTreeAttempt.greatwoodBiome(0.6F,
                new Scripted(new int[0], new float[]{0.59F})), "support accepted");
        check(!LegacyTreeAttempt.greatwoodBiome(0.6F,
                new Scripted(new int[0], new float[]{0.6F})), "support strict");
        check(LegacyTreeAttempt.silverwoodBiome(0F, true, false, 0,
                new Scripted(new int[0], new float[]{0.8F})), "magical");
        check(!LegacyTreeAttempt.silverwoodBiome(0F, true, true, 0,
                new Scripted(new int[0], new float[]{0.8F})), "forest excluded");
        for (int id : new int[]{18, 28})
            check(LegacyTreeAttempt.silverwoodBiome(0F, false, false, id,
                    new Scripted(new int[0], new float[]{0.8F})), "legacy biome " + id);
        check(LegacyTreeAttempt.silverwoodBiome(1F, false, false, 0,
                new Scripted(new int[0], new float[]{0.49F})), "half support");
        check(!LegacyTreeAttempt.silverwoodBiome(1F, false, false, 0,
                new Scripted(new int[0], new float[]{0.5F})), "half strict");
        for (int n = 0; n < 8; n++)
            check(LegacyTreeAttempt.spiderGreatwood(
                    new Scripted(new int[]{n}, new float[0])) == (n == 0), "spider " + n);
        // Every accepted legacy override still consumes its preceding float.
        Scripted s = new Scripted(new int[0], new float[]{0.9F});
        check(LegacyTreeAttempt.silverwoodBiome(0F, true, false, 18, s) && s.f == 1,
                "legacy override RNG consumption");
        // A centered coordinate is valid at the last representable chunk edge,
        // and the random offset must be consumed before the overflow guard.
        int minValidChunk = Math.ceilDiv(Integer.MIN_VALUE - 4, 16);
        int maxValidChunk = Math.floorDiv(Integer.MAX_VALUE - 12, 16);
        for (int chunk : new int[]{minValidChunk, maxValidChunk}) {
            for (int n = 0; n < 9; n++) {
                int expected = (int) ((long) chunk * 16 + 4 + n);
                check(LegacyTreeAttempt.centeredCoordinate(chunk,
                        new Scripted(new int[]{n}, new float[0])) == expected,
                        "representable chunk edge " + chunk + " offset " + n);
            }
        }
        for (int chunk : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            boolean thrown = false;
            try { LegacyTreeAttempt.centeredCoordinate(chunk,
                    new Scripted(new int[]{4}, new float[0])); }
            catch (IllegalArgumentException expected) { thrown = true; }
            check(thrown, "overflow rejected");
        }
        System.out.println("M12 LegacyTreeAttempt: OK (" + assertions + " assertions)");
    }
}
