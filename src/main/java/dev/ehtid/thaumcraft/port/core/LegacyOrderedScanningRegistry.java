package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stable registration staging for ConfigResearch.initScannables.
 *
 * <p>The ordinal is the explicit constructor/add site in the original method. subOrder exists because
 * ScanBlock's constructor registers one companion ScanItem per block before ConfigResearch adds the
 * ScanBlock instance itself. This cannot be reproduced correctly by a simple append-only modern bootstrap
 * when handlers are ported in multiple stages.</p>
 */
public final class LegacyOrderedScanningRegistry<P, T> {
    public record Entry<P, T>(int legacyOrdinal, int subOrder, ScanThing<P, T> thing) {}

    private final List<Entry<P, T>> entries = new ArrayList<>();

    public void add(int legacyOrdinal, int subOrder, ScanThing<P, T> thing) {
        if (legacyOrdinal < 0) throw new IllegalArgumentException("legacyOrdinal");
        entries.add(new Entry<>(legacyOrdinal, subOrder, Objects.requireNonNull(thing, "thing")));
    }

    /** Convenience for an ordinary ConfigResearch addScannableThing call. */
    public void add(int legacyOrdinal, ScanThing<P, T> thing) {
        add(legacyOrdinal, 0, thing);
    }

    /**
     * Reproduces ScanBlock constructor ordering. Companions get negative sub-orders and the block itself 0.
     * Thus every companion precedes its owning ScanBlock while preserving the constructor's block-array order.
     */
    public void addScanBlockSite(int legacyOrdinal, List<? extends ScanThing<P, T>> companionItems,
                                 ScanThing<P, T> blockThing) {
        Objects.requireNonNull(companionItems, "companionItems");
        int first = -companionItems.size();
        for (int i = 0; i < companionItems.size(); i++) {
            add(legacyOrdinal, first + i, companionItems.get(i));
        }
        add(legacyOrdinal, 0, blockThing);
    }

    public List<Entry<P, T>> entriesInLegacyOrder() {
        return entries.stream()
                .sorted(Comparator.comparingInt(Entry<P, T>::legacyOrdinal)
                        .thenComparingInt(Entry<P, T>::subOrder))
                .toList();
    }

    public ScanningService<P, T> buildService() {
        ScanningService<P, T> service = new ScanningService<>();
        for (Entry<P, T> entry : entriesInLegacyOrder()) service.addScannableThing(entry.thing());
        return service;
    }
}
