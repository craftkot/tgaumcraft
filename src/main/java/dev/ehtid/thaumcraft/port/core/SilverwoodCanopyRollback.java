package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Best-effort cleanup for a failed canopy-only tree attempt.
 * Restores only positions still containing the leaf state written by this attempt.
 * This is not atomic and cannot identify concurrent equal-state replacements.
 */
public final class SilverwoodCanopyRollback {
    private SilverwoodCanopyRollback() {}

    public static <P, S> int restore(Map<P, S> originals, S writtenLeaves,
                                      Function<P, S> currentState,
                                      BiPredicate<P, S> setState) {
        Objects.requireNonNull(originals, "originals");
        Objects.requireNonNull(writtenLeaves, "writtenLeaves");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(setState, "setState");
        // Validate the full journal before any world mutation. A null original
        // is not a valid BlockState and must not cause a partial rollback.
        for (Map.Entry<P, S> entry : originals.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "canopy position");
            Objects.requireNonNull(entry.getValue(), "original canopy state");
        }
        int restored = 0;
        RuntimeException firstFailure = null;
        for (Map.Entry<P, S> entry : originals.entrySet()) {
            try {
                if (writtenLeaves.equals(currentState.apply(entry.getKey()))
                        && setState.test(entry.getKey(), entry.getValue())) {
                    restored++;
                }
            } catch (RuntimeException failure) {
                // One failing position must not strand unrelated leaves.
                if (firstFailure == null) firstFailure = failure;
                else if (firstFailure != failure) firstFailure.addSuppressed(failure);
            }
        }
        if (firstFailure != null) throw firstFailure;
        return restored;
    }
}
