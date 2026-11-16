package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Best-effort rollback of successful Silverwood trunk writes with differing axis states.
 * Each position has its own expected written state and original state.
 * This does not make placement atomic or protect against equal-state foreign writes.
 */
public final class SilverwoodTrunkRollback {
    private SilverwoodTrunkRollback() {}

    public static <P, S> int restore(Map<P, S> originals, Map<P, S> written,
            Function<P, S> currentState, BiPredicate<P, S> setState) {
        Objects.requireNonNull(originals, "originals");
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(setState, "setState");
        // Validate the entire write journal before changing any world block.
        // A corrupt journal must not leave a partially rolled-back tree.
        for (P pos : written.keySet()) {
            if (!originals.containsKey(pos)) {
                throw new IllegalArgumentException("Missing original state for trunk write");
            }
        }
        int restored = 0;
        for (Map.Entry<P, S> entry : written.entrySet()) {
            P pos = entry.getKey();
            if (Objects.equals(entry.getValue(), currentState.apply(pos))
                    && setState.test(pos, originals.get(pos))) {
                restored++;
            }
        }
        return restored;
    }
}
