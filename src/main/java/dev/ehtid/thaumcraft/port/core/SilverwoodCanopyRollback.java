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
        int restored = 0;
        for (Map.Entry<P, S> entry : originals.entrySet()) {
            if (writtenLeaves.equals(currentState.apply(entry.getKey()))
                    && setState.test(entry.getKey(), entry.getValue())) {
                restored++;
            }
        }
        return restored;
    }
}
