package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Exact post-block-scan inventory recursion limit from BETA26 ScanningManager.scanTheThing. */
public final class LegacyScanContainerLogic {
    private LegacyScanContainerLogic() {}

    public record Result(int scannedNonEmptySlots, int lastVisitedSlot, boolean inventoryTooLarge) {}

    /**
     * BETA26 scans the stack first, increments the non-empty counter, and only then tests counter >= 100.
     * Therefore the 100th non-empty slot is scanned, the warning is emitted, and later slots are not visited.
     */
    public static <T> Result scanTopInventory(
            int slotCount,
            IntFunction<T> stackAt,
            Predicate<T> isEmpty,
            Consumer<T> recursiveScan) {
        if (slotCount < 0) throw new IllegalArgumentException("slotCount");
        Objects.requireNonNull(stackAt, "stackAt");
        Objects.requireNonNull(isEmpty, "isEmpty");
        Objects.requireNonNull(recursiveScan, "recursiveScan");

        int nonEmpty = 0;
        int lastVisited = -1;
        for (int slot = 0; slot < slotCount; slot++) {
            lastVisited = slot;
            T stack = stackAt.apply(slot);
            if (stack != null && !isEmpty.test(stack)) {
                recursiveScan.accept(stack);
                nonEmpty++;
            }
            if (nonEmpty >= 100) {
                return new Result(nonEmpty, lastVisited, true);
            }
        }
        return new Result(nonEmpty, lastVisited, false);
    }
}
