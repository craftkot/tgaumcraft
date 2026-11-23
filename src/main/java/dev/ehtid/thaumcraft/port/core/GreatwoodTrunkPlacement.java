package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-neutral transactional placement for a precomputed Greatwood trunk.
 * The adapter must perform synchronous reads/writes and return false on rejected writes.
 * This is not a NeoForge Feature and cannot guarantee rollback if the world rejects restores.
 */
public final class GreatwoodTrunkPlacement {
    private GreatwoodTrunkPlacement() {}

    public interface WorldAdapter<S> {
        S read(GreatwoodTrunkPlan.Cell cell);
        boolean canReplace(GreatwoodTrunkPlan.Cell cell, S previous);
        boolean write(GreatwoodTrunkPlan.Cell cell, S state);
    }

    private record Snapshot<S>(GreatwoodTrunkPlan.Cell cell, S previous) {}

    /**
     * Preflight all cells before any writes; restore successful writes in reverse order
     * after a rejected write or exception. A failed restoration is reported, never hidden.
     */
    public static <S> boolean place(List<GreatwoodTrunkPlan.Cell> plan, S trunk,
                                    WorldAdapter<S> world) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(trunk, "trunk");
        Objects.requireNonNull(world, "world");
        if (plan.isEmpty()) return false;
        List<Snapshot<S>> snapshots = new ArrayList<>(plan.size());
        Set<GreatwoodTrunkPlan.Cell> unique = new HashSet<>();
        for (GreatwoodTrunkPlan.Cell cell : plan) {
            Objects.requireNonNull(cell, "cell");
            if (!unique.add(cell)) return false;
            S previous = Objects.requireNonNull(world.read(cell), "previous");
            if (!world.canReplace(cell, previous)) return false;
            snapshots.add(new Snapshot<>(cell, previous));
        }
        int attempted = 0;
        RuntimeException writeFailure = null;
        boolean rejected = false;
        for (Snapshot<S> snapshot : snapshots) {
            // Include the failing cell: adapters may mutate before returning false.
            attempted++;
            try {
                if (!world.write(snapshot.cell(), trunk)) {
                    rejected = true;
                    break;
                }
            } catch (RuntimeException failure) {
                writeFailure = failure;
                break;
            }
        }
        if (!rejected && writeFailure == null) return true;
        try {
            rollback(snapshots, attempted, world);
        } catch (RuntimeException rollbackFailure) {
            if (writeFailure != null) {
                writeFailure.addSuppressed(rollbackFailure);
            } else {
                throw rollbackFailure;
            }
        }
        if (writeFailure != null) throw writeFailure;
        return false;
    }

    private static <S> void rollback(List<Snapshot<S>> snapshots, int attempted,
                                     WorldAdapter<S> world) {
        RuntimeException failure = null;
        for (int i = attempted - 1; i >= 0; i--) {
            Snapshot<S> snapshot = snapshots.get(i);
            try {
                if (!world.write(snapshot.cell(), snapshot.previous())) {
                    throw new IllegalStateException("Greatwood rollback rejected at " + snapshot.cell());
                }
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
