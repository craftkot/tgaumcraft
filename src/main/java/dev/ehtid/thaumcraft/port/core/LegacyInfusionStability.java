package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source: BETA26 TileInfusionMatrix.getSurroundings(), calcDeminishingReturns(),
 * updateEntity(). This class contains ONLY the verified parts which the
 * currently registered pillar/pedestal variants can support.
 *
 * Other IInfusionStabiliser blocks, InfusionMatrix corner speed/cost glyphs,
 * vis consumption and craftCycle are future integrations, not simulated here.
 */
public final class LegacyInfusionStability {
    private LegacyInfusionStability() {}

    public static final int CAP = 25;
    public static final float DEFAULT_UNMATCHED_STRENGTH = 0.1F;
    public static final float ELD_STRENGTH = 0.1F;
    public static final float SYMMETRY_PENALTY = 0.1F;

    public enum Pillar { ARCANE, ANCIENT, ELDRITCH, MISSING }
    public record Modifiers(int cycleTime, float essentiaCostMult,
                            float replenish, int countDelay) {}
    public record Stabilizer(String blockId, float strength, boolean occupied,
                             float symmetryPenalty) {
        public Stabilizer {
            if (blockId == null || blockId.isBlank()) throw new IllegalArgumentException("blockId");
        }
    }
    public record Balance(float replenishment, List<LegacyInfusionMatrixLayout.Offset> problemPositions) {
        public Balance {
            problemPositions = List.copyOf(problemPositions);
        }
    }

    /** All four matching Ancient OR Eldritch pillars modify infusion. */
    public static Modifiers cornerModifiers(List<Pillar> corners) {
        if (corners == null || corners.size() != 4 || corners.stream().anyMatch(java.util.Objects::isNull)
                || corners.contains(Pillar.MISSING))
            throw new IllegalArgumentException("Matrix requires four pillars");
        int cycle = 10;
        float cost = 1.0F;
        float regen = 0.0F;
        if (corners.stream().allMatch(p -> p == Pillar.ANCIENT)) {
            cycle -= 1;
            cost -= 0.1F;
            regen -= 0.1F;
        }
        if (corners.stream().allMatch(p -> p == Pillar.ELDRITCH)) {
            cycle -= 3;
            cost += 0.05F;
            regen += 0.2F;
        }
        return new Modifiers(cycle, cost, regen, cycle / 2);
    }

    /**
     * Each of the four speed/cost glyph slots is 1 block BELOW its pillar.
     * Source BETA26 matrixSpeed: cycleTime-1,cost+0.01.
     * matrixCost: cycleTime+1,cost-0.02.
     */
    public enum Glyph { NONE, SPEED, COST }
    public static Modifiers withGlyphs(Modifiers m, List<Glyph> glyphs) {
        if (m == null || glyphs == null || glyphs.size() != 4 || glyphs.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Expected four glyph blocks");
        int cycle = m.cycleTime();
        float cost = m.essentiaCostMult();
        for (Glyph glyph : glyphs) {
            if (glyph == Glyph.SPEED) { cycle--; cost += 0.01F; }
            if (glyph == Glyph.COST) { cycle++; cost -= 0.02F; }
        }
        return new Modifiers(cycle, cost, m.replenish(), cycle / 2);
    }

    /**
     * Source gets a cost adjustment for EACH scanned pedestal; its inventory
     * is not used to decide this adjustment.
     */
    public static Modifiers withPedestals(Modifiers m, int ancientCount, int eldritchCount) {
        if (ancientCount < 0 || eldritchCount < 0)
            throw new IllegalArgumentException("negative pedestal count");
        float cost = m.essentiaCostMult();
        for (int i = 0; i < ancientCount; i++) cost -= 0.01F;
        for (int i = 0; i < eldritchCount; i++) cost += 0.0025F;
        return new Modifiers(m.cycleTime(), cost, m.replenish(), m.countDelay());
    }

    /** Original diminishing return: v * 0.75 ^ previous matched pairs. */
    public static float diminishing(float strength, int previousPairsOfSameBlock) {
        if (previousPairsOfSameBlock < 0) throw new IllegalArgumentException("count");
        return (float) (strength * Math.pow(0.75D, previousPairsOfSameBlock));
    }

    /**
     * Only supported BlockPedestal IInfusionStabiliserExt pairs for now.
     * Original opposite across x/z, SAME relative y. A missing mirror has
     * implicit strength 0.1, even opposite an ordinary Arcane pedestal.
     */
    public static Balance pedestalBalance(
            Map<LegacyInfusionMatrixLayout.Offset, Stabilizer> stabilizers) {
        if (stabilizers == null) throw new IllegalArgumentException("stabilizers");
        float value = 0;
        List<LegacyInfusionMatrixLayout.Offset> problem = new ArrayList<>();
        Map<String,Integer> matchedCounts = new HashMap<>();
        List<LegacyInfusionMatrixLayout.Offset> positions = new ArrayList<>(stabilizers.keySet());
        positions.sort(Comparator.comparingInt(LegacyInfusionMatrixLayout.Offset::x)
                .thenComparingInt(LegacyInfusionMatrixLayout.Offset::z)
                .thenComparingInt(LegacyInfusionMatrixLayout.Offset::y));
        java.util.Set<LegacyInfusionMatrixLayout.Offset> visited = new java.util.HashSet<>();
        for (var position : positions) {
            var mirror = new LegacyInfusionMatrixLayout.Offset(-position.x(), position.y(),
                    -position.z());
            // The mirror may be absent; don't drop right-hand unmatched blocks.
            if (!visited.add(position)) continue;
            visited.add(mirror);
            Stabilizer first = stabilizers.get(position);
            Stabilizer second = stabilizers.get(mirror);
            float strengthFirst = first.strength();
            float strengthSecond = second == null ? DEFAULT_UNMATCHED_STRENGTH : second.strength();
            if (second != null && first.blockId().equals(second.blockId())
                    && Float.compare(strengthFirst, strengthSecond) == 0) {
                if (first.occupied() != second.occupied()) {
                    value -= first.symmetryPenalty();
                    problem.add(position);
                } else {
                    int previous = matchedCounts.getOrDefault(first.blockId(), 0);
                    value += diminishing(strengthFirst, previous);
                    matchedCounts.put(first.blockId(), previous + 1);
                }
            } else {
                value -= Math.max(strengthFirst, strengthSecond);
                problem.add(position);
            }
        }
        return new Balance(value, problem);
    }

    /** Original updateEntity, idle path; clamped against 25 stability cap. */
    public static float idleRecharge(float current, int tick, boolean active,
                                     boolean crafting, int countDelay,
                                     float stabilityReplenish) {
        if (!active || crafting || current >= CAP || tick <= 0
                || tick % Math.max(5, countDelay) != 0) return current;
        return Math.min(CAP, current + Math.max(0.1F, stabilityReplenish));
    }

    public enum Rating { VERY_STABLE, STABLE, UNSTABLE, VERY_UNSTABLE }
    public static Rating rating(float stability, int cap) {
        if (stability > cap / 2) return Rating.VERY_STABLE;
        if (stability >= 0) return Rating.STABLE;
        if (stability > -25) return Rating.UNSTABLE;
        return Rating.VERY_UNSTABLE;
    }
}
