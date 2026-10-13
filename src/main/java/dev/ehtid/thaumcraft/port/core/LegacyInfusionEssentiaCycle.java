package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Original BETA26 TileInfusionMatrix.craftCycle, essentia-drain section
 * (javap -c -p bytecode offsets 1015..1152):
 *
 * 1. If recipeEssentia.visSize() <= 0, move on to pedestal ingredients.
 * 2. In AspectList insertion order, skip entries with amount <= 0.
 * 3. EssentiaHandler.drainEssentia(tile, aspect, null, 12,
 *                                  amount > 1 ? countDelay : 0).
 * 4. On FIRST success, reduce exactly 1 and return immediately.
 * 5. On each failure, stability -= 0.25 and try the NEXT aspect.
 * 6. If none succeeded, mark checkSurroundings=true and return.
 *
 * This function invokes the caller's real drain adapter. It does not invent
 * stored essentia, fake resource drain, or complete a craft. The NeoForge
 * essentia network must be implemented before wiring this into live rituals.
 */
public final class LegacyInfusionEssentiaCycle {
    private LegacyInfusionEssentiaCycle() {}
    public static final int DRAIN_RANGE = 12;
    public static final float FAILED_DRAIN_STABILITY_LOSS = 0.25F;

    @FunctionalInterface
    public interface Drainer {
        /** Caller MUST actually debit one essentia on returning true. */
        boolean drainOne(AspectData aspect, int range, int delay);
    }

    public enum Action {
        READY_FOR_INGREDIENTS, DRAINED_ONE, FAILED_ALL
    }

    public record Result(Action action, AspectListData remaining,
                         float stability, int failedAttempts,
                         AspectData consumed, boolean checkSurroundings) {
        public Result {
            Objects.requireNonNull(action);
            remaining = remaining.copy();
            if (failedAttempts < 0) throw new IllegalArgumentException("failures");
        }
        @Override public AspectListData remaining() { return remaining.copy(); }
    }

    /**
     * Caller owns actual world resource drain, not this pure engine.
     * For source fidelity, original amount-zero entries stay as zero via
     * AspectListData.reduce rather than being removed from the map.
     */
    public static Result cycle(AspectListData requested, float stability,
                               int countDelay, Drainer drain) {
        Objects.requireNonNull(requested, "requested");
        if (!Float.isFinite(stability) || countDelay < 0)
            throw new IllegalArgumentException("Invalid infusion state");
        AspectListData remaining = requested.copy();
        if (remaining.visSize() <= 0) {
            return new Result(Action.READY_FOR_INGREDIENTS, remaining,
                    stability, 0, null, false);
        }
        Objects.requireNonNull(drain, "drain adapter required while essentia pending");
        int failed = 0;
        for (AspectData aspect : remaining.getAspects()) {
            int units = remaining.getAmount(aspect);
            if (units <= 0) continue;
            if (drain.drainOne(aspect, DRAIN_RANGE, units > 1 ? countDelay : 0)) {
                remaining.reduce(aspect, 1);
                return new Result(Action.DRAINED_ONE, remaining, stability,
                        failed, aspect, false);
            }
            stability -= FAILED_DRAIN_STABILITY_LOSS;
            failed++;
        }
        return new Result(Action.FAILED_ALL, remaining, stability,
                failed, null, true);
    }
}
